"""Checks every directory under business/tasks, so a new task is covered without touching the tests."""
import json
import re

import pytest
import yaml
from ansible.vars.reserved import is_reserved_name

from conftest import EXAMPLES, PLAYBOOKS, TASKS, TASK_NAMES, run_ansible, task_metadata

ENGINE_SOURCE = EXAMPLES / 'platform' / 'jenkins' / 'src' / 'deploy'
TEMPLATE = re.compile(r'\{[{%#]')
# magic variables not starting with ansible_, from the Ansible docs reference_appendices/special_variables.rst
MAGIC_VARIABLES = {'group_names', 'groups', 'hostvars', 'inventory_dir', 'inventory_file', 'inventory_hostname',
                   'inventory_hostname_short', 'omit', 'play_hosts', 'playbook_dir', 'role_name', 'role_names', 'role_path'}


def labels(enum_file):
    """The labels of an engine enum, read from its Groovy source: NAME('label'...)."""
    return re.findall(r"^\s+[A-Z_]+\('([^']+)'", (ENGINE_SOURCE / enum_file).read_text(), re.MULTILINE)


def options(task):
    spec = yaml.safe_load((TASKS / task / 'meta' / 'argument_specs.yml').read_text())
    return spec['argument_specs']['main']['options'] or {}


def defaults(task):
    path = TASKS / task / 'defaults' / 'main.yml'
    return (yaml.safe_load(path.read_text()) or {}) if path.exists() else {}


@pytest.fixture(scope='module')
def inventory_variable_names():
    result = run_ansible(['ansible-inventory', '--list'])
    assert result.returncode == 0, result.stderr
    host_vars = json.loads(result.stdout)['_meta']['hostvars']
    return {name for variables in host_vars.values() for name in variables}


def test_every_task_directory_has_metadata_and_a_role():
    directories = sorted(path.name for path in TASKS.iterdir() if path.is_dir())

    assert directories
    assert TASK_NAMES == directories
    assert all((TASKS / task / 'tasks' / 'main.yml').exists() and (TASKS / task / 'meta' / 'argument_specs.yml').exists()
               for task in TASK_NAMES)


@pytest.mark.parametrize('task', TASK_NAMES)
def test_metadata_uses_only_engine_values(task):
    meta = task_metadata(task)

    assert set(meta) <= {'description', 'phases', 'access', 'run_on', 'policy', 'timeout_minutes', 'order', 'review'}
    assert meta['phases'] and set(meta['phases']) <= set(labels('Phase.groovy'))
    assert meta['access'] in labels('TaskAccess.groovy')
    assert meta['run_on'] in labels('RunLocation.groovy')
    assert meta['policy'] in labels('FailurePolicy.groovy')
    assert type(meta['timeout_minutes']) is int and 1 <= meta['timeout_minutes'] <= 240
    assert type(meta['order']) is int
    assert 'review' not in meta or (set(meta['review']) == {'timeout_minutes'} and 1 <= meta['review']['timeout_minutes'] <= 240)


def test_order_numbers_are_unique():
    orders = [task_metadata(task)['order'] for task in TASK_NAMES]

    assert len(orders) == len(TASK_NAMES)
    assert len(set(orders)) == len(orders)


@pytest.mark.parametrize('task', TASK_NAMES)
def test_every_option_has_a_supported_type(task):
    assert options(task) is not None
    for name, option in options(task).items():
        assert option.get('type', 'str') in labels('ParameterType.groovy'), name


@pytest.mark.parametrize('task', TASK_NAMES)
def test_choices_are_used_only_with_string_options(task):
    for name, option in options(task).items():
        assert not option.get('choices') or option.get('type', 'str') == 'str', name


@pytest.mark.parametrize('task', TASK_NAMES)
def test_option_names_cannot_override_inventory_reserved_or_engine_variables(task, inventory_variable_names):
    # task parameters are extra vars, the highest precedence: a clash would silently replace another value
    for name in options(task):
        assert name.startswith(task + '_'), name
        assert name not in inventory_variable_names, name
        assert not is_reserved_name(name) and name not in MAGIC_VARIABLES, name
        assert name not in labels('EngineVariable.groovy') and not name.startswith('ansible_'), name


@pytest.mark.parametrize('task', TASK_NAMES)
def test_documented_defaults_equal_role_defaults_and_cover_every_optional_option(task):
    # Ansible takes the real default from defaults/main.yml; the argument_specs default is documentation
    declared = options(task)

    assert {name: option['default'] for name, option in declared.items() if 'default' in option} == defaults(task)
    assert set(defaults(task)) == {name for name, option in declared.items() if not option.get('required')}


@pytest.mark.parametrize('task', TASK_NAMES)
def test_role_defaults_are_literal_values(task):
    assert [name for name, value in defaults(task).items() if TEMPLATE.search(str(value))] == []


@pytest.mark.parametrize('task', TASK_NAMES)
def test_description_has_no_characters_the_active_choices_sanitizer_turns_into_entities(task):
    assert not re.search(r'[！-～@=+"]', task_metadata(task)['description'])


@pytest.mark.parametrize('task', [task for task in TASK_NAMES if 'review' in task_metadata(task)])
def test_reviewed_task_reads_both_review_variables(task):
    source = '\n'.join(path.read_text() for path in (TASKS / task / 'tasks').glob('*.yml'))

    assert 'task_review_mode' in source and 'task_review_file' in source


def test_engine_variables_are_the_ones_the_platform_playbooks_read():
    engine = labels('EngineVariable.groovy')
    run_task = (PLAYBOOKS / 'run-task.yml').read_text()

    assert engine == ['task_role', 'task_hosts', 'task_review_mode', 'task_review_file', 'task_parameter_names']
    assert '{{ task_role }}' in run_task and '{{ task_hosts }}' in run_task
    assert 'task_parameter_names' in (PLAYBOOKS / 'check-variable-names.yml').read_text()
    assert [host for host in re.findall(r"^\s+[A-Z_]+\('[^']+', '([^']+)'\)", (ENGINE_SOURCE / 'RunLocation.groovy').read_text(), re.MULTILINE)] \
        == ['deploy_targets', 'localhost']
