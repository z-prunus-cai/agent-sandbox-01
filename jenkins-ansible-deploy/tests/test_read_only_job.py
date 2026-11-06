"""Integration tests against a real Jenkins: the read-only job offers only read tasks and never deploys."""
import pytest

from conftest import READ_ONLY, plan, playbook_runs, run, task_metadata, TASK_NAMES

pytestmark = pytest.mark.jenkins

READ_TASKS = [task for task in TASK_NAMES if task_metadata(task)['access'] == 'read']
WRITE_TASKS = [task for task in TASK_NAMES if task_metadata(task)['access'] == 'write']


@pytest.fixture(scope='module')
def read_only_job(jenkins, repository, request):
    from conftest import create_registered_job, job_name
    name = create_registered_job(jenkins, repository, job_name(request.node.nodeid) + '-read-only', READ_ONLY)
    yield name
    jenkins.delete_job(name)


def test_parameter_page_lists_only_read_tasks(jenkins, read_only_job):
    config = jenkins.config_xml(read_only_job)

    assert READ_TASKS and WRITE_TASKS
    assert all(f"&apos;{task}&apos;" in config for task in READ_TASKS)
    assert not any(f"&apos;{task}&apos;" in config for task in WRITE_TASKS)


def test_read_tasks_run_without_a_deployment(jenkins, read_only_job):
    result, console, _ = run(jenkins, read_only_job, plan('{targets: [api], pre: [task_b], post: [check_c]}'))

    assert result == 'SUCCESS', console
    assert playbook_runs(console) == ['run-task.yml', 'run-task.yml']
    assert '[Pipeline] { (Deploy)' not in console


@pytest.mark.parametrize('deploy_plan, message', [
    pytest.param('{targets: [web], pre: [{name: task_a, params: {task_a_message: x}}]}',
                 'pre[0]: task task_a writes, and this job is read-only', id='write-task'),
    pytest.param('{targets: [web], skip_deploy: false, post: [check_c]}', "unknown keys [skip_deploy], expected [targets, pre, post]",
                 id='deploy-requested'),
])
def test_write_requests_fail_before_any_task_runs(jenkins, read_only_job, deploy_plan, message):
    result, console, _ = run(jenkins, read_only_job, plan(deploy_plan))

    assert result == 'FAILURE'
    assert message in console
    assert playbook_runs(console) == []


def test_write_task_chosen_on_the_form_fails_before_any_task_runs(jenkins, read_only_job):
    result, console, _ = run(jenkins, read_only_job, {'TARGET_GROUPS': 'web', 'PRE_TASKS': 'task_a',
                                                      'PRE_TASK_OPTIONS': '@@task_a.policy,abort,@@task_a.task_a_message,hi'})

    assert result == 'FAILURE'
    assert 'pre[0]: task task_a writes, and this job is read-only' in console
    assert playbook_runs(console) == []
