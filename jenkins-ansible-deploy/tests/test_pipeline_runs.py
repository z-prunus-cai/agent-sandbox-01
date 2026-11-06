"""Integration tests against a real Jenkins: how the read-write job runs tasks and the deployment."""
import json
import re

import pytest

from conftest import READ_ONLY, READ_WRITE, plan, playbook_command_lines, playbook_runs, run

pytestmark = pytest.mark.jenkins

REGISTERED = 'Parameters registered; run again with Build with Parameters'


@pytest.mark.parametrize('script_path, expected', [
    (READ_WRITE, ['TARGET_GROUPS', 'SKIP_DEPLOY', 'PRE_TASKS', 'PRE_SHOW_MORE', 'PRE_MORE_TASKS', 'PRE_TASK_OPTIONS',
                  'POST_TASKS', 'POST_SHOW_MORE', 'POST_MORE_TASKS', 'POST_TASK_OPTIONS', 'DEPLOY_PLAN']),
    (READ_ONLY, ['TARGET_GROUPS', 'PRE_TASKS', 'PRE_SHOW_MORE', 'PRE_MORE_TASKS', 'PRE_TASK_OPTIONS',
                 'POST_TASKS', 'POST_SHOW_MORE', 'POST_MORE_TASKS', 'POST_TASK_OPTIONS', 'DEPLOY_PLAN']),
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

    assert active_choices == 7
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


def test_order_typed_on_the_form_decides_the_order_within_a_phase(jenkins, new_job):
    job = new_job(READ_WRITE)

    result, console, _ = run(jenkins, job, {
        'TARGET_GROUPS': 'web', 'SKIP_DEPLOY': 'true', 'PRE_TASKS': 'task_a,task_b', 'PRE_MORE_TASKS': 'check_c',
        'PRE_TASK_OPTIONS': '@@task_a.order,30,@@task_a.task_a_message,hi,@@task_b.order,,@@check_c.order,5'})

    assert result == 'SUCCESS', console
    assert playbook_runs(console) == ['run-task.yml'] * 3                   # SKIP_DEPLOY on the form skips the deployment
    # check_c 5, then task_b with no order typed (its catalog order 20), then task_a 30
    assert console.index('(pre: check_c)') < console.index('(pre: task_b)') < console.index('(pre: task_a)')


def test_tasks_from_both_lists_run_in_catalog_order_without_typed_orders(jenkins, new_job):
    job = new_job(READ_WRITE)

    # check_a (main list, order 50) is submitted before check_c (more list, order 40)
    result, console, _ = run(jenkins, job, {'TARGET_GROUPS': 'web', 'SKIP_DEPLOY': 'true', 'POST_TASKS': 'check_a',
                                            'POST_MORE_TASKS': 'check_c', 'POST_TASK_OPTIONS': '@@check_a.check_a_min_connections,1'})

    assert result == 'SUCCESS', console
    assert console.index('(post: check_c)') < console.index('(post: check_a)')


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
                           plan_source.read_text().replace("'[deploy] plan:'", "'[deploy] newer plan:'"),
                           **mutated(repository, 'business/tasks/task_b/tasks/main.yml', 'default(0) }} bytes', 'default(0) }} octets')},
                          'engine and business change meanwhile')
        result = jenkins.wait_for_build(job, number)
        console = jenkins.console(job, number)
    finally:
        repository.reset(head)

    assert result == 'SUCCESS', console
    assert '[deploy] plan:' in console and 'business/inventory/hosts.yml: ' in console
    assert 'newer plan' not in console and 'octets' not in console


def test_new_task_directory_runs_without_any_platform_change(jenkins, new_job, repository):
    head = repository.commit({
        'business/tasks/task_e/meta/task.yml': 'description: added for this test\nphases: [post]\naccess: read\nmenu: more\n'
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


def test_timeout_under_abort_stops_before_the_deployment(jenkins, new_job):
    job = new_job(READ_WRITE)

    result, console, number = run(jenkins, job, plan('''
targets: [api]
pre: [{name: task_a, policy: abort, timeout_minutes: 1, params: {task_a_message: x, task_a_delay_seconds: 90}}]
'''))
    report = json.loads(jenkins.artifact(job, number, 'deploy-report.json'))

    assert result == 'FAILURE'
    assert '[deploy] task task_a failed: timed out after 1 min (abort: stopping the pipeline)' in console
    assert playbook_runs(console) == ['run-task.yml']
    assert [(row['id'], row['outcome']) for row in report] == [('task_a', 'timed-out-stopped')]


def test_failed_deployment_stops_the_build_before_post_tasks(jenkins, new_job, repository):
    job = new_job(READ_WRITE)
    web = repository.path / 'business/inventory/group_vars/web.yml'
    head = repository.commit({'business/inventory/group_vars/web.yml': web.read_text().replace('app_port: 8080', 'app_port: eighty')},
                             'break the web group')
    try:
        result, console, number = run(jenkins, job, plan('{targets: [web], post: [check_c]}'))
        report = json.loads(jenkins.artifact(job, number, 'deploy-report.json'))
    finally:
        repository.reset(head)

    assert result == 'FAILURE'
    assert playbook_runs(console) == ['deploy.yml']
    assert [(row['id'], row['outcome']) for row in report] == [('deploy', 'failed-stopped')]


def test_equal_orders_on_the_form_keep_the_catalog_order(jenkins, new_job):
    job = new_job(READ_WRITE)

    result, console, _ = run(jenkins, job, {
        'TARGET_GROUPS': 'web', 'SKIP_DEPLOY': 'true', 'POST_TASKS': 'check_a', 'POST_MORE_TASKS': 'check_c',
        'POST_TASK_OPTIONS': '@@check_a.order,-1,@@check_a.check_a_min_connections,1,@@check_c.order,-1'})

    assert result == 'SUCCESS', console
    assert console.index('(post: check_c)') < console.index('(post: check_a)')


def mutated(repository, path, old, new):
    text = (repository.path / path).read_text()
    assert old in text
    return {path: text.replace(old, new, 1)}


CATALOG_PROBLEMS = [
    pytest.param(lambda r: mutated(r, 'business/tasks/task_b/meta/task.yml', 'order: 20', 'order: 10'),
                 'tasks [task_a, task_b] share order 10', id='shared-order'),
    pytest.param(lambda r: mutated(r, 'business/tasks/task_b/meta/task.yml', 'phases: [pre]', 'phases: []'),
                 'task task_b: phases must be a non-empty list of [pre, post]', id='no-phase'),
    pytest.param(lambda r: mutated(r, 'business/tasks/task_b/meta/task.yml', 'phases: [pre]', 'phases: [during]'),
                 "task task_b: phase 'during' is not one of [pre, post]", id='unknown-phase'),
    pytest.param(lambda r: mutated(r, 'business/tasks/task_b/meta/task.yml', 'order: 20', 'order: 20\nreview: yes'),
                 'task task_b: review must be a mapping with timeout_minutes', id='review-not-mapping'),
    pytest.param(lambda r: mutated(r, 'business/tasks/task_b/meta/task.yml', 'order: 20', 'order: first'),
                 'task task_b: order must be an integer', id='order-not-integer'),
    pytest.param(lambda r: mutated(r, 'business/tasks/task_b/meta/task.yml', 'order: 20', 'order: 20\nretries: 3'),
                 'task task_b: unknown keys [retries] in meta/task.yml', id='unknown-meta-key'),
    pytest.param(lambda r: mutated(r, 'business/tasks/task_b/meta/task.yml', 'timeout_minutes: 5', 'timeout_minutes: 241'),
                 "task task_b: timeout_minutes must be an integer from 1 to 240, got '241'", id='timeout-too-long'),
    pytest.param(lambda r: {'business/tasks/task_b/meta/argument_specs.yml': None},
                 'task task_b: business/tasks/task_b/meta/argument_specs.yml is missing', id='no-argument-specs'),
    pytest.param(lambda r: {'business/tasks/task_b/tasks/main.yml': None},
                 'task task_b: business/tasks/task_b/tasks/main.yml is missing', id='no-role-tasks'),
    pytest.param(lambda r: mutated(r, 'business/tasks/task_a/meta/argument_specs.yml', '        default: 1\n',
                                   '        default: 1\n        choices: [1, 2]\n'),
                 'task task_a: parameter task_a_repeat: choices are allowed only for str', id='choices-not-str'),
    pytest.param(lambda r: mutated(r, 'business/tasks/task_a/meta/argument_specs.yml', '        default: 1\n',
                                   '        default: 1\n        reqiured: true\n'),
                 'task task_a: parameter task_a_repeat: unknown keys [reqiured]', id='unknown-option-key'),
    pytest.param(lambda r: {**mutated(r, 'business/tasks/task_a/meta/argument_specs.yml', 'task_a_repeat:', 'repeat:'),
                            **mutated(r, 'business/tasks/task_a/defaults/main.yml', 'task_a_repeat:', 'repeat:')},
                 'task task_a: parameter repeat: the name must start with task_a_', id='name-without-prefix'),
    pytest.param(lambda r: mutated(r, 'business/tasks/task_a/defaults/main.yml', 'task_a_repeat: 1', 'task_a_repeat: two'),
                 "task task_a: parameter task_a_repeat: default in defaults/main.yml must be an integer, got 'two'", id='default-wrong-type'),
    pytest.param(lambda r: mutated(r, 'business/tasks/task_a/defaults/main.yml', 'task_a_repeat: 1', 'task_a_repeat: 1\ntask_a_extra: 2'),
                 'task task_a: defaults/main.yml sets task_a_extra, which meta/argument_specs.yml does not declare', id='undeclared-default'),
    pytest.param(lambda r: {'business/tasks/run/meta/task.yml': (r.path / 'business/tasks/task_b/meta/task.yml').read_text()
                            .replace('order: 20', 'order: 90'),
                            'business/tasks/run/meta/argument_specs.yml': 'argument_specs:\n  main:\n    short_description: x\n'
                                                                          '    options:\n      run_once:\n        type: bool\n'
                                                                          '        description: x\n',
                            'business/tasks/run/tasks/main.yml': '- name: Nothing\n  ansible.builtin.debug:\n    msg: x\n'},
                 'task run: parameter run_once: the name must start with run_ and must not be', id='reserved-name'),
    pytest.param(lambda r: {'business/tasks/Bad-Name/meta/task.yml': (r.path / 'business/tasks/task_b/meta/task.yml').read_text()
                            .replace('order: 20', 'order: 91'),
                            'business/tasks/Bad-Name/meta/argument_specs.yml': 'argument_specs:\n  main:\n    short_description: x\n'
                                                                               '    options: {}\n',
                            'business/tasks/Bad-Name/tasks/main.yml': '- name: Nothing\n  ansible.builtin.debug:\n    msg: x\n'},
                 'task Bad-Name: the directory name may hold only a-z, 0-9 and _, starting with a letter', id='bad-directory-name'),
    pytest.param(lambda r: mutated(r, 'business/inventory/hosts.yml', '        api:\n          hosts:\n            api-01:\n',
                                   '        api:\n          hosts: {}\n'),
                 'business/inventory/hosts.yml: group api has no hosts', id='empty-group'),
    pytest.param(lambda r: mutated(r, 'business/inventory/hosts.yml', 'deploy_targets:', 'release_targets:'),
                 'business/inventory/hosts.yml has no all.children.deploy_targets.children', id='no-deploy-targets'),
]


@pytest.mark.parametrize('change, message', CATALOG_PROBLEMS)
def test_problem_in_a_task_directory_or_the_inventory_stops_the_build_before_any_task_runs(jenkins, shared_job, repository,
                                                                                          change, message):
    head = repository.commit(change(repository), 'break the catalog')
    try:
        result, console, _ = run(jenkins, shared_job, plan('{skip_deploy: true, pre: [task_b]}'))
    finally:
        repository.reset(head)

    assert result == 'FAILURE'
    assert '[deploy] task catalog or inventory:' in console
    assert message in console
    assert playbook_runs(console) == []
