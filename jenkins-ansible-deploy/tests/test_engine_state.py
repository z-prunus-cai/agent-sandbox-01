"""Static checks of the engine source: no enum is part of the build state that Jenkins reads back after a restart.

Jenkins reads that state under a sandbox that refuses the static call each Groovy enum makes when its class loads, so a
build holding an enum in a field, or in a variable of a method that is on the stack while a step can pause (input, node,
timeout, a playbook run ...), cannot resume (test_waiting_review_survives_a_jenkins_restart).
"""
import re

import pytest

from conftest import ENGINE_SOURCE

LIBRARY = ENGINE_SOURCE.parent.parent
ENUMS = sorted(path.stem for path in ENGINE_SOURCE.glob('*.groovy')
               if re.search(r'^(?:public\s+)?enum\s', path.read_text(), re.MULTILINE))
# steps during which Jenkins can save the build state and be restarted
PAUSING_STEPS = re.compile(r'\b(?:input|node|timeout|stage|ansiblePlaybook|withCredentials|withEnv|dir|sleep|waitUntil|parallel)\s*[({]')


def enum_members(sources, enums):
    """Names of properties and methods that return an enum: a def variable assigned from one holds an enum too."""
    names = '|'.join(enums)
    members = set()
    for text in sources:
        for name in re.findall(rf'\b(?:{names})\s+get([A-Z]\w*)\s*\(', text):
            members.add(name[0].lower() + name[1:])
        members |= set(re.findall(rf'\b(?:{names})\s+([a-z]\w*)\s*\(', text))
    return members


def enum_type(enums):
    """A type that is, or contains, one of the enums: Phase, deploy.Phase, Phase[], Map<Phase, Map>, List<? extends Phase>."""
    names = '|'.join(enums)
    return rf'(?:[\w.]*\b(?:{names})\b(?:\[\])?|\w+\s*<[^=;]*\b(?:{names})\b[^=;]*>)'


def methods(text):
    """[(name, parameters, body, cps)] of the methods of a class (4-space indent) or of a vars/ script (no indent)."""
    found = []
    pattern = re.compile(r'^( *)((?:@NonCPS\s*\n\1)?)(?:(?:private|public|protected|static|final)\s+)*(?:def|[\w.<>\[\], ?]+?)\s+'
                         r'(\w+)\s*\(([^)]*)\)\s*\{\s*$', re.MULTILINE)
    for match in pattern.finditer(text):
        indent, annotation, name, parameters = match.groups()
        if len(indent) not in (0, 4) or name in ('if', 'for', 'while', 'switch', 'catch'):
            continue
        end = re.compile(rf'^{indent}}}\s*$', re.MULTILINE).search(text, match.end())
        found.append((name, parameters, text[match.end():end.start() if end else len(text)], not annotation))
    return found


def waiting_methods(all_methods):
    """CPS methods that run a step that can pause, or call one that does: their variables are in the build state then."""
    cps = {name: body for name, _, body, is_cps in all_methods if is_cps}
    waiting = {name for name, body in cps.items() if PAUSING_STEPS.search(body)}
    while True:
        callers = {name for name, body in cps.items() if name not in waiting and any(re.search(rf'\b{w}\s*\(', body) for w in waiting)}
        if not callers:
            return waiting
        waiting |= callers


def problems(parameters, body, enums, members=()):
    """The lines of one waiting method that would put an enum into the build state."""
    enum = enum_type(enums)
    names = '|'.join(enums)
    member = '|'.join(members) or '(?!)'
    found = [f'parameter: {parameters.strip()}'] if re.search(rf'(?:^|,)\s*(?:final\s+)?{enum}\s+\w+', parameters) else []
    for line in body.splitlines():
        code = line.split('//')[0]
        if (re.search(rf'(?:^|[;({{,]|->)\s*(?:final\s+)?{enum}\s+[a-z]\w*\s*(?:=|;|$|\bin\b|->|,|\))', code)
                or re.search(rf'\b(?:def|var|final)\s+\w+\s*=.*\b(?:{names})\.(?!.*\.label\s*$)', code)
                or re.search(rf'\b(?:def|var|final)\s+\w+\s*=.*(?:\.(?:{member})\b(?!\s*[.(])|\b(?:{member})\s*\()(?!.*\.label\s*$)', code)
                or re.search(rf'\b(?:{names})\.values\(\)(?:\s+as\s+List\s*\))?\s*\.\w+\s*\{{', code)
                or re.search(r'\bswitch\s*\(', code)):
            found.append(line.strip())
    return found


@pytest.mark.parametrize('line', [
    'Phase phase = Phase.PRE', 'final Phase phase = Phase.PRE', 'deploy.Phase phase = Phase.PRE', 'Phase[] phases = Phase.values()',
    'Map<Phase, Map> chosen = [:]', 'List<? extends Phase> phases = []', 'def phase = Phase.PRE', 'def kind = RequestKind.of(requested)',
    'final policy = FailurePolicy.WARN', 'for (Phase phase in Phase.values()) {', 'tasks.each { Phase phase -> }', 'Phase phase; // comment',
    'switch (form()) {', 'def pol = planned.policy', 'def mode = accessMode(config)', 'Phase.values().each { p -> }',
])
def test_the_scanner_finds_an_enum_in_a_waiting_method(line):
    assert problems('', line, ['Phase', 'RequestKind', 'FailurePolicy'], ['policy', 'phase', 'accessMode']) != []


@pytest.mark.parametrize('line', [
    'String label = Phase.PRE.label', 'def label = Phase.PRE.label', 'if (RequestKind.of(requested) == RequestKind.PLAN_TEXT) {',
    'Map<String, Map> chosen = [:]', 'List<PlannedTask> tasks = plan.tasksIn(Phase.PRE)', 'String name // a Phase name',
    'def label = planned.policy.label', 'def name = planned.phaseName()',
])
def test_the_scanner_accepts_code_that_keeps_no_enum(line):
    assert problems('', line, ['Phase', 'RequestKind', 'FailurePolicy'], ['policy', 'phase', 'accessMode']) == []


def test_the_scanner_finds_enum_parameters_and_the_methods_that_wait():
    source = '''class X {
    void run(Phase phase) {
        helper()
    }

    private void helper() {
        script.input(message: 'x')
    }

    @NonCPS
    static String pure(Phase phase) {
        return phase.label
    }
}
'''
    found = methods(source)
    assert waiting_methods(found) == {'run', 'helper'}
    assert waiting_methods(methods(source.replace("script.input(message: 'x')", "script.node { work() }"))) == {'run', 'helper'}
    assert [problems(parameters, body, ['Phase']) for name, parameters, body, _ in found if name == 'run'] == [['parameter: Phase phase']]


def test_the_engine_has_enums():
    assert {'Phase', 'TaskOutcome', 'FailurePolicy', 'RequestKind'} <= set(ENUMS)


def test_no_class_keeps_an_enum_in_a_field():
    enum = enum_type(ENUMS)
    fields = {path.name: [line.strip() for line in path.read_text().splitlines()
                          if re.match(rf'^    (?:(?:private|public|protected|final)\s+)*{enum}\s+\w+\s*(?:=.*)?$', line)]
              for path in ENGINE_SOURCE.glob('*.groovy') if path.stem not in ENUMS}
    assert {name: lines for name, lines in fields.items() if lines} == {}


def test_no_method_that_can_pause_keeps_an_enum_in_a_variable():
    sources = list(ENGINE_SOURCE.glob('*.groovy')) + list((LIBRARY / 'vars').glob('*.groovy'))
    all_methods = [method for path in sources if path.stem not in ENUMS for method in methods(path.read_text())]
    waiting = waiting_methods(all_methods)
    members = enum_members([path.read_text() for path in sources], ENUMS)

    assert {'call', 'runTasks', 'run', 'runSteps', 'attempt', 'playbook', 'deploy', 'report'} <= waiting
    assert {'phase', 'policy', 'outcome', 'runOn'} <= members
    found = {name: problems(parameters, body, ENUMS, members) for name, parameters, body, cps in all_methods if cps and name in waiting}
    assert {name: lines for name, lines in found.items() if lines} == {}
