"""Static checks of the engine source: no enum is part of the build state that Jenkins reads back after a restart.

Jenkins reads that state under a sandbox that refuses the static call each Groovy enum makes when its class loads, so a
build holding an enum in a field or a long-lived variable cannot resume (test_waiting_review_survives_a_jenkins_restart).
"""
import re

from conftest import ENGINE_SOURCE

LIBRARY = ENGINE_SOURCE.parent.parent
ENUMS = sorted(path.stem for path in ENGINE_SOURCE.glob('*.groovy') if re.search(r'^enum ', path.read_text(), re.MULTILINE))
ENUM_TYPE = re.compile(r'\b(' + '|'.join(ENUMS) + r')\b')


def declarations(text):
    """Declarations at the first indentation level: fields of a class, or variables of a vars/ script's call method."""
    return [line for line in text.splitlines()
            if re.match(r'^    (?:(?:private|public|protected|final)\s+)*[A-Z][\w<>, ]*\s+\w+\s*(=.*)?$', line) and 'static' not in line]


def test_the_engine_has_enums():
    assert {'Phase', 'TaskOutcome', 'FailurePolicy'} <= set(ENUMS)


def test_no_class_keeps_an_enum_in_a_field():
    fields = {path.name: [line.strip() for line in declarations(path.read_text()) if ENUM_TYPE.search(line)]
              for path in ENGINE_SOURCE.glob('*.groovy') if path.stem not in ENUMS}
    assert {name: lines for name, lines in fields.items() if lines} == {}


def test_the_pipeline_keeps_no_enum_in_a_variable():
    variables = [line.strip() for path in (LIBRARY / 'vars').glob('*.groovy')
                 for line in declarations(path.read_text()) if ENUM_TYPE.search(line)]
    assert variables == []
