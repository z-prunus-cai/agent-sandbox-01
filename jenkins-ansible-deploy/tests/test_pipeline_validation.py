"""Integration tests against a real Jenkins: every problem in a plan stops the build before a task runs."""
import pytest

from conftest import limit, plan, playbook_runs, run

pytestmark = pytest.mark.jenkins

MAX_STRING = limit('MAX_STRING_PARAMETER_CHARACTERS')
MAX_ID = limit('MAX_ID_CHARACTERS')
MAX_ENTRIES = limit('MAX_PLAN_ENTRIES')
MAX_PLAN = limit('MAX_PLAN_CHARACTERS')
MAX_TIMEOUT = limit('MAX_TIMEOUT_MINUTES')


def padded_plan(text, length):
    """text followed by a YAML comment that brings the whole plan to exactly length characters."""
    return text + '\n#' + 'x' * (length - len(text) - 2)


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
    pytest.param('{targets: [web], pre: [{name: task_a, params: {task_a_message: yes}}]}',
                 'pre[0]: parameter task_a_message must be a string (quote it in YAML)', id='yaml-boolean-for-string'),
    pytest.param('{targets: [web], pre: [{name: task_a, params: {task_a_message: 1.0}}]}',
                 'pre[0]: parameter task_a_message must be a string (quote it in YAML)', id='yaml-decimal-for-string'),
    pytest.param('{targets: [web], pre: [{name: task_a, params: {task_a_message: "{{ lookup(\'pipe\', \'id\') }}"}}]}',
                 'pre[0]: parameter task_a_message must not contain template syntax ({{, {%, {# or a leading #jinja2:)', id='template-expression'),
    pytest.param('{targets: [web], pre: [{name: task_a, params: {task_a_message: "{% if true %}x{% endif %}"}}]}',
                 'pre[0]: parameter task_a_message must not contain template syntax', id='template-statement'),
    pytest.param('{targets: [web], pre: [{name: task_a, params: {task_a_message: "a {# c #} b"}}]}',
                 'pre[0]: parameter task_a_message must not contain template syntax', id='template-comment'),
    pytest.param('{targets: [web], pre: [{name: task_a, params: {task_a_message: "#jinja2:variable_start_string:\'[[\'\\n[[ 1 ]]"}}]}',
                 'pre[0]: parameter task_a_message must not contain template syntax ({{, {%, {# or a leading #jinja2:)', id='template-override-header'),
    pytest.param('{targets: [web], pre: [{name: task_a, params: {task_a_message: "a\\x1bb"}}]}',
                 'pre[0]: parameter task_a_message must not contain control or format characters', id='control-character'),
    pytest.param('{targets: [web], pre: [{name: task_a, params: {task_a_message: "a\\u2028b"}}]}',
                 'pre[0]: parameter task_a_message must not contain control or format characters', id='line-separator'),
    pytest.param('{targets: [web], pre: [{name: task_a, params: {task_a_message: x, 1: y}}]}',
                 'pre[0]: task task_a takes no parameters [1]', id='parameter-name-not-a-string'),
    pytest.param('{targets: [web], pre: [{name: task_a, params: {task_a_message: "a\\u202Eb"}}]}',
                 'pre[0]: parameter task_a_message must not contain control or format characters', id='format-character'),
    pytest.param('{skip_deploy: true, pre: [check_c]}', 'no group selected', id='no-group-for-a-task-on-targets'),
    pytest.param('{targets: [web], pre: [check_c, check_c]}', "pre[1]: id 'check_c' is used twice", id='duplicate-id'),
    pytest.param('{targets: [web], pre: [{name: check_c, id: "a b"}]}', "pre[0]: id 'a b' may hold only letters, digits, _ and -", id='bad-id'),
    pytest.param("{targets: [web], pre: [{name: check_c, id: ''}]}", "pre[0]: id '' may hold only letters, digits, _ and -", id='empty-id'),
    pytest.param('{targets: [web], pre: [{name: check_c, id: ' + 'i' * (MAX_ID + 1) + '}]}',
                 f'may hold only letters, digits, _ and -, at most {MAX_ID} characters', id='id-too-long'),
    pytest.param('{targets: [web], pre: [{name: check_c, timeout_minutes: 0}]}',
                 f"pre[0]: timeout_minutes must be an integer from 1 to {MAX_TIMEOUT}, got '0'", id='zero-timeout'),
    pytest.param('{targets: [web], pre: [{name: check_c, timeout_minutes: ' + str(MAX_TIMEOUT + 1) + '}]}',
                 f"pre[0]: timeout_minutes must be an integer from 1 to {MAX_TIMEOUT}, got '{MAX_TIMEOUT + 1}'", id='timeout-too-long'),
    pytest.param("{targets: [web], pre: [{name: check_c, timeout_minutes: '5'}]}",
                 f"pre[0]: timeout_minutes must be an integer from 1 to {MAX_TIMEOUT}, got '5'", id='timeout-quoted'),
    pytest.param('{targets: [web], pre: [{name: task_a, params: {task_a_message: ' + 'x' * (MAX_STRING + 1) + '}}]}',
                 f'pre[0]: parameter task_a_message is longer than {MAX_STRING} characters', id='string-too-long'),
    pytest.param('{targets: [web], post: [' + ', '.join(f'{{name: check_c, id: c{index}}}' for index in range(MAX_ENTRIES + 1)) + ']}',
                 f'the plan has {MAX_ENTRIES + 1} tasks, at most {MAX_ENTRIES} are allowed', id='too-many-tasks'),
    pytest.param('{targets: [web], verify: true}', 'unknown keys [verify]', id='unknown-plan-key'),
    pytest.param('[web]', 'DEPLOY_PLAN must be a YAML mapping', id='not-a-map'),
    pytest.param('targets: [web', 'DEPLOY_PLAN is not valid YAML', id='yaml-syntax-error'),
    pytest.param(padded_plan('targets: [web]', MAX_PLAN + 1), f'DEPLOY_PLAN is longer than {MAX_PLAN} characters', id='plan-too-long'),
])
def test_invalid_plan_fails_before_any_task_runs(jenkins, shared_job, deploy_plan, message):
    result, console, _ = run(jenkins, shared_job, plan(deploy_plan))

    assert result == 'FAILURE'
    assert message in console
    assert playbook_runs(console) == []


@pytest.mark.parametrize('deploy_plan', [
    pytest.param('{targets: [web], skip_deploy: true, pre: [{name: check_c, id: ' + 'i' * MAX_ID + '}]}', id='longest-id'),
    pytest.param('{targets: [web], skip_deploy: true, pre: [{name: task_a, params: {task_a_message: ' + 'x' * MAX_STRING + '}}]}',
                 id='longest-string'),
    pytest.param('{targets: [web], skip_deploy: true, post: [' + ', '.join(f'{{name: check_c, id: c{index}}}' for index in range(MAX_ENTRIES))
                 + ']}', id='most-tasks'),
    pytest.param('{targets: [web], skip_deploy: true, pre: [{name: check_c, timeout_minutes: ' + str(MAX_TIMEOUT) + '}]}',
                 id='longest-timeout'),
    pytest.param(padded_plan('{targets: [web], skip_deploy: true, pre: [check_c]}', MAX_PLAN), id='longest-plan'),
    pytest.param('{targets: [web], skip_deploy: true, pre: [{name: task_a, params: {task_a_message: "a\\tb"}}]}', id='tab-in-string'),
])
def test_values_at_their_limit_are_accepted(jenkins, shared_job, deploy_plan):
    result, console, _ = run(jenkins, shared_job, plan(deploy_plan))

    assert result == 'SUCCESS', console
    assert 'the plan has problems' not in console


def test_problems_print_control_characters_as_escapes_so_they_cannot_fake_a_line(jenkins, shared_job):
    result, console, _ = run(jenkins, shared_job, plan('{targets: [web], pre: [{name: task_a, policy: "x\\n[deploy] forged"}]}'))

    assert result == 'FAILURE'
    assert "pre[0]: policy 'x\\u000a[deploy] forged' is not one of [abort, warn]" in console
    assert '\n[deploy] forged' not in console


def test_invalid_plan_lists_all_problems_at_once(jenkins, shared_job):
    result, console, _ = run(jenkins, shared_job, plan('{targets: [nope], pre: [no_such_task, {name: task_a, policy: ask}]}'))

    assert result == 'FAILURE'
    problems = console[console.index('[deploy] the plan has problems:'):]
    assert '  - unknown groups [nope]' in problems
    assert "  - pre[0]: unknown task 'no_such_task'" in problems
    assert "  - pre[1]: policy 'ask' is not one of [abort, warn]" in problems
    assert '  - pre[1]: task task_a needs parameter task_a_message' in problems
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
    pytest.param('@@task_a.policy,abort,@@task_a.task_a_message,{{ 1 }}',
                 'parameter task_a_message must not contain template syntax', id='template-in-form'),
    pytest.param('@@task_a.task_a_message,' + 'x' * MAX_PLAN, f'PRE_TASK_OPTIONS is longer than {MAX_PLAN} characters', id='options-too-long'),
    pytest.param('@@task_a.order,first,@@task_a.task_a_message,hi',
                 "form field task_a.order must be an integer, got 'first'", id='order-not-integer'),
])
def test_invalid_form_values_fail_before_any_task_runs(jenkins, shared_job, options, message):
    result, console, _ = run(jenkins, shared_job, {'TARGET_GROUPS': 'web', 'PRE_TASKS': 'task_a', 'PRE_TASK_OPTIONS': options})

    assert result == 'FAILURE'
    assert message in console
    assert playbook_runs(console) == []


def test_form_value_longer_than_the_plan_limit_fails_before_any_task_runs(jenkins, shared_job):
    result, console, _ = run(jenkins, shared_job, {'TARGET_GROUPS': 'web,' * (MAX_PLAN // 4 + 1), 'PRE_TASKS': 'check_c'})

    assert result == 'FAILURE'
    assert f'TARGET_GROUPS is longer than {MAX_PLAN} characters' in console
    assert playbook_runs(console) == []


def test_task_options_exactly_at_the_plan_limit_are_accepted(jenkins, shared_job):
    options = '@@task_a.task_a_message,hi,@@task_a.task_a_delay_seconds,'
    options += '0' * (MAX_PLAN - len(options))                       # leading zeros: still the integer 0

    result, console, _ = run(jenkins, shared_job, {'TARGET_GROUPS': 'web', 'SKIP_DEPLOY': 'true', 'PRE_TASKS': 'task_a', 'PRE_TASK_OPTIONS': options})

    assert len(options) == MAX_PLAN
    assert result == 'SUCCESS', console
