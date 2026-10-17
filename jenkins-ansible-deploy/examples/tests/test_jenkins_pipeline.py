"""Integration tests against a real Jenkins: the pipeline behaviour of both Jenkinsfiles."""
import json
import time

import pytest

pytestmark = pytest.mark.jenkins

ACTIVE = 'Jenkinsfile'
BASIC = 'Jenkinsfile.no-plugin'


def run(jenkins, job, params):
    number = jenkins.start_build(job, params)
    return jenkins.wait_for_build(job, number), jenkins.console(job, number), number


def plan(text):
    return {'DEPLOY_PLAN': text}


def playbook_runs(console):
    return [line.split('ansible-playbook ', 1)[1].split()[0].rsplit('/', 1)[1]
            for line in console.splitlines() if '$ ansible-playbook ' in line]


def wait_for_console(jenkins, job, number, text, timeout_seconds=120):
    deadline = time.time() + timeout_seconds
    while time.time() < deadline:
        if text in jenkins.console(job, number):
            return
        time.sleep(1)
    raise TimeoutError(text)


@pytest.mark.parametrize('jenkinsfile, expected', [
    (ACTIVE, ['TARGET_GROUPS', 'SKIP_DEPLOY', 'PRE_TASKS', 'PRE_TASK_OPTIONS', 'POST_TASKS', 'POST_TASK_OPTIONS', 'DEPLOY_PLAN']),
    (BASIC, ['TARGET_web', 'TARGET_api', 'TARGET_worker', 'SKIP_DEPLOY', 'PRE_task_a', 'PRE_task_b', 'PRE_task_c',
             'PRE_check_c', 'POST_task_c', 'POST_check_c', 'POST_check_a', 'EDIT_TASK_OPTIONS', 'DEPLOY_PLAN']),
])
def test_first_build_registers_parameters_from_catalog_and_inventory_and_runs_nothing(jenkins, new_job, jenkinsfile, expected):
    job = new_job(jenkinsfile)

    assert jenkins.parameter_names(job) == expected
    assert playbook_runs(jenkins.console(job, 1)) == []
    assert jenkins.build_info(job, 1)['description'] == '已登记构建参数，请用 Build with Parameters 重新运行'


def test_selection_runs_tasks_in_catalog_order_with_typed_form_values(jenkins, new_job):
    job = new_job(ACTIVE)

    result, console, _ = run(jenkins, job, {
        'TARGET_GROUPS': 'web,api', 'PRE_TASKS': 'task_b,task_a',
        'PRE_TASK_OPTIONS': '@@task_a.policy,warn,@@task_a.task_a_message,hi,@@task_a.task_a_repeat,2,@@task_a.task_a_delay_seconds,',
        'POST_TASKS': 'check_c', 'POST_TASK_OPTIONS': '@@check_c.policy,abort'})

    assert result == 'SUCCESS', console
    assert playbook_runs(console) == ['run-task.yml', 'run-task.yml', 'deploy.yml', 'run-task.yml']
    assert '    - task_a [warn] [task_a_message:hi, task_a_repeat:2, task_a_delay_seconds:0]' in console
    assert console.index('[deploy] task_a') < console.index('[deploy] task_b')


def test_report_lists_every_step_with_its_result(jenkins, new_job):
    job = new_job(ACTIVE)

    result, console, number = run(jenkins, job, plan('''
targets: [api]
pre: [task_b]
post: [{name: check_a, params: {check_a_min_connections: 500}}]
'''))
    report = json.loads(jenkins.artifact(job, number, 'deploy-report.json'))

    assert result == 'UNSTABLE', console
    assert [(r['phase'], r['id'], r['policy'], r['status'], r.get('outcome')) for r in report] == [
        ('pre', 'task_b', 'abort', 'PASSED', None),
        ('deploy', 'deploy', 'abort', 'PASSED', None),
        ('post', 'check_a', 'warn', 'FAILED', 'continued')]
    assert report[2]['params'] == {'check_a_min_connections': 500}


def test_deploy_plan_runs_listed_order_repeats_a_task_by_id_and_skips_deploy(jenkins, new_job):
    job = new_job(ACTIVE)

    result, console, _ = run(jenkins, job, plan('''
targets: [worker]
skip_deploy: true
pre:
  - {name: check_c, id: check_c_first}
  - {name: task_c, params: {task_c_mode: apply, task_c_verbose: true}}
  - {name: check_c, id: check_c_second}
'''))

    assert result == 'SUCCESS', console
    assert 'Stage "Deploy" skipped due to when conditional' in console
    assert 'deploy.yml' not in playbook_runs(console)
    assert console.index('[deploy] check_c_first') < console.index('[deploy] task_c') < console.index('[deploy] check_c_second')
    assert '"msg": "worker-02: task_c mode=apply label="' in console


def test_controller_only_run_needs_no_target_group(jenkins, new_job):
    job = new_job(ACTIVE)

    result, console, _ = run(jenkins, job, plan('{skip_deploy: true, pre: [task_b]}'))

    assert result == 'SUCCESS', console
    assert playbook_runs(console) == ['run-task.yml']
    assert ' -l ' not in console


@pytest.mark.parametrize('deploy_plan, message', [
    ('targets: [nope]', "未知主机组 [nope]，可用主机组 [web, api, worker]"),
    ('targets: []', '没有选择主机组（部署和 run_on: targets 的任务需要至少一个主机组）'),
    ('{targets: [web], pre: [no_such_task]}', "pre[0]：未知任务 'no_such_task'"),
    ('{targets: [web], post: [task_b]}', 'post[0]：任务 task_b 不能用于 post，只能用于 [pre]'),
    ('{targets: [web], pre: [{name: task_a, policy: ask}]}', "pre[0]：未知策略 'ask'，只能是 abort 或 warn"),
    ('{targets: [web], pre: [task_a]}', 'pre[0]：任务 task_a 必须提供参数 task_a_message'),
    ('{targets: [web], pre: [{name: task_a, params: {task_a_message: x, ansible_connection: local}}]}',
     "pre[0]：任务 task_a 不接受参数 [ansible_connection]"),
    ('{targets: [web], pre: [{name: task_a, params: {task_a_message: x, task_a_repeat: two}}]}',
     "pre[0]：参数 task_a_repeat 应为整数，实际为 'two'"),
    ('{targets: [web], pre: [{name: task_c, params: {task_c_mode: destroy}}]}',
     "pre[0]：参数 task_c_mode 只能是 [plan, apply]，实际为 'destroy'"),
    ('{targets: [web], pre: [{name: task_c, params: {task_c_verbose: yes please}}]}',
     "pre[0]：参数 task_c_verbose 应为 true 或 false，实际为 'yes please'"),
    ('{targets: [web], pre: [{name: task_a, params: {task_a_message: "{{ lookup(\'pipe\', \'id\') }}"}}]}',
     'pre[0]：参数 task_a_message 不能包含模板语法（{{、{%、{#）'),
    ('{targets: [web], pre: [check_c, check_c]}', "pre[1]：id 'check_c' 重复"),
    ('{targets: [web], pre: [{name: check_c, id: "a b"}]}', "pre[0]：id 'a b' 只能包含字母、数字、_、-"),
    ('{targets: [web], pre: [{name: check_c, timeout_minutes: 0}]}', 'pre[0]：timeout_minutes 必须是正整数'),
    ('{targets: [web], verify: true}', '不支持的字段 [verify]'),
    ('[web]', 'DEPLOY_PLAN 必须是 YAML 映射'),
])
def test_invalid_plan_fails_before_any_playbook_runs(jenkins, new_job, deploy_plan, message):
    job = new_job(ACTIVE)

    result, console, _ = run(jenkins, job, plan(deploy_plan))

    assert result == 'FAILURE'
    assert message in console
    assert playbook_runs(console) == []


def test_invalid_plan_lists_all_problems_at_once(jenkins, new_job):
    job = new_job(ACTIVE)

    result, console, _ = run(jenkins, job, plan('{targets: [nope], pre: [no_such_task, {name: task_a, policy: ask}]}'))

    assert result == 'FAILURE'
    problems = console[console.index('[deploy] 计划有误：'):]
    assert '  - 未知主机组 [nope]' in problems
    assert "  - pre[0]：未知任务 'no_such_task'" in problems
    assert "  - pre[1]：未知策略 'ask'" in problems
    assert playbook_runs(console) == []


def test_warn_failure_marks_build_unstable_and_continues(jenkins, new_job):
    job = new_job(ACTIVE)

    result, console, _ = run(jenkins, job, plan('''
targets: [web]
pre: [{name: task_a, policy: warn, params: {task_a_message: x, task_a_repeat: 9}}]
'''))

    assert result == 'UNSTABLE', console
    assert '[deploy] 任务 task_a 失败：Ansible playbook execution failed（warn：继续执行）' in console
    assert playbook_runs(console) == ['run-task.yml', 'deploy.yml']


def test_abort_failure_stops_before_deploy_and_later_tasks(jenkins, new_job):
    job = new_job(ACTIVE)

    result, console, _ = run(jenkins, job, plan('''
targets: [web]
pre: [{name: task_b, params: {task_b_path: no-such-file.yml}}, check_c]
post: [check_c]
'''))

    assert result == 'FAILURE'
    assert '[deploy] 任务 task_b 失败：Ansible playbook execution failed（abort：停止流水线）' in console
    assert playbook_runs(console) == ['run-task.yml']


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
    wait_for_console(jenkins, job, number, 'Pausing for 60 seconds')

    jenkins.stop_build(job, number)

    assert jenkins.wait_for_build(job, number) == 'ABORTED'
    console = jenkins.console(job, number)
    assert 'warn：继续执行' not in console
    assert playbook_runs(console) == ['run-task.yml']


def test_no_plugin_form_shows_only_selected_tasks_and_its_values_are_used(jenkins, new_job):
    job = new_job(BASIC)
    number = jenkins.start_build(job, {'TARGET_web': 'true', 'PRE_task_a': 'true', 'POST_check_a': 'true'})
    [pending] = jenkins.wait_for_input(job, number)

    jenkins.submit_input(job, number, pending['id'], {
        'pre.task_a.policy': 'warn', 'pre.task_a.task_a_message': 'from form', 'pre.task_a.task_a_repeat': '3',
        'pre.task_a.task_a_delay_seconds': '0', 'post.check_a.policy': 'abort', 'post.check_a.check_a_min_connections': '150'})

    assert pending['names'] == ['pre.task_a.policy', 'pre.task_a.task_a_message', 'pre.task_a.task_a_repeat',
                                'pre.task_a.task_a_delay_seconds', 'post.check_a.policy', 'post.check_a.check_a_min_connections']
    assert jenkins.wait_for_build(job, number) == 'FAILURE'
    console = jenkins.console(job, number)
    assert '    - task_a [warn] [task_a_message:from form, task_a_repeat:3, task_a_delay_seconds:0]' in console
    assert 'web-01: app_max_connections=100 < 150' in console
    assert playbook_runs(console) == ['run-task.yml', 'deploy.yml', 'run-task.yml']


def test_no_plugin_form_with_a_single_field_returns_its_value(jenkins, new_job):
    job = new_job(BASIC)
    number = jenkins.start_build(job, {'TARGET_worker': 'true', 'SKIP_DEPLOY': 'true', 'POST_check_c': 'true'})
    [pending] = jenkins.wait_for_input(job, number)

    jenkins.submit_input(job, number, pending['id'], {'post.check_c.policy': 'abort'})

    assert pending['names'] == ['post.check_c.policy']
    assert jenkins.wait_for_build(job, number) == 'SUCCESS'
    assert '    - check_c [abort] [:]' in jenkins.console(job, number)


def test_no_plugin_without_form_uses_defaults(jenkins, new_job):
    job = new_job(BASIC)

    result, console, _ = run(jenkins, job, {'SKIP_DEPLOY': 'true', 'PRE_task_b': 'true', 'EDIT_TASK_OPTIONS': 'false'})

    assert result == 'SUCCESS', console
    assert '    - task_b [abort] [task_b_path:inventory/hosts.yml, task_b_min_bytes:1]' in console


def test_no_plugin_without_form_rejects_a_missing_required_parameter(jenkins, new_job):
    job = new_job(BASIC)

    result, console, _ = run(jenkins, job, {'TARGET_web': 'true', 'PRE_task_a': 'true', 'EDIT_TASK_OPTIONS': 'false'})

    assert result == 'FAILURE'
    assert 'pre[0]：任务 task_a 必须提供参数 task_a_message' in console
    assert playbook_runs(console) == []


def test_no_plugin_abort_in_form_aborts_the_build(jenkins, new_job):
    job = new_job(BASIC)
    number = jenkins.start_build(job, {'TARGET_web': 'true', 'PRE_task_c': 'true'})
    [pending] = jenkins.wait_for_input(job, number)

    jenkins.abort_input(job, number, pending['id'])

    assert jenkins.wait_for_build(job, number) == 'ABORTED'
    assert playbook_runs(jenkins.console(job, number)) == []


def test_no_plugin_deploy_plan_skips_the_form(jenkins, new_job):
    job = new_job(BASIC)

    result, console, _ = run(jenkins, job, {'PRE_task_a': 'true', 'DEPLOY_PLAN': '{skip_deploy: true, pre: [task_b]}'})

    assert result == 'SUCCESS', console
    assert 'Input requested' not in console
    assert playbook_runs(console) == ['run-task.yml']
