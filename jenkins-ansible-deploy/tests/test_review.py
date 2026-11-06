"""Integration tests against a real Jenkins: a task that asks for a review runs only what was confirmed."""
import hashlib
import json
import time

import pytest

from conftest import READ_WRITE, plan, playbook_runs, run

pytestmark = pytest.mark.jenkins

REVIEW_PLAN = "{skip_deploy: true, pre: [{name: task_d, params: {task_d_items: 'a, b'}}]}"


@pytest.fixture
def waiting(jenkins, new_job):
    job = new_job(READ_WRITE)
    number = jenkins.start_build(job, plan(REVIEW_PLAN))
    [pending] = jenkins.wait_for_input(job, number)
    return job, number, pending


def test_review_shows_the_file_and_its_checksum_and_runs_nothing_yet(jenkins, waiting):
    job, number, pending = waiting
    console = jenkins.console(job, number)
    reviewed = jenkins.artifact(job, number, '.deploy-run/review/pre-task_d.txt')

    assert reviewed == 'change a\nchange b\n'
    assert pending['message'].startswith(f'Run task_d as reviewed below? (sha256 {hashlib.sha256(reviewed.encode()).hexdigest()})')
    assert pending['message'].endswith('\n\nchange a\nchange b\n')
    assert 'applied' not in console
    jenkins.abort_input(job, number, pending['id'])
    jenkins.wait_for_build(job, number)


def test_waiting_for_review_holds_no_executor(jenkins, waiting):
    job, number, pending = waiting

    busy = jenkins.json('/computer', 'busyExecutors')['busyExecutors']
    jenkins.abort_input(job, number, pending['id'])
    jenkins.wait_for_build(job, number)

    assert busy == 0


def test_confirmed_review_runs_the_reviewed_file_and_records_the_reviewer(jenkins, waiting):
    job, number, pending = waiting

    jenkins.submit_input(job, number, pending['id'], {})

    assert jenkins.wait_for_build(job, number) == 'SUCCESS'
    console = jenkins.console(job, number)
    assert '"msg": "applied change a"' in console and '"msg": "applied change b"' in console
    assert '[Pipeline] verifySha256' in console
    report = json.loads(jenkins.artifact(job, number, 'deploy-report.json'))
    assert [(row['id'], row['outcome'], row['reviewer']) for row in report] == [('task_d', 'passed', 'anonymous')]


def test_aborted_review_runs_nothing_and_aborts_the_build(jenkins, waiting):
    job, number, pending = waiting

    jenkins.abort_input(job, number, pending['id'])

    assert jenkins.wait_for_build(job, number) == 'ABORTED'
    console = jenkins.console(job, number)
    assert 'applied' not in console
    assert playbook_runs(console) == ['run-task.yml']           # only the run that wrote the review file
    report = json.loads(jenkins.artifact(job, number, 'deploy-report.json'))
    assert [(row['id'], row['outcome'], row['message']) for row in report] == [('task_d', 'aborted', 'not confirmed')]


@pytest.fixture
def no_executors(jenkins):
    """Take every executor away for one test, and check they are back afterwards."""
    executors = jenkins.run_script('print(Jenkins.get().numExecutors)').strip()
    try:
        jenkins.run_script('Jenkins.get().setNumExecutors(0)')
        yield
    finally:
        jenkins.run_script(f'Jenkins.get().setNumExecutors({executors})')
        assert jenkins.run_script('print(Jenkins.get().numExecutors)').strip() == executors


def wait_for_queued(jenkins, job, timeout_seconds=60):
    deadline = time.time() + timeout_seconds
    while time.time() < deadline:
        # a node block of a running build is queued as "part of <job> #<number>"
        if any(job in str(item) for item in jenkins.json('/queue', 'items[task[name,url]]')['items']):
            return
        time.sleep(1)
    raise TimeoutError(f'{job}: nothing queued')


def test_build_aborted_after_confirmation_before_the_run_reports_the_task_aborted(jenkins, waiting, no_executors):
    job, number, pending = waiting
    jenkins.submit_input(job, number, pending['id'], {})
    wait_for_queued(jenkins, job)                                   # the confirmed run waits for an executor
    jenkins.stop_build(job, number)
    jenkins.wait_for_console(job, number, 'Aborted by')


    assert jenkins.wait_for_build(job, number) == 'ABORTED'
    console = jenkins.console(job, number)
    assert 'applied' not in console
    report = json.loads(jenkins.artifact(job, number, 'deploy-report.json'))
    assert [(row['id'], row['outcome'], row['reviewer']) for row in report] == [('task_d', 'aborted', 'anonymous')]


def test_failed_generate_run_asks_for_no_review(jenkins, new_job, repository):
    head = repository.commit(mutated_task_d('- name: Write the changes to review\n',
                                            '- name: Fail before writing\n  ansible.builtin.fail:\n    msg: cannot generate\n'
                                            '  when: task_review_mode == \'generate\'\n\n- name: Write the changes to review\n', repository),
                             'task_d fails while generating')
    try:
        job = new_job(READ_WRITE)
        result, console, number = run(jenkins, job, plan(REVIEW_PLAN))
        report = json.loads(jenkins.artifact(job, number, 'deploy-report.json'))
    finally:
        repository.reset(head)

    assert result == 'FAILURE'
    assert 'cannot generate' in console
    assert 'Input requested' not in console and 'applied' not in console
    assert [(row['id'], row['outcome']) for row in report] == [('task_d', 'failed-stopped')]


def mutated_task_d(old, new, repository):
    path = 'business/tasks/task_d/tasks/main.yml'
    text = (repository.path / path).read_text()
    assert old in text
    return {path: text.replace(old, new, 1)}


def test_unconfirmed_review_times_out_and_runs_nothing(jenkins, new_job, repository):
    meta = repository.path / 'business/tasks/task_d/meta/task.yml'
    head = repository.commit({'business/tasks/task_d/meta/task.yml': meta.read_text().replace('timeout_minutes: 30', 'timeout_minutes: 1')},
                             'review times out after one minute')
    try:
        job = new_job(READ_WRITE)
        number = jenkins.start_build(job, plan(REVIEW_PLAN))
        jenkins.wait_for_input(job, number)
        result = jenkins.wait_for_build(job, number, timeout_seconds=240)
        console = jenkins.console(job, number)
        report = json.loads(jenkins.artifact(job, number, 'deploy-report.json'))
    finally:
        repository.reset(head)

    assert result == 'ABORTED'
    assert 'applied' not in console
    assert [(row['id'], row['outcome'], row['message']) for row in report] == [('task_d', 'aborted', 'not confirmed')]


def test_reviewed_task_that_writes_no_review_file_fails_the_build(jenkins, new_job, repository):
    head = repository.commit({
        'business/tasks/task_f/meta/task.yml': 'description: writes nothing to review\nphases: [pre]\naccess: write\nmenu: more\n'
                                               'run_on: controller\npolicy: abort\ntimeout_minutes: 5\norder: 80\n'
                                               'review: {timeout_minutes: 5}\n',
        'business/tasks/task_f/meta/argument_specs.yml': 'argument_specs:\n  main:\n    short_description: x\n    options: {}\n',
        'business/tasks/task_f/tasks/main.yml': '- name: Do nothing\n  ansible.builtin.debug:\n    msg: nothing to review\n',
    }, 'add task_f')
    try:
        job = new_job(READ_WRITE)
        result, console, number = run(jenkins, job, plan('{skip_deploy: true, pre: [task_f]}'))
        report = json.loads(jenkins.artifact(job, number, 'deploy-report.json'))
    finally:
        repository.reset(head)

    assert result == 'FAILURE'
    assert '[deploy] task task_f wrote no review file at task_review_file' in console
    assert 'Input requested' not in console
    assert [(row['id'], row['outcome']) for row in report] == [('task_f', 'error')]


def test_waiting_review_survives_a_jenkins_restart(jenkins, new_job):
    job = new_job(READ_WRITE)
    number = jenkins.start_build(job, plan(REVIEW_PLAN))
    jenkins.wait_for_input(job, number)

    jenkins.restart()
    [pending] = jenkins.wait_for_input(job, number)
    jenkins.submit_input(job, number, pending['id'], {})

    assert jenkins.wait_for_build(job, number) == 'SUCCESS'
    console = jenkins.console(job, number)
    assert 'Resuming build' in console
    assert '"msg": "applied change a"' in console and '"msg": "applied change b"' in console


def test_failed_generate_run_under_warn_continues_without_asking_for_review(jenkins, new_job, repository):
    tasks = mutated_task_d('- name: Write the changes to review\n',
                           '- name: Fail before writing\n  ansible.builtin.fail:\n    msg: cannot generate\n'
                           '  when: task_review_mode == \'generate\'\n\n- name: Write the changes to review\n', repository)
    meta = repository.path / 'business/tasks/task_d/meta/task.yml'
    head = repository.commit({**tasks, 'business/tasks/task_d/meta/task.yml': meta.read_text().replace('policy: abort', 'policy: warn')},
                             'task_d fails while generating, under warn')
    try:
        job = new_job(READ_WRITE)
        result, console, number = run(jenkins, job, plan(REVIEW_PLAN))
        report = json.loads(jenkins.artifact(job, number, 'deploy-report.json'))
    finally:
        repository.reset(head)

    assert result == 'UNSTABLE'
    assert 'Input requested' not in console and 'applied' not in console
    assert [(row['id'], row['outcome']) for row in report] == [('task_d', 'failed-continued')]


def test_review_file_that_is_a_directory_fails_the_task_as_an_error(jenkins, new_job, repository):
    head = repository.commit({
        'business/tasks/task_f/meta/task.yml': 'description: writes a directory to review\nphases: [pre]\naccess: write\nmenu: more\n'
                                               'run_on: controller\npolicy: abort\ntimeout_minutes: 5\norder: 80\n'
                                               'review: {timeout_minutes: 5}\n',
        'business/tasks/task_f/meta/argument_specs.yml': 'argument_specs:\n  main:\n    short_description: x\n    options: {}\n',
        'business/tasks/task_f/tasks/main.yml': '- name: Write a directory instead of a file\n  ansible.builtin.file:\n'
                                                '    path: "{{ task_review_file }}"\n    state: directory\n    mode: "0755"\n',
    }, 'add task_f')
    try:
        job = new_job(READ_WRITE)
        result, console, number = run(jenkins, job, plan('{skip_deploy: true, pre: [task_f]}'))
        report = json.loads(jenkins.artifact(job, number, 'deploy-report.json'))
    finally:
        repository.reset(head)

    assert result == 'FAILURE'
    assert 'Input requested' not in console
    assert [(row['id'], row['outcome']) for row in report] == [('task_f', 'error')]
