"""Checks that keep the task catalog, the roles and the parameter page consistent."""
import json
import re

import pytest
import yaml
from ansible.vars.reserved import is_reserved_name

from conftest import ANSIBLE_DIR, CATALOG, run_ansible

SUPPORTED_TYPES = {'str', 'int', 'bool'}
TEMPLATE = re.compile(r'\{[{%#]')
# magic variables not starting with ansible_, from the Ansible docs reference_appendices/special_variables.rst
MAGIC_VARIABLES = {'group_names', 'groups', 'hostvars', 'inventory_dir', 'inventory_file', 'inventory_hostname',
                   'inventory_hostname_short', 'omit', 'play_hosts', 'playbook_dir', 'role_name', 'role_names', 'role_path'}


def options(role):
    spec = yaml.safe_load((ANSIBLE_DIR / 'roles' / role / 'meta' / 'argument_specs.yml').read_text())
    return spec['argument_specs']['main']['options'] or {}


def defaults(role):
    path = ANSIBLE_DIR / 'roles' / role / 'defaults' / 'main.yml'
    return (yaml.safe_load(path.read_text()) or {}) if path.exists() else {}


@pytest.fixture(scope='module')
def inventory_variable_names():
    result = run_ansible(['ansible-inventory', '--list'])
    assert result.returncode == 0, result.stderr
    host_vars = json.loads(result.stdout)['_meta']['hostvars']
    return {name for variables in host_vars.values() for name in variables}


@pytest.mark.parametrize('role', CATALOG)
def test_every_option_has_a_supported_type(role):
    for name, option in options(role).items():
        assert option.get('type', 'str') in SUPPORTED_TYPES, name


@pytest.mark.parametrize('role', CATALOG)
def test_choices_are_used_only_with_string_options(role):
    for name, option in options(role).items():
        assert not option.get('choices') or option.get('type', 'str') == 'str', name


@pytest.mark.parametrize('role', CATALOG)
def test_option_names_cannot_override_inventory_reserved_or_engine_variables(role, inventory_variable_names):
    # task parameters are extra vars, the highest precedence: a clash would silently replace another value
    for name in options(role):
        assert name.startswith(role + '_'), name
        assert name not in inventory_variable_names, name
        assert not is_reserved_name(name) and name not in MAGIC_VARIABLES, name
        assert name not in {'task_role', 'task_hosts'} and not name.startswith('ansible_'), name


@pytest.mark.parametrize('role', CATALOG)
def test_documented_defaults_equal_role_defaults_and_cover_every_optional_option(role):
    # Ansible takes the real default from defaults/main.yml; the argument_specs default is documentation
    declared = options(role)

    assert {name: option['default'] for name, option in declared.items() if 'default' in option} == defaults(role)
    assert set(defaults(role)) == {name for name, option in declared.items() if not option.get('required')}


@pytest.mark.parametrize('role', CATALOG)
def test_role_defaults_are_literal_values(role):
    assert [name for name, value in defaults(role).items() if TEMPLATE.search(str(value))] == []


@pytest.mark.parametrize('role', CATALOG)
def test_catalog_entry_uses_only_known_values(role):
    entry = CATALOG[role]

    assert set(entry['phases']) <= {'pre', 'post'} and entry['phases']
    assert entry['run_on'] in {'targets', 'controller'}
    assert entry['policy'] in {'abort', 'warn'}
    assert type(entry['timeout_minutes']) is int and entry['timeout_minutes'] >= 1


@pytest.mark.parametrize('role', CATALOG)
def test_description_has_no_characters_the_active_choices_sanitizer_turns_into_entities(role):
    assert not re.search(r'[！-～@=+"]', CATALOG[role]['description'])
