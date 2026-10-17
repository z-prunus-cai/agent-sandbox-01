"""Checks that keep the task catalog, the roles and the parameter page consistent."""
import re

import pytest
import yaml

from conftest import ANSIBLE_DIR, EXAMPLES

CATALOG = yaml.safe_load((EXAMPLES / 'jenkins' / 'tasks.yml').read_text())['tasks']
SUPPORTED_TYPES = {'str', 'path', 'int', 'bool'}


def options(role):
    spec = yaml.safe_load((ANSIBLE_DIR / 'roles' / role / 'meta' / 'argument_specs.yml').read_text())
    return spec['argument_specs']['main']['options'] or {}


def defaults(role):
    path = ANSIBLE_DIR / 'roles' / role / 'defaults' / 'main.yml'
    return (yaml.safe_load(path.read_text()) or {}) if path.exists() else {}


@pytest.mark.parametrize('role', CATALOG)
def test_every_option_has_a_supported_type_and_role_prefix(role):
    for name, option in options(role).items():
        assert option.get('type', 'str') in SUPPORTED_TYPES, name
        assert not option.get('choices') or option.get('type', 'str') == 'str', name
        assert name.startswith(role + '_'), name


@pytest.mark.parametrize('role', CATALOG)
def test_defaults_live_only_in_defaults_main_and_cover_every_optional_option(role):
    # Ansible takes the real default from defaults/main.yml; a default in argument_specs is documentation only
    declared = options(role)

    assert [name for name, option in declared.items() if 'default' in option] == []
    assert set(defaults(role)) == {name for name, option in declared.items() if not option.get('required')}


@pytest.mark.parametrize('role', CATALOG)
def test_catalog_entry_uses_only_known_values(role):
    entry = CATALOG[role]

    assert set(entry['phases']) <= {'pre', 'post'} and entry['phases']
    assert entry['run_on'] in {'targets', 'controller'}
    assert entry['policy'] in {'abort', 'warn'}
    assert isinstance(entry['timeout_minutes'], int) and entry['timeout_minutes'] > 0


@pytest.mark.parametrize('role', CATALOG)
def test_description_has_no_characters_the_active_choices_sanitizer_turns_into_entities(role):
    assert not re.search(r'[！-～@=+"]', CATALOG[role]['description'])
