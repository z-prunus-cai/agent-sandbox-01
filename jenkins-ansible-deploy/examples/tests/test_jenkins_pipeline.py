"""Integration tests against a real Jenkins: the pipeline behaviour of both Jenkinsfiles."""
import json
import os
import re
import subprocess

import pytest

from conftest import EXAMPLES

pytestmark = pytest.mark.jenkins

ACTIVE = 'Jenkinsfile'
BASIC = 'Jenkinsfile.no-plugin'


def run(jenkins, job, params):
    number = jenkins.start_build(job, params)
    return jenkins.wait_for_build(job, number), jenkins.console(job, number), number


def plan(text):
    return {'DEPLOY_PLAN': text}


def repository():
    return os.environ['DEPLOY_REPO_URL'].removeprefix('file://')


def git(*args):
    return subprocess.run(['git', '-C', repository(), '-c', 'user.name=test', '-c', 'user.email=test@example.com', *args],
                          check=True, capture_output=True, text=True).stdout.strip()


def playbook_command_lines(console):
    return [line.split('$ ansible-playbook ', 1)[1] for line in console.splitlines() if '$ ansible-playbook ' in line]


def playbook_runs(console):
    return [command.split()[0].rsplit('/', 1)[1] for command in playbook_command_lines(console)]


@pytest.mark.parametrize('jenkinsfile, expected', [
    (ACTIVE, ['TARGET_GROUPS', 'SKIP_DEPLOY', 'PRE_TASKS', 'PRE_TASK_OPTIONS', 'POST_TASKS', 'POST_TASK_OPTIONS', 'DEPLOY_PLAN']),
    (BASIC, ['TARGET_web', 'TARGET_api', 'TARGET_worker', 'SKIP_DEPLOY', 'PRE_task_a', 'PRE_task_b', 'PRE_task_c',
             'PRE_check_c', 'POST_task_c', 'POST_check_c', 'POST_check_a', 'EDIT_TASK_OPTIONS', 'DEPLOY_PLAN']),
])
def test_first_build_registers_parameters_from_catalog_and_inventory_and_runs_nothing(jenkins, new_job, jenkinsfile, expected):
    job = new_job(jenkinsfile)

    first_build = jenkins.build_info(job, 1)
    assert first_build['result'] == 'NOT_BUILT'
    assert first_build['description'] == '已登记构建参数，请用 Build with Parameters 重新运行'
    assert jenkins.parameter_names(job) == expected
    assert playbook_runs(jenkins.console(job, 1)) == []


@pytest.mark.parametrize('jenkinsfile', [ACTIVE, BASIC])
def test_builds_of_one_job_do_not_run_concurrently(jenkins, new_job, jenkinsfile):
    job = new_job(jenkinsfile)

    assert jenkins.json(f'/job/{job}', 'concurrentBuild')['concurrentBuild'] is False


def test_parameter_page_scripts_run_in_the_sandbox_without_approval(jenkins, new_job):
    # the test Jenkins has no security, so it would approve a non-sandboxed script silently: check the job itself
    job = new_job(ACTIVE)
    config = jenkins.config_xml(job)
    active_choices = config.count('<org.biouno.unochoice.')
    approvals = jenkins.run_script(
        'def approval = org.jenkinsci.plugins.scriptsecurity.scripts.ScriptApproval.get()\n'
        'println approval.approvedScriptHashes.size() + approval.pendingScripts.size()')

    assert active_choices > 0
    assert config.count('<sandbox>true</sandbox>') == 2 * active_choices      # every script and its fallback
    assert '<sandbox>false</sandbox>' not in config
    assert approvals.strip() == '0'


def test_selection_runs_tasks_in_catalog_order_on_the_selected_groups_only(jenkins, new_job):
    job = new_job(ACTIVE)

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
    assert 'inventory/hosts.yml: ' in console
    assert 'deploy web 2.3.1 to web-02' in console and 'deploy api 1.8.0 to api-01' in console
    assert 'worker-0' not in console


REPORT_PLAN = '''
targets: [api]
pre: [task_b]
post: [{name: check_a, params: {check_a_min_connections: 500}}]
'''


def test_each_task_runs_in_its_own_stage_named_by_phase_and_id(jenkins, new_job):
    job = new_job(ACTIVE)

    result, console, _ = run(jenkins, job, plan(REPORT_PLAN))

    assert result == 'UNSTABLE', console
    assert '[Pipeline] { (pre: task_b)' in console and '[Pipeline] { (post: check_a)' in console


def test_report_lists_every_step_with_its_result(jenkins, new_job):
    job = new_job(ACTIVE)

    result, console, number = run(jenkins, job, plan(REPORT_PLAN))
    report = json.loads(jenkins.artifact(job, number, 'deploy-report.json'))

    assert result == 'UNSTABLE', console
    assert [(row['phase'], row['id'], row['name'], row['policy'], row['status'], row.get('outcome')) for row in report] == [
        ('pre', 'task_b', 'task_b', 'abort', 'PASSED', None),
        ('deploy', 'deploy', 'deploy', 'abort', 'PASSED', None),
        ('post', 'check_a', 'check_a', 'warn', 'FAILED', 'continued')]
    assert all(type(row['seconds']) is int for row in report)
    assert report[2]['params'] == {'check_a_min_connections': 500}
    summary_row = console[console.index('[deploy] 结果：'):].splitlines()[3].split()
    assert summary_row[:4] == ['post', 'check_a', 'warn', 'FAILED'] and summary_row[5:] == ['->', 'continued']
    assert re.fullmatch(r'\d+s', summary_row[4])


def test_deploy_plan_runs_entries_in_listed_order_and_repeats_a_task_by_id(jenkins, new_job):
    job = new_job(ACTIVE)

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
    job = new_job(ACTIVE)

    result, console, _ = run(jenkins, job, plan('{targets: [worker], skip_deploy: true, pre: [check_c], post: [check_c]}'))

    assert result == 'SUCCESS', console
    assert 'Stage "Deploy" skipped due to when conditional' in console
    assert playbook_runs(console) == ['run-task.yml', 'run-task.yml']
    assert '(pre: check_c)' in console and '(post: check_c)' in console   # the same id in both phases is fine


def test_controller_only_run_needs_no_target_group(jenkins, new_job):
    job = new_job(ACTIVE)

    result, console, _ = run(jenkins, job, plan('{skip_deploy: true, pre: [task_b]}'))

    assert result == 'SUCCESS', console
    assert playbook_runs(console) == ['run-task.yml']
    assert 'inventory/hosts.yml: ' in console


@pytest.mark.parametrize('deploy_plan, message', [
    pytest.param('targets: [nope]', '未知主机组 [nope]，可用主机组 [web, api, worker]', id='unknown-group'),
    pytest.param('targets: []', '没有选择主机组（部署和 run_on: targets 的任务需要至少一个主机组）', id='no-group'),
    pytest.param('targets: web', 'targets 必须是列表', id='targets-not-list'),
    pytest.param('{targets: [web], skip_deploy: maybe}', 'skip_deploy 必须是 true 或 false', id='skip-not-bool'),
    pytest.param('{targets: [web], pre: task_a}', 'pre 必须是列表', id='phase-not-list'),
    pytest.param('{targets: [web], pre: {}}', 'pre 必须是列表', id='phase-empty-map'),
    pytest.param('{targets: [web], pre: [42]}', 'pre[0]：应为任务名，或包含 name 的映射', id='entry-not-name'),
    pytest.param('{targets: [web], pre: [no_such_task]}', "pre[0]：未知任务 'no_such_task'", id='unknown-task'),
    pytest.param('{targets: [web], post: [task_b]}', 'post[0]：任务 task_b 不能用于 post，只能用于 [pre]', id='wrong-phase'),
    pytest.param('{targets: [web], pre: [{name: check_c, retries: 3}]}', "pre[0]：不支持的字段 [retries]", id='unknown-entry-key'),
    pytest.param('{targets: [web], pre: [{name: task_a, policy: ask}]}', "pre[0]：未知策略 'ask'，只能是 abort 或 warn", id='unknown-policy'),
    pytest.param("{targets: [web], pre: [{name: task_a, policy: ''}]}", "pre[0]：未知策略 ''", id='empty-policy'),
    pytest.param('{targets: [web], pre: [task_a]}', 'pre[0]：任务 task_a 必须提供参数 task_a_message', id='missing-required'),
    pytest.param('{targets: [web], pre: [{name: task_a, params: {task_a_message: "  "}}]}',
                 'pre[0]：任务 task_a 必须提供参数 task_a_message', id='blank-required'),
    pytest.param('{targets: [web], pre: [{name: check_c, params: []}]}', 'pre[0]：params 必须是映射', id='params-empty-list'),
    pytest.param('{targets: [web], pre: [{name: task_a, params: {task_a_message: x, ansible_connection: local}}]}',
                 'pre[0]：任务 task_a 不接受参数 [ansible_connection]', id='undeclared-param'),
    pytest.param('{targets: [web], pre: [{name: task_a, params: {task_a_message: x, task_a_repeat: two}}]}',
                 "pre[0]：参数 task_a_repeat 应为整数，实际为 'two'", id='not-int'),
    pytest.param('{targets: [web], pre: [{name: task_c, params: {task_c_mode: destroy}}]}',
                 "pre[0]：参数 task_c_mode 只能是 [plan, apply]，实际为 'destroy'", id='not-in-choices'),
    pytest.param('{targets: [web], pre: [{name: task_c, params: {task_c_verbose: yes please}}]}',
                 "pre[0]：参数 task_c_verbose 应为 true 或 false，实际为 'yes please'", id='not-bool'),
    pytest.param('{targets: [web], pre: [{name: task_a, params: {task_a_message: 2026-01-01}}]}',
                 'pre[0]：参数 task_a_message 应为字符串（YAML 中请加引号）', id='yaml-date-for-string'),
    pytest.param('{targets: [web], pre: [{name: task_a, params: {task_a_message: "{{ lookup(\'pipe\', \'id\') }}"}}]}',
                 'pre[0]：参数 task_a_message 不能包含模板语法（{{、{%、{#）', id='template-expression'),
    pytest.param('{targets: [web], pre: [{name: task_a, params: {task_a_message: "{% if true %}x{% endif %}"}}]}',
                 'pre[0]：参数 task_a_message 不能包含模板语法', id='template-statement'),
    pytest.param('{targets: [web], pre: [{name: task_a, params: {task_a_message: "a {# c #} b"}}]}',
                 'pre[0]：参数 task_a_message 不能包含模板语法', id='template-comment'),
    pytest.param('{targets: [web], pre: [check_c, check_c]}', "pre[1]：id 'check_c' 重复", id='duplicate-id'),
    pytest.param('{targets: [web], pre: [{name: check_c, id: "a b"}]}', "pre[0]：id 'a b' 只能包含字母、数字、_、-", id='bad-id'),
    pytest.param("{targets: [web], pre: [{name: check_c, id: ''}]}", "pre[0]：id '' 只能包含字母、数字、_、-", id='empty-id'),
    pytest.param('{targets: [web], pre: [{name: check_c, timeout_minutes: 0}]}', 'pre[0]：timeout_minutes 必须是正整数', id='zero-timeout'),
    pytest.param('{targets: [web], verify: true}', '不支持的字段 [verify]', id='unknown-plan-key'),
    pytest.param('[web]', 'DEPLOY_PLAN 必须是 YAML 映射', id='not-a-map'),
    pytest.param('targets: [web', 'DEPLOY_PLAN 不是合法的 YAML', id='yaml-syntax-error'),
])
def test_invalid_plan_fails_before_any_playbook_runs(jenkins, shared_active_job, deploy_plan, message):
    result, console, _ = run(jenkins, shared_active_job, plan(deploy_plan))

    assert result == 'FAILURE'
    assert message in console
    assert playbook_runs(console) == []


def test_invalid_plan_lists_all_problems_at_once(jenkins, shared_active_job):
    result, console, _ = run(jenkins, shared_active_job, plan('{targets: [nope], pre: [no_such_task, {name: task_a, policy: ask}]}'))

    assert result == 'FAILURE'
    problems = console[console.index('[deploy] 计划有误：'):]
    assert '  - 未知主机组 [nope]' in problems
    assert "  - pre[0]：未知任务 'no_such_task'" in problems
    assert "  - pre[1]：未知策略 'ask'" in problems
    assert playbook_runs(console) == []


@pytest.mark.parametrize('options, message', [
    pytest.param('@@task_a.policy,abort,@@task_a.task_a_message,hi,@@task_a.policy,warn',
                 '表单字段 task_a.policy 出现多次：参数值中不能含 ,@@', id='marker-inside-value'),
    pytest.param('@@task_a.policy,abort,@@task_a.task_a_message,hi,@@task_a.no_such_field,1',
                 '任务 task_a 不接受参数 [no_such_field]', id='undeclared-field'),
    pytest.param('@@task_a.policy,abort,@@task_a.task_a_message,hi,@@task_b.task_b_path,x',
                 '表单字段 task_b.task_b_path 属于未选择的任务', id='field-of-unselected-task'),
    pytest.param('@@task_a.policy,abort,@@task_a.task_a_message,hi,@@task_a.task_a_repeat,99999999999999999999',
                 "参数 task_a_repeat 应为整数，实际为 '99999999999999999999'", id='integer-too-large'),
])
def test_invalid_form_values_fail_before_any_playbook_runs(jenkins, shared_active_job, options, message):
    result, console, _ = run(jenkins, shared_active_job, {'TARGET_GROUPS': 'web', 'PRE_TASKS': 'task_a', 'PRE_TASK_OPTIONS': options})

    assert result == 'FAILURE'
    assert message in console
    assert playbook_runs(console) == []


def test_warn_failure_marks_build_unstable_and_continues(jenkins, new_job):
    job = new_job(ACTIVE)

    result, console, _ = run(jenkins, job, plan('''
targets: [web]
pre: [{name: task_a, policy: warn, params: {task_a_message: x, task_a_repeat: 9}}]
'''))

    assert result == 'UNSTABLE', console
    assert '[deploy] 任务 task_a 失败：Ansible playbook execution failed（warn：继续执行）' in console
    assert 'task_a_repeat must be between 1 and 5, got 9' in console
    assert playbook_runs(console) == ['run-task.yml', 'deploy.yml']


def test_abort_failure_stops_before_deploy_and_later_tasks(jenkins, new_job):
    job = new_job(ACTIVE)

    result, console, number = run(jenkins, job, plan('''
targets: [web]
pre: [{name: task_b, params: {task_b_path: no-such-file.yml}}, check_c]
post: [check_c]
'''))
    report = json.loads(jenkins.artifact(job, number, 'deploy-report.json'))

    assert result == 'FAILURE'
    assert '[deploy] 任务 task_b 失败：Ansible playbook execution failed（abort：停止流水线）' in console
    assert playbook_runs(console) == ['run-task.yml']
    assert [(row['id'], row['status'], row['outcome']) for row in report] == [('task_b', 'FAILED', 'stopped')]


def test_timeout_counts_as_task_failure_and_follows_the_policy(jenkins, new_job):
    job = new_job(ACTIVE)

    result, console, _ = run(jenkins, job, plan('''
targets: [api]
pre: [{name: task_a, policy: warn, timeout_minutes: 1, params: {task_a_message: x, task_a_delay_seconds: 90}}]
'''))

    assert result == 'UNSTABLE', console
    assert '[deploy] 任务 task_a 失败：超过 1 分钟未完成（warn：继续执行）' in console
    assert playbook_runs(console) == ['run-task.yml', 'deploy.yml']


def test_manual_abort_during_a_task_aborts_the_build_instead_of_continuing(jenkins, new_job):
    job = new_job(ACTIVE)
    number = jenkins.start_build(job, plan('''
targets: [api]
pre: [{name: task_a, policy: warn, params: {task_a_message: x, task_a_delay_seconds: 60}}]
'''))
    jenkins.wait_for_console(job, number, 'Pausing for 60 seconds')

    jenkins.stop_build(job, number)

    assert jenkins.wait_for_build(job, number) == 'ABORTED'
    console = jenkins.console(job, number)
    assert 'warn：继续执行' not in console
    assert playbook_runs(console) == ['run-task.yml']
    report = json.loads(jenkins.artifact(job, number, 'deploy-report.json'))
    assert [(row['id'], row['status']) for row in report] == [('task_a', 'ABORTED')]


def test_missing_vault_credential_stops_the_build_before_any_playbook_runs(jenkins, new_job, vault_credential_removed):
    job = new_job(ACTIVE)

    result, console, _ = run(jenkins, job, plan('{skip_deploy: true, pre: [task_b]}'))

    assert result == 'FAILURE'
    assert 'ansible-vault-password' in console
    assert playbook_runs(console) == []


def test_no_plugin_form_shows_only_selected_tasks_and_its_values_are_used(jenkins, new_job):
    job = new_job(BASIC)
    number = jenkins.start_build(job, {'TARGET_web': 'true', 'PRE_task_a': 'true', 'POST_check_a': 'true'})
    [pending] = jenkins.wait_for_input(job, number)
    assert pending['names'] == ['pre.task_a.policy', 'pre.task_a.task_a_message', 'pre.task_a.task_a_repeat',
                                'pre.task_a.task_a_delay_seconds', 'post.check_a.policy', 'post.check_a.check_a_min_connections']

    # check_a's default policy is warn; choosing abort in the form is what makes this build fail
    jenkins.submit_input(job, number, pending['id'], {
        'pre.task_a.policy': 'warn', 'pre.task_a.task_a_message': 'from form', 'pre.task_a.task_a_repeat': '3',
        'pre.task_a.task_a_delay_seconds': '0', 'post.check_a.policy': 'abort', 'post.check_a.check_a_min_connections': '150'})

    assert jenkins.wait_for_build(job, number) == 'FAILURE'
    console = jenkins.console(job, number)
    assert '    - task_a [warn] [task_a_message:from form, task_a_repeat:3, task_a_delay_seconds:0]' in console
    assert 'web-01: app_max_connections=100 < 150' in console
    assert playbook_runs(console) == ['run-task.yml', 'deploy.yml', 'run-task.yml']


def test_no_plugin_waiting_for_input_holds_no_executor(jenkins, new_job):
    job = new_job(BASIC)
    number = jenkins.start_build(job, {'TARGET_web': 'true', 'PRE_task_c': 'true'})
    [pending] = jenkins.wait_for_input(job, number)

    busy = jenkins.json('/computer', 'busyExecutors')['busyExecutors']
    jenkins.abort_input(job, number, pending['id'])
    jenkins.wait_for_build(job, number)

    assert busy == 0


def test_no_plugin_abort_in_form_aborts_the_build(jenkins, new_job):
    job = new_job(BASIC)
    number = jenkins.start_build(job, {'TARGET_web': 'true', 'PRE_task_c': 'true'})
    [pending] = jenkins.wait_for_input(job, number)

    jenkins.abort_input(job, number, pending['id'])

    assert jenkins.wait_for_build(job, number) == 'ABORTED'
    assert playbook_runs(jenkins.console(job, number)) == []


def test_no_plugin_form_with_a_single_field_returns_its_value(jenkins, new_job):
    job = new_job(BASIC)
    number = jenkins.start_build(job, {'TARGET_worker': 'true', 'SKIP_DEPLOY': 'true', 'POST_check_c': 'true'})
    [pending] = jenkins.wait_for_input(job, number)
    assert pending['names'] == ['post.check_c.policy']

    jenkins.submit_input(job, number, pending['id'], {'post.check_c.policy': 'abort'})

    assert jenkins.wait_for_build(job, number) == 'SUCCESS'
    console = jenkins.console(job, number)
    assert '    - check_c [abort] [:]' in console
    assert playbook_runs(console) == ['run-task.yml']


def test_no_plugin_without_form_uses_defaults(jenkins, new_job):
    job = new_job(BASIC)

    result, console, _ = run(jenkins, job, {'SKIP_DEPLOY': 'true', 'PRE_task_b': 'true', 'EDIT_TASK_OPTIONS': 'false'})

    assert result == 'SUCCESS', console
    assert 'Input requested' not in console
    assert '    - task_b [abort] [task_b_path:inventory/hosts.yml, task_b_min_bytes:1]' in console
    assert playbook_runs(console) == ['run-task.yml']
    assert 'inventory/hosts.yml: ' in console


def test_no_plugin_bool_from_the_form_reaches_ansible(jenkins, new_job):
    job = new_job(BASIC)
    number = jenkins.start_build(job, {'TARGET_api': 'true', 'SKIP_DEPLOY': 'true', 'PRE_task_c': 'true'})
    [pending] = jenkins.wait_for_input(job, number)

    jenkins.submit_input(job, number, pending['id'], {
        'pre.task_c.policy': 'abort', 'pre.task_c.task_c_mode': 'plan', 'pre.task_c.task_c_verbose': True,
        'pre.task_c.task_c_label': ''})

    assert jenkins.wait_for_build(job, number) == 'SUCCESS'
    assert '"msg": "api-01: app=api version=1.8.0"' in jenkins.console(job, number)


def test_no_plugin_runs_the_files_it_validated_even_if_the_branch_moves_while_waiting(jenkins, new_job):
    job = new_job(BASIC)
    number = jenkins.start_build(job, {'SKIP_DEPLOY': 'true', 'PRE_task_b': 'true'})
    [pending] = jenkins.wait_for_input(job, number)
    hosts_yml = os.path.join(repository(), EXAMPLES.parent.name, EXAMPLES.name, 'ansible', 'inventory', 'hosts.yml')
    validated_size = os.path.getsize(hosts_yml)
    head = git('rev-parse', 'HEAD')
    try:
        with open(hosts_yml, 'a', encoding='utf-8') as hosts:
            hosts.write('\n# a line added while the build waits\n')
        git('commit', '-q', '-a', '-m', 'change while waiting')
        jenkins.submit_input(job, number, pending['id'], {
            'pre.task_b.policy': 'abort', 'pre.task_b.task_b_path': 'inventory/hosts.yml', 'pre.task_b.task_b_min_bytes': '1'})
        result = jenkins.wait_for_build(job, number)
    finally:
        git('reset', '-q', '--hard', head)

    assert result == 'SUCCESS'
    assert f'"msg": "inventory/hosts.yml: {validated_size} bytes"' in jenkins.console(job, number)



def test_no_plugin_without_form_rejects_a_missing_required_parameter(jenkins, new_job):
    job = new_job(BASIC)

    result, console, _ = run(jenkins, job, {'TARGET_web': 'true', 'PRE_task_a': 'true', 'EDIT_TASK_OPTIONS': 'false'})

    assert result == 'FAILURE'
    assert 'pre[0]：任务 task_a 必须提供参数 task_a_message' in console
    assert playbook_runs(console) == []


def test_no_plugin_deploy_plan_skips_the_form(jenkins, new_job):
    job = new_job(BASIC)

    result, console, _ = run(jenkins, job, {'PRE_task_a': 'true', 'DEPLOY_PLAN': '{skip_deploy: true, pre: [task_b]}'})

    assert result == 'SUCCESS', console
    assert 'Input requested' not in console
    assert playbook_runs(console) == ['run-task.yml']
    assert '(pre: task_b)' in console and '(pre: task_a)' not in console
