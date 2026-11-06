"""Integration tests against a real Jenkins: a task that asks for a review runs only what was confirmed."""
import json

import pytest

from conftest import READ_WRITE, plan, playbook_runs

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
    assert pending['message'].startswith('Run task_d as reviewed below? (sha256 ')
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
