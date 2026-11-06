"""Integration tests against a real Jenkins: every problem in a plan stops the build before a task runs."""
import pytest

from conftest import plan, playbook_runs, run

pytestmark = pytest.mark.jenkins

LONG_TEXT = 'x' * 1001


@pytest.mark.parametrize('deploy_plan, message', [
    pytest.param('targets: [nope]', 'unknown groups [nope], expected [web, api, worker]', id='unknown-group'),
    pytest.param('targets: []', 'no group selected: deploying and tasks that run on targets need at least one group', id='no-group'),
    pytest.param('targets: web', 'targets must be a list', id='targets-not-list'),
    pytest.param('{targets: [web], skip_deploy: maybe}', 'skip_deploy must be true or false', id='skip-not-bool'),
    pytest.param('{targets: [web], pre: task_a}', 'pre must be a list', id='phase-not-list'),
    pytest.param('{targets: [web], pre: {}}', 'pre must be a list', id='phase-empty-map'),
    pytest.param('{targets: [web], pre: [42]}', 'pre[0]: expected a task name or a mapping with name', id='entry-not-name'),
    pytest.param('{targets: [web], pre: [no_such_task]}', "pre[0]: unknown task 'no_such_task'", id='unknown-task'),
    pytest.param('{targets: [web], post: [task_b]}', 'post[0]: task task_b cannot run in post, only in [pre]', id='wrong-phase'),
    pytest.param('{targets: [web], pre: [{name: check_c, retries: 3}]}', 'pre[0]: unknown keys [retries]', id='unknown-entry-key'),
    pytest.param('{targets: [web], pre: [{name: task_a, policy: ask}]}', "pre[0]: policy 'ask' is not one of [abort, warn]", id='unknown-policy'),
    pytest.param("{targets: [web], pre: [{name: task_a, policy: ''}]}", "pre[0]: policy '' is not one of [abort, warn]", id='empty-policy'),
    pytest.param('{targets: [web], pre: [task_a]}', 'pre[0]: task task_a needs parameter task_a_message', id='missing-required'),
    pytest.param('{targets: [web], pre: [{name: task_a, params: {task_a_message: "  "}}]}',
                 'pre[0]: task task_a needs parameter task_a_message', id='blank-required'),
    pytest.param('{targets: [web], pre: [{name: check_c, params: []}]}', 'pre[0]: params must be a mapping', id='params-empty-list'),
    pytest.param('{targets: [web], pre: [{name: task_a, params: {task_a_message: x, ansible_connection: local}}]}',
                 'pre[0]: task task_a takes no parameters [ansible_connection]', id='undeclared-param'),
    pytest.param('{targets: [web], pre: [{name: task_a, params: {task_a_message: x, task_a_repeat: two}}]}',
                 "pre[0]: parameter task_a_repeat must be an integer, got 'two'", id='not-int'),
    pytest.param('{targets: [web], pre: [{name: task_c, params: {task_c_mode: destroy}}]}',
                 "pre[0]: parameter task_c_mode must be one of [plan, apply], got 'destroy'", id='not-in-choices'),
    pytest.param('{targets: [web], pre: [{name: task_c, params: {task_c_verbose: yes please}}]}',
                 "pre[0]: parameter task_c_verbose must be true or false, got 'yes please'", id='not-bool'),
    pytest.param('{targets: [web], pre: [{name: task_a, params: {task_a_message: 2026-01-01}}]}',
                 'pre[0]: parameter task_a_message must be a string (quote it in YAML)', id='yaml-date-for-string'),
    pytest.param('{targets: [web], pre: [{name: task_a, params: {task_a_message: "{{ lookup(\'pipe\', \'id\') }}"}}]}',
                 'pre[0]: parameter task_a_message must not contain template syntax ({{, {%, {#)', id='template-expression'),
    pytest.param('{targets: [web], pre: [{name: task_a, params: {task_a_message: "{% if true %}x{% endif %}"}}]}',
                 'pre[0]: parameter task_a_message must not contain template syntax', id='template-statement'),
    pytest.param('{targets: [web], pre: [{name: task_a, params: {task_a_message: "a {# c #} b"}}]}',
                 'pre[0]: parameter task_a_message must not contain template syntax', id='template-comment'),
    pytest.param('{targets: [web], pre: [check_c, check_c]}', "pre[1]: id 'check_c' is used twice", id='duplicate-id'),
    pytest.param('{targets: [web], pre: [{name: check_c, id: "a b"}]}', "pre[0]: id 'a b' may hold only letters, digits, _ and -", id='bad-id'),
    pytest.param("{targets: [web], pre: [{name: check_c, id: ''}]}", "pre[0]: id '' may hold only letters, digits, _ and -", id='empty-id'),
    pytest.param('{targets: [web], pre: [{name: check_c, id: ' + 'i' * 65 + '}]}',
                 'may hold only letters, digits, _ and -, at most 64 characters', id='id-too-long'),
    pytest.param('{targets: [web], pre: [{name: check_c, timeout_minutes: 0}]}',
                 "pre[0]: timeout_minutes must be an integer from 1 to 240, got '0'", id='zero-timeout'),
    pytest.param('{targets: [web], pre: [{name: task_a, params: {task_a_message: ' + LONG_TEXT + '}}]}',
                 'pre[0]: parameter task_a_message is longer than 1000 characters', id='string-too-long'),
    pytest.param('{targets: [web], post: [' + ', '.join(f'{{name: check_c, id: c{index}}}' for index in range(51)) + ']}',
                 'the plan has 51 tasks, at most 50 are allowed', id='too-many-tasks'),
    pytest.param('{targets: [web], verify: true}', 'unknown keys [verify]', id='unknown-plan-key'),
    pytest.param('[web]', 'DEPLOY_PLAN must be a YAML mapping', id='not-a-map'),
    pytest.param('targets: [web', 'DEPLOY_PLAN is not valid YAML', id='yaml-syntax-error'),
    pytest.param('targets: [web]\n#' + 'x' * 20000, 'DEPLOY_PLAN is longer than 20000 characters', id='plan-too-long'),
])
def test_invalid_plan_fails_before_any_task_runs(jenkins, shared_job, deploy_plan, message):
    result, console, _ = run(jenkins, shared_job, plan(deploy_plan))

    assert result == 'FAILURE'
    assert message in console
    assert playbook_runs(console) == []


@pytest.mark.parametrize('deploy_plan', [
    pytest.param('{targets: [web], pre: [{name: check_c, id: ' + 'i' * 64 + '}]}', id='longest-id'),
    pytest.param('{targets: [web], skip_deploy: true, pre: [{name: task_a, params: {task_a_message: ' + 'x' * 1000 + '}}]}',
                 id='longest-string'),
    pytest.param('{targets: [web], skip_deploy: true, post: [' + ', '.join(f'{{name: check_c, id: c{index}}}' for index in range(50))
                 + ']}', id='most-tasks'),
])
def test_values_at_their_limit_are_accepted(jenkins, shared_job, deploy_plan):
    result, console, _ = run(jenkins, shared_job, plan(deploy_plan))

    assert result == 'SUCCESS', console
    assert 'the plan has problems' not in console


def test_invalid_plan_lists_all_problems_at_once(jenkins, shared_job):
    result, console, _ = run(jenkins, shared_job, plan('{targets: [nope], pre: [no_such_task, {name: task_a, policy: ask}]}'))

    assert result == 'FAILURE'
    problems = console[console.index('[deploy] the plan has problems:'):]
    assert '  - unknown groups [nope]' in problems
    assert "  - pre[0]: unknown task 'no_such_task'" in problems
    assert "  - pre[1]: policy 'ask' is not one of [abort, warn]" in problems
    assert playbook_runs(console) == []


@pytest.mark.parametrize('options, message', [
    pytest.param('@@task_a.policy,abort,@@task_a.task_a_message,hi,@@task_a.policy,warn',
                 'form field task_a.policy appears more than once: a value must not contain ,@@', id='marker-inside-value'),
    pytest.param('@@task_a.policy,abort,@@task_a.task_a_message,hi,@@task_a.no_such_field,1',
                 'task task_a takes no parameters [no_such_field]', id='undeclared-field'),
    pytest.param('@@task_a.policy,abort,@@task_a.task_a_message,hi,@@task_b.task_b_path,x',
                 'form field task_b.task_b_path belongs to a task that is not selected', id='field-of-unselected-task'),
    pytest.param('@@task_a.policy,abort,@@task_a.task_a_message,hi,@@task_a.task_a_repeat,99999999999999999999',
                 "parameter task_a_repeat must be an integer, got '99999999999999999999'", id='integer-too-large'),
    pytest.param('@@task_a.order,first,@@task_a.task_a_message,hi',
                 "form field task_a.order must be an integer, got 'first'", id='order-not-integer'),
])
def test_invalid_form_values_fail_before_any_task_runs(jenkins, shared_job, options, message):
    result, console, _ = run(jenkins, shared_job, {'TARGET_GROUPS': 'web', 'PRE_TASKS': 'task_a', 'PRE_TASK_OPTIONS': options})

    assert result == 'FAILURE'
    assert message in console
    assert playbook_runs(console) == []
