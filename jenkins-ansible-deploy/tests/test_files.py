"""Checks every file of the deliverable: documents, examples and tests are ASCII only, and BRIEF.md states the
engine limits and plugin versions the sources hold."""
import re

import pytest

from conftest import ENGINE_SOURCE, EXAMPLES, TESTS

ROOT = EXAMPLES.parent
SKIPPED_DIRECTORIES = {'.jenkins', '__pycache__', '.pytest_cache'}
BINARY_SUFFIXES = {'.png'}
BRIEF = ROOT / 'BRIEF.md'


def deliverable_files():
    return sorted(path for path in ROOT.rglob('*')
                  if path.is_file() and path.suffix not in BINARY_SUFFIXES
                  and not SKIPPED_DIRECTORIES & set(path.relative_to(ROOT).parts))


@pytest.mark.parametrize('path', deliverable_files(), ids=lambda path: str(path.relative_to(ROOT)))
def test_file_is_ascii(path):
    lines = [number for number, line in enumerate(path.read_bytes().splitlines(), 1) if any(byte > 0x7e or (byte < 0x20 and byte != 0x09) for byte in line)]
    assert lines == [], f'{path.relative_to(ROOT)}: non-ASCII or control bytes on lines {lines}'


def brief_row(prefix):
    """The one BRIEF.md table row that starts with prefix."""
    rows = [line for line in BRIEF.read_text().splitlines() if line.startswith(prefix)]
    assert len(rows) == 1, f'BRIEF.md: expected one row starting {prefix!r}, found {len(rows)}'
    return rows[0]


def test_brief_states_every_limit_of_the_engine():
    limits = dict(re.findall(r'public static final int (\w+) = (\d+)', (ENGINE_SOURCE / 'Limits.groovy').read_text()))
    row = brief_row('| `Limits` |')

    assert limits
    assert {name: value for name, value in limits.items() if not re.search(rf'(?<!\d){value}(?!\d)', row)} == {}


def test_brief_names_the_pinned_version_of_every_plugin_it_lists():
    pinned = dict(re.findall(r'^([\w.-]+):(\S+)$', (TESTS / 'jenkins_server' / 'plugins.txt').read_text(), re.MULTILINE))
    row = brief_row('| Jenkins plugins |')
    # `name` then an optional (display name), then the version up to a comma, semicolon or space
    listed = {name: re.search(rf'`{re.escape(name)}`(?: \([^)]*\))? ([^\s,;]+)', row) for name in pinned if f'`{name}`' in row}

    assert listed
    assert {name: (pinned[name], match and match.group(1)) for name, match in listed.items()
            if not match or match.group(1) != pinned[name]} == {}
