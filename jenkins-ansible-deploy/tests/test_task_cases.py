"""Runs the cases each task keeps in business/tasks/<task>/tests/cases.yml, so a new task brings its own tests.

Every case runs the task once through platform/ansible/run-task.yml, the way the engine does: the parameters
as a JSON file with -e @file, task_role, task_hosts from run_on (RunLocation.groovy), the example vault password.

cases.yml is a list of cases. Keys of a case (any other key fails the case):

  name               required; text, unique in the file; the test id is <task>/<name>
  params             map of task parameters, passed as given (wrong types on purpose are fine); default {}.
                     Only parameters the task declares in meta/argument_specs.yml, as the engine accepts no others
  limit              host pattern for --limit, as the engine passes the selected host groups; default none
  rc                 required; the exit code of ansible-playbook (0 passed, 2 a task failed)
  recap_hosts        list: exactly the hosts in PLAY RECAP
  recap_failed       map host -> number: exactly the hosts in PLAY RECAP and the failed counter of each
  output             list of texts that must each appear in stdout
  absent             list of texts that must not appear in stdout (for example 'TASK [<task> : <name>' of a
                     task that must not run)
  review_mode        generate or execute; required for a task with review in meta/task.yml, refused for others.
                     The engine variables task_review_mode and task_review_file are set from it
  review_file        text written to the review file before the run; default: no file
  review_file_after  text the review file must hold after the run

The keys are not called hosts: ansible-lint reads a list whose first item has hosts as a playbook.
"""
import pytest
import yaml

from conftest import TASKS, TASK_NAMES, recap, run_task, task_metadata

CASES_FILE = 'tests/cases.yml'
REVIEW_MODES = ('generate', 'execute')
TEXT_LISTS = ('output', 'absent')
TEXTS = ('limit', 'review_mode', 'review_file', 'review_file_after')
KEYS = {'name', 'params', 'limit', 'rc', 'recap_hosts', 'recap_failed', *TEXT_LISTS, *TEXTS}


def load_cases(task):
    """The parsed cases.yml of a task, or None when the file is missing."""
    path = TASKS / task / CASES_FILE
    return yaml.safe_load(path.read_text()) if path.exists() else None


def declared_options(task):
    spec = yaml.safe_load((TASKS / task / 'meta' / 'argument_specs.yml').read_text())
    return set(spec['argument_specs']['main']['options'] or {})


def is_text_list(value):
    return isinstance(value, list) and all(isinstance(item, str) for item in value)


def problems(task, case):
    """Everything wrong with one case, so a broken cases.yml says what to fix instead of failing obscurely."""
    if not isinstance(case, dict):
        return [f'a case must be a mapping, got {case!r}']
    found = [f'unknown keys {sorted(set(case) - KEYS)}, expected some of {sorted(KEYS)}'] if set(case) - KEYS else []
    if not isinstance(case.get('name'), str) or not case['name']:
        found.append('name must be a non-empty text')
    # bool is an int in Python; rc: true is a mistake
    if type(case.get('rc')) is not int:
        found.append('rc must be an integer')
    params = case.get('params', {})
    if not isinstance(params, dict):
        found.append('params must be a mapping')
    elif set(params) - declared_options(task):
        found.append(f'params {sorted(set(params) - declared_options(task))} are not declared in meta/argument_specs.yml')
    found += [f'{key} must be a text' for key in TEXTS if key in case and not isinstance(case[key], str)]
    found += [f'{key} must be a list of texts' for key in (*TEXT_LISTS, 'recap_hosts') if key in case and not is_text_list(case[key])]
    failed = case.get('recap_failed', {})
    if not (isinstance(failed, dict) and all(type(count) is int for count in failed.values())):
        found.append('recap_failed must map each host to an integer')
    if 'review' in task_metadata(task):
        if case.get('review_mode') not in REVIEW_MODES:
            found.append(f'review_mode must be one of {list(REVIEW_MODES)}: the task has review in meta/task.yml')
    elif {'review_mode', 'review_file', 'review_file_after'} & set(case):
        found.append('review_mode, review_file and review_file_after are only for a task with review in meta/task.yml')
    return found


def collected_cases():
    params, ids = [], set()
    for task in TASK_NAMES:
        cases = load_cases(task)
        # a malformed file or a repeated name fails test_every_task_has_cases instead of breaking collection
        for index, case in enumerate(cases if isinstance(cases, list) else []):
            name = case.get('name') if isinstance(case, dict) else None
            case_id = f'{task}/{name if isinstance(name, str) and name else index}'
            case_id = f'{case_id}-{index}' if case_id in ids else case_id
            ids.add(case_id)
            params.append(pytest.param(task, case, id=case_id))
    return params


@pytest.mark.parametrize('task', TASK_NAMES)
def test_every_task_has_cases(task):
    cases = load_cases(task)
    names = [case.get('name') for case in cases if isinstance(case, dict)] if isinstance(cases, list) else []
    names = [name for name in names if isinstance(name, str)]

    assert cases is not None, f'business/tasks/{task}/{CASES_FILE} is missing'
    assert isinstance(cases, list) and cases, f'business/tasks/{task}/{CASES_FILE} must be a non-empty list of cases'
    assert sorted({name for name in names if names.count(name) > 1}) == [], 'case names repeat'


@pytest.mark.parametrize('task, case', collected_cases())
def test_task_case(tmp_path, task, case):
    assert problems(task, case) == [], f'business/tasks/{task}/{CASES_FILE}'
    params = dict(case.get('params', {}))
    review_file = tmp_path / 'review.txt'
    if 'review_mode' in case:
        params.update(task_review_mode=case['review_mode'], task_review_file=str(review_file))
    if 'review_file' in case:
        review_file.write_text(case['review_file'])

    result = run_task(tmp_path, task, params, limit=case.get('limit'))

    assert result.returncode == case['rc'], result.stdout + result.stderr
    if 'recap_hosts' in case:
        assert set(recap(result.stdout)) == set(case['recap_hosts']), result.stdout
    if 'recap_failed' in case:
        assert {host: counters['failed'] for host, counters in recap(result.stdout).items()} == case['recap_failed'], result.stdout
    assert [text for text in case.get('output', []) if text not in result.stdout] == [], result.stdout
    assert [text for text in case.get('absent', []) if text in result.stdout] == [], result.stdout
    if 'review_file_after' in case:
        assert review_file.exists() and review_file.read_text() == case['review_file_after']
