"""Integration tests against a real Jenkins: how the read-write job runs tasks and the deployment."""
import json
import re

import pytest

from conftest import READ_ONLY, READ_WRITE, plan, playbook_command_lines, playbook_runs, run

pytestmark = pytest.mark.jenkins

REGISTERED = 'Parameters registered; run again with Build with Parameters'


@pytest.mark.parametrize('script_path, expected', [
    (READ_WRITE, ['TARGET_GROUPS', 'SKIP_DEPLOY', 'PRE_TASKS', 'PRE_TASK_OPTIONS', 'POST_TASKS', 'POST_TASK_OPTIONS', 'DEPLOY_PLAN']),
    (READ_ONLY, ['TARGET_GROUPS', 'PRE_TASKS', 'PRE_TASK_OPTIONS', 'POST_TASKS', 'POST_TASK_OPTIONS', 'DEPLOY_PLAN']),
])
def test_first_build_registers_parameters_and_runs_nothing(jenkins, new_job, script_path, expected):
    job = new_job(script_path)

    first_build = jenkins.build_info(job, 1)
    assert first_build['result'] == 'NOT_BUILT'
    assert first_build['description'] == REGISTERED
    assert jenkins.parameter_names(job) == expected
    assert playbook_runs(jenkins.console(job, 1)) == []


@pytest.mark.parametrize('script_path', [READ_WRITE, READ_ONLY])
def test_builds_of_one_job_do_not_run_concurrently(jenkins, new_job, script_path):
    job = new_job(script_path)

    assert jenkins.json(f'/job/{job}', 'concurrentBuild')['concurrentBuild'] is False


def test_parameter_page_scripts_run_in_the_sandbox_without_approval(jenkins, new_job):
    # the test Jenkins has no security, so it would approve a non-sandboxed script silently: check the job itself
    job = new_job(READ_WRITE)
    config = jenkins.config_xml(job)
    active_choices = config.count('<org.biouno.unochoice.')
    approvals = jenkins.run_script(
        'def approval = org.jenkinsci.plugins.scriptsecurity.scripts.ScriptApproval.get()\n'
        'println approval.approvedScriptHashes.size() + approval.pendingScripts.size()')

    assert active_choices == 5
    assert config.count('<sandbox>true</sandbox>') == 2 * active_choices      # every script and its fallback
    assert '<sandbox>false</sandbox>' not in config
    assert approvals.strip() == '0'


def test_selection_runs_tasks_in_catalog_order_on_the_selected_groups_only(jenkins, new_job):
    job = new_job(READ_WRITE)

    result, console, _ = run(jenkins, job, {
        'TARGET_GROUPS': 'web,api', 'PRE_TASKS': 'task_b,task_a',
        'PRE_TASK_OPTIONS': '@@task_a.policy,warn,@@task_a.task_a_message,hi,@@task_a.task_a_repeat,2,@@task_a.task_a_delay_seconds,'})

    assert result == 'SUCCESS', console
    commands = playbook_command_lines(console)
    assert playbook_runs(console) == ['run-task.yml', 'run-task.yml', 'deploy.yml']
    assert ' -l web:api ' in commands[0] and ' -l web:api ' in commands[2]
    assert ' -l ' not in commands[1]                                # task_b runs on the Jenkins node itself
    assert '    - task_a [warn] [task_a_message:hi, task_a_repeat:2, task_a_delay_seconds:0]' in console
    # the selected hosts really ran: without the inventory Ansible would print "no hosts matched" and still succeed
    assert '"msg": "web-01: hi (2/2)"' in console and '"msg": "api-01: hi (2/2)"' in console
    assert 'business/inventory/hosts.yml: ' in console
    assert 'deploy web 2.3.1 to web-02' in console and 'deploy api 1.8.0 to api-01' in console
    assert 'worker-0' not in console


REPORT_PLAN = '''
targets: [api]
pre: [task_b]
post: [{name: check_a, params: {check_a_min_connections: 500}}]
'''


def test_each_task_runs_in_its_own_stage_named_by_phase_and_id(jenkins, new_job):
    job = new_job(READ_WRITE)

    result, console, _ = run(jenkins, job, plan(REPORT_PLAN))

    assert result == 'UNSTABLE', console
    assert '[Pipeline] { (pre: task_b)' in console and '[Pipeline] { (post: check_a)' in console


def test_report_lists_every_step_with_its_outcome(jenkins, new_job):
    job = new_job(READ_WRITE)

    result, console, number = run(jenkins, job, plan(REPORT_PLAN))
    report = json.loads(jenkins.artifact(job, number, 'deploy-report.json'))

    assert result == 'UNSTABLE', console
    assert [(row['phase'], row['id'], row['name'], row['policy'], row['outcome']) for row in report] == [
        ('pre', 'task_b', 'task_b', 'abort', 'passed'),
        ('deploy', 'deploy', 'deploy', 'abort', 'passed'),
        ('post', 'check_a', 'check_a', 'warn', 'failed-continued')]
    assert all(type(row['seconds']) is int for row in report)
    assert report[2]['params'] == {'check_a_min_connections': 500}
    summary_row = console[console.index('[deploy] results:'):].splitlines()[3].split()
    assert summary_row[:4] == ['post', 'check_a', 'warn', 'failed-continued']
    assert re.fullmatch(r'\d+s', summary_row[4])


def test_deploy_plan_runs_entries_in_listed_order_and_repeats_a_task_by_id(jenkins, new_job):
    job = new_job(READ_WRITE)

    result, console, _ = run(jenkins, job, plan('''
targets: [worker]
pre:
  - {name: check_c, id: check_c_first}
  - {name: task_c, params: {task_c_mode: apply, task_c_verbose: true}}
  - {name: check_c, id: check_c_second}
'''))

    assert result == 'SUCCESS', console
    assert console.index('(pre: check_c_first)') < console.index('(pre: task_c)') < console.index('(pre: check_c_second)')
    assert '"msg": "worker-02: task_c mode=apply label="' in console
    assert '"msg": "worker-02: app=worker version=1.8.0"' in console    # task_c_verbose: true arrived as a boolean
    assert 'web-0' not in console and 'api-01' not in console


def test_skipped_deploy_still_runs_pre_and_post_tasks(jenkins, new_job):
    job = new_job(READ_WRITE)

    result, console, _ = run(jenkins, job, plan('{targets: [worker], skip_deploy: true, pre: [check_c], post: [check_c]}'))

    assert result == 'SUCCESS', console
    assert '[Pipeline] { (Deploy)' not in console
    assert playbook_runs(console) == ['run-task.yml', 'run-task.yml']
    assert '(pre: check_c)' in console and '(post: check_c)' in console   # the same id in both phases is fine


def test_controller_only_run_needs_no_target_group(jenkins, new_job):
    job = new_job(READ_WRITE)

    result, console, _ = run(jenkins, job, plan('{skip_deploy: true, pre: [task_b]}'))

    assert result == 'SUCCESS', console
    assert playbook_runs(console) == ['run-task.yml']
    assert 'business/inventory/hosts.yml: ' in console


def test_warn_failure_marks_build_unstable_and_continues(jenkins, new_job):
    job = new_job(READ_WRITE)

    result, console, _ = run(jenkins, job, plan('''
targets: [web]
pre: [{name: task_a, policy: warn, params: {task_a_message: x, task_a_repeat: 9}}]
'''))

    assert result == 'UNSTABLE', console
    assert '[deploy] task task_a failed: Ansible playbook execution failed (warn: continuing)' in console
    assert 'task_a_repeat must be between 1 and 5, got 9' in console
    assert playbook_runs(console) == ['run-task.yml', 'deploy.yml']


def test_abort_failure_stops_before_deploy_and_later_tasks(jenkins, new_job):
    job = new_job(READ_WRITE)

    result, console, number = run(jenkins, job, plan('''
targets: [web]
pre: [{name: task_b, params: {task_b_path: no-such-file.yml}}, check_c]
post: [check_c]
'''))
    report = json.loads(jenkins.artifact(job, number, 'deploy-report.json'))

    assert result == 'FAILURE'
    assert '[deploy] task task_b failed: Ansible playbook execution failed (abort: stopping the pipeline)' in console
    assert playbook_runs(console) == ['run-task.yml']
    assert [(row['id'], row['outcome']) for row in report] == [('task_b', 'failed-stopped')]


def test_timeout_counts_as_task_failure_and_follows_the_policy(jenkins, new_job):
    job = new_job(READ_WRITE)

    result, console, number = run(jenkins, job, plan('''
targets: [api]
pre: [{name: task_a, policy: warn, timeout_minutes: 1, params: {task_a_message: x, task_a_delay_seconds: 90}}]
'''))
    report = json.loads(jenkins.artifact(job, number, 'deploy-report.json'))

    assert result == 'UNSTABLE', console
    assert '[deploy] task task_a failed: timed out after 1 min (warn: continuing)' in console
    assert playbook_runs(console) == ['run-task.yml', 'deploy.yml']
    assert [(row['id'], row['outcome']) for row in report] == [('task_a', 'timed-out-continued'), ('deploy', 'passed')]


def test_manual_abort_during_a_task_aborts_the_build_instead_of_continuing(jenkins, new_job):
    job = new_job(READ_WRITE)
    number = jenkins.start_build(job, plan('''
targets: [api]
pre: [{name: task_a, policy: warn, params: {task_a_message: x, task_a_delay_seconds: 60}}]
'''))
    jenkins.wait_for_console(job, number, 'Pausing for 60 seconds')

    jenkins.stop_build(job, number)

    assert jenkins.wait_for_build(job, number) == 'ABORTED'
    console = jenkins.console(job, number)
    assert 'warn: continuing' not in console
    assert playbook_runs(console) == ['run-task.yml']
    report = json.loads(jenkins.artifact(job, number, 'deploy-report.json'))
    assert [(row['id'], row['outcome']) for row in report] == [('task_a', 'aborted')]


def test_missing_vault_credential_stops_the_build_before_any_task_runs(jenkins, new_job, request):
    job = new_job(READ_WRITE)
    credential = request.getfixturevalue('vault_credential_removed')

    result, console, _ = run(jenkins, job, plan('{skip_deploy: true, pre: [task_b]}'))

    assert result == 'FAILURE'
    assert credential in console
    assert playbook_runs(console) == []


def test_engine_comes_from_the_commit_the_jenkinsfile_came_from(jenkins, new_job, repository):
    # a Jenkinsfile that waits between reading its commit and loading the library, so the branch can move meanwhile
    jenkinsfile = (repository.path / READ_WRITE).read_text()
    waiting = jenkinsfile.replace('\nlibrary(', '\nsleep 20\nlibrary(', 1)
    assert waiting != jenkinsfile
    head = repository.commit({'pipelines/waiting/Jenkinsfile': waiting}, 'waiting Jenkinsfile')
    plan_source = repository.path / 'platform/jenkins/src/deploy/Plan.groovy'
    try:
        job = new_job('pipelines/waiting/Jenkinsfile')
        number = jenkins.start_build(job, plan('{skip_deploy: true, pre: [task_b]}'))
        jenkins.wait_for_console(job, number, 'Sleeping for 20 sec')
        repository.commit({str(plan_source.relative_to(repository.path)):
                           plan_source.read_text().replace("'[deploy] plan:'", "'[deploy] newer plan:'")}, 'engine changes meanwhile')
        result = jenkins.wait_for_build(job, number)
        console = jenkins.console(job, number)
    finally:
        repository.reset(head)

    assert result == 'SUCCESS', console
    assert '[deploy] plan:' in console
    assert 'newer plan' not in console


def test_new_task_directory_runs_without_any_platform_change(jenkins, new_job, repository):
    head = repository.commit({
        'business/tasks/task_e/meta/task.yml': 'description: added for this test\nphases: [post]\naccess: read\n'
                                               'run_on: controller\npolicy: abort\ntimeout_minutes: 5\norder: 70\n',
        'business/tasks/task_e/meta/argument_specs.yml': 'argument_specs:\n  main:\n    short_description: x\n    options:\n'
                                                         '      task_e_word:\n        type: str\n        required: true\n'
                                                         '        description: x\n',
        'business/tasks/task_e/tasks/main.yml': '- name: Say the word\n  ansible.builtin.debug:\n    msg: "task_e says {{ task_e_word }}"\n',
    }, 'add task_e')
    try:
        changed = repository.git('diff', '--name-only', head, 'HEAD').splitlines()
        job = new_job(READ_WRITE)
        result, console, _ = run(jenkins, job, plan('{skip_deploy: true, post: [{name: task_e, params: {task_e_word: hello}}]}'))
    finally:
        repository.reset(head)

    assert len(changed) == 3 and all(path.startswith('business/tasks/task_e/') for path in changed)
    assert result == 'SUCCESS', console
    assert '"msg": "task_e says hello"' in console


def test_parameter_name_used_by_the_inventory_stops_the_build_before_any_task_runs(jenkins, new_job, repository):
    job = new_job(READ_WRITE)
    head = repository.commit({'business/inventory/host_vars/web-01.yml': 'ansible_host: 192.0.2.10\ntask_a_message: from inventory\n'},
                             'clash')
    try:
        result, console, _ = run(jenkins, job, plan('{skip_deploy: true, pre: [task_b]}'))
    finally:
        repository.reset(head)

    assert result == 'FAILURE'
    assert "task parameters also defined in the inventory: ['task_a_message']" in console
    assert playbook_runs(console) == []


def test_unknown_value_in_task_metadata_stops_the_build_before_any_task_runs(jenkins, new_job, repository):
    job = new_job(READ_WRITE)
    original = (repository.path / 'business/tasks/task_b/meta/task.yml').read_text()
    head = repository.commit({'business/tasks/task_b/meta/task.yml': original.replace('access: read', 'access: admin')}, 'bad access')
    try:
        result, console, _ = run(jenkins, job, plan('{skip_deploy: true, pre: [task_b]}'))
    finally:
        repository.reset(head)

    assert result == 'FAILURE'
    assert "task task_b: access 'admin' is not one of [read, write]" in console
    assert playbook_runs(console) == []
