"""Integration tests against a real Jenkins: the basic form, which asks for task options with input after the build starts."""
import pytest

from conftest import BASIC, playbook_runs, run

pytestmark = pytest.mark.jenkins


def test_first_build_registers_a_checkbox_per_group_and_task(jenkins, new_job):
    job = new_job(BASIC)

    assert jenkins.parameter_names(job) == [
        'TARGET_web', 'TARGET_api', 'TARGET_worker', 'SKIP_DEPLOY',
        'PRE_task_a', 'PRE_task_b', 'PRE_task_c', 'PRE_check_c', 'PRE_task_d',
        'POST_task_c', 'POST_check_c', 'POST_check_a', 'POST_task_d', 'EDIT_TASK_OPTIONS', 'DEPLOY_PLAN']


def test_form_shows_only_selected_tasks_and_its_values_are_used(jenkins, new_job):
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


def test_waiting_for_the_form_holds_no_executor(jenkins, new_job):
    job = new_job(BASIC)
    number = jenkins.start_build(job, {'TARGET_web': 'true', 'PRE_task_c': 'true'})
    [pending] = jenkins.wait_for_input(job, number)

    busy = jenkins.json('/computer', 'busyExecutors')['busyExecutors']
    jenkins.abort_input(job, number, pending['id'])
    jenkins.wait_for_build(job, number)

    assert busy == 0


def test_abort_in_the_form_aborts_the_build(jenkins, new_job):
    job = new_job(BASIC)
    number = jenkins.start_build(job, {'TARGET_web': 'true', 'PRE_task_c': 'true'})
    [pending] = jenkins.wait_for_input(job, number)

    jenkins.abort_input(job, number, pending['id'])

    assert jenkins.wait_for_build(job, number) == 'ABORTED'
    assert playbook_runs(jenkins.console(job, number)) == []


def test_form_with_a_single_field_returns_its_value(jenkins, new_job):
    job = new_job(BASIC)
    number = jenkins.start_build(job, {'TARGET_worker': 'true', 'SKIP_DEPLOY': 'true', 'POST_check_c': 'true'})
    [pending] = jenkins.wait_for_input(job, number)
    assert pending['names'] == ['post.check_c.policy']

    jenkins.submit_input(job, number, pending['id'], {'post.check_c.policy': 'abort'})

    assert jenkins.wait_for_build(job, number) == 'SUCCESS'
    console = jenkins.console(job, number)
    assert '    - check_c [abort] [:]' in console
    assert playbook_runs(console) == ['run-task.yml']


def test_without_the_form_every_task_uses_its_defaults(jenkins, new_job):
    job = new_job(BASIC)

    result, console, _ = run(jenkins, job, {'SKIP_DEPLOY': 'true', 'PRE_task_b': 'true', 'EDIT_TASK_OPTIONS': 'false'})

    assert result == 'SUCCESS', console
    assert 'Input requested' not in console
    assert '    - task_b [abort] [task_b_path:business/inventory/hosts.yml, task_b_min_bytes:1]' in console
    assert playbook_runs(console) == ['run-task.yml']


def test_bool_from_the_form_reaches_ansible(jenkins, new_job):
    job = new_job(BASIC)
    number = jenkins.start_build(job, {'TARGET_api': 'true', 'SKIP_DEPLOY': 'true', 'PRE_task_c': 'true'})
    [pending] = jenkins.wait_for_input(job, number)

    jenkins.submit_input(job, number, pending['id'], {
        'pre.task_c.policy': 'abort', 'pre.task_c.task_c_mode': 'plan', 'pre.task_c.task_c_verbose': True,
        'pre.task_c.task_c_label': ''})

    assert jenkins.wait_for_build(job, number) == 'SUCCESS'
    assert '"msg": "api-01: app=api version=1.8.0"' in jenkins.console(job, number)


def test_runs_the_files_it_validated_even_if_the_branch_moves_while_waiting(jenkins, new_job, repository):
    job = new_job(BASIC)
    hosts_yml = repository.path / 'business/inventory/hosts.yml'
    validated_size = hosts_yml.stat().st_size
    number = jenkins.start_build(job, {'SKIP_DEPLOY': 'true', 'PRE_task_b': 'true'})
    [pending] = jenkins.wait_for_input(job, number)
    head = repository.commit({'business/inventory/hosts.yml': hosts_yml.read_text() + '\n# a line added while the build waits\n'},
                             'change while waiting')
    try:
        jenkins.submit_input(job, number, pending['id'], {
            'pre.task_b.policy': 'abort', 'pre.task_b.task_b_path': 'business/inventory/hosts.yml', 'pre.task_b.task_b_min_bytes': '1'})
        result = jenkins.wait_for_build(job, number)
    finally:
        repository.reset(head)

    assert result == 'SUCCESS'
    assert f'"msg": "business/inventory/hosts.yml: {validated_size} bytes"' in jenkins.console(job, number)


def test_without_the_form_a_missing_required_parameter_fails(jenkins, new_job):
    job = new_job(BASIC)

    result, console, _ = run(jenkins, job, {'TARGET_web': 'true', 'PRE_task_a': 'true', 'EDIT_TASK_OPTIONS': 'false'})

    assert result == 'FAILURE'
    assert 'pre[0]: task task_a needs parameter task_a_message' in console
    assert playbook_runs(console) == []


def test_deploy_plan_skips_the_form(jenkins, new_job):
    job = new_job(BASIC)

    result, console, _ = run(jenkins, job, {'PRE_task_a': 'true', 'DEPLOY_PLAN': '{skip_deploy: true, pre: [task_b]}'})

    assert result == 'SUCCESS', console
    assert 'Input requested' not in console
    assert playbook_runs(console) == ['run-task.yml']
    assert '(pre: task_b)' in console and '(pre: task_a)' not in console
