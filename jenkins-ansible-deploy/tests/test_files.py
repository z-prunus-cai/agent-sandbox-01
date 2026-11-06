"""Checks every file of the deliverable: documents, examples and tests are ASCII only."""
from pathlib import Path

import pytest

from conftest import EXAMPLES

ROOT = EXAMPLES.parent
SKIPPED_DIRECTORIES = {'.jenkins', '__pycache__', '.pytest_cache'}
BINARY_SUFFIXES = {'.png'}


def deliverable_files():
    return sorted(path for path in ROOT.rglob('*')
                  if path.is_file() and path.suffix not in BINARY_SUFFIXES
                  and not SKIPPED_DIRECTORIES & set(path.relative_to(ROOT).parts))


@pytest.mark.parametrize('path', deliverable_files(), ids=lambda path: str(path.relative_to(ROOT)))
def test_file_is_ascii(path):
    lines = [number for number, line in enumerate(path.read_bytes().splitlines(), 1) if any(byte > 0x7e or (byte < 0x20 and byte != 0x09) for byte in line)]
    assert lines == [], f'{path.relative_to(ROOT)}: non-ASCII or control bytes on lines {lines}'
