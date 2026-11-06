import json
import os
import re
import shutil
import socket
import subprocess
from pathlib import Path

import pytest
import yaml

TESTS = Path(__file__).resolve().parent
EXAMPLES = TESTS.parent / 'examples'
PLAYBOOKS = EXAMPLES / 'platform' / 'ansible'
TASKS = EXAMPLES / 'business' / 'tasks'
VAULT_PASSWORD_FILE = TESTS / 'example-vault-password.txt'
READ_WRITE = 'pipelines/readwrite/Jenkinsfile'
READ_ONLY = 'pipelines/readonly/Jenkinsfile'
# the basic form has no Jenkinsfile of its own in the examples: the tests derive one from the read-write job
BASIC = 'pipelines/basic/Jenkinsfile'


def task_metadata(name):
    return yaml.safe_load((TASKS / name / 'meta' / 'task.yml').read_text())


TASK_NAMES = sorted(path.name for path in TASKS.iterdir() if (path / 'meta' / 'task.yml').exists())
CREDENTIALS = dict(re.findall(r"(vaultCredentialsId|sshCredentialsId): '([^']+)'", (EXAMPLES / READ_WRITE).read_text()))


def run_ansible(command, root=EXAMPLES, vault=True, extra_vars=None, tmp_path=None):
    """Run an Ansible command the way Jenkins does: ansible.cfg through ANSIBLE_CONFIG, the vault
    password from a file, task parameters as a JSON file passed with -e @file."""
    args = list(command)
    if extra_vars is not None:
        vars_file = tmp_path / 'extra-vars.json'
        vars_file.write_text(json.dumps(extra_vars))
        args += ['-e', f'@{vars_file}']
    playbooks = root / 'platform' / 'ansible'
    env = {key: value for key, value in os.environ.items() if not key.startswith('ANSIBLE_')}
    env.update(ANSIBLE_CONFIG=str(playbooks / 'ansible.cfg'), ANSIBLE_NOCOLOR='1')
    if vault:
        env['ANSIBLE_VAULT_PASSWORD_FILE'] = str(VAULT_PASSWORD_FILE)
    return subprocess.run(args, cwd=playbooks, env=env, capture_output=True, text=True, timeout=300)


def recap(stdout):
    """Parse PLAY RECAP into {host: {'ok': n, 'failed': n, ...}}."""
    result = {}
    for line in stdout[stdout.index('PLAY RECAP'):].splitlines()[1:]:
        if ':' in line:
            host, counters = line.split(':', 1)
            result[host.strip()] = {key: int(value) for key, value in (item.split('=') for item in counters.split())}
    return result


@pytest.fixture
def examples_copy(tmp_path):
    """A writable copy of the examples for tests that break the inventory or a task on purpose."""
    target = tmp_path / 'examples'
    shutil.copytree(EXAMPLES, target)
    return target


JOB_XML = '''<?xml version="1.1" encoding="UTF-8"?>
<flow-definition plugin="workflow-job">
  <definition class="org.jenkinsci.plugins.workflow.cps.CpsScmFlowDefinition" plugin="workflow-cps">
    <scm class="hudson.plugins.git.GitSCM" plugin="git">
      <configVersion>2</configVersion>
      <userRemoteConfigs><hudson.plugins.git.UserRemoteConfig><url>file://{repo}</url></hudson.plugins.git.UserRemoteConfig></userRemoteConfigs>
      <branches><hudson.plugins.git.BranchSpec><name>*/main</name></hudson.plugins.git.BranchSpec></branches>
    </scm>
    <scriptPath>{script_path}</scriptPath>
    <lightweight>true</lightweight>
  </definition>
</flow-definition>'''


class Repository:
    """The git repository the jobs check out: the examples at its root, on branch main."""

    def __init__(self, path):
        self.path = path

    def git(self, *args):
        return subprocess.run(['git', '-C', str(self.path), '-c', 'user.name=test', '-c', 'user.email=test@example.com', *args],
                              check=True, capture_output=True, text=True).stdout.strip()

    def commit(self, changes, message):
        """Write {relative path: text} and commit; returns the commit to reset to afterwards."""
        head = self.git('rev-parse', 'HEAD')
        for relative, text in changes.items():
            target = self.path / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_text(text)
        self.git('add', '-A')
        self.git('commit', '-q', '-m', message)
        return head

    def reset(self, head):
        self.git('reset', '-q', '--hard', head)
        self.git('clean', '-q', '-fd')


@pytest.fixture(scope='session')
def repository(tmp_path_factory):
    path = tmp_path_factory.mktemp('repository')
    shutil.copytree(EXAMPLES, path, dirs_exist_ok=True, ignore=shutil.ignore_patterns('__pycache__'))
    basic = (EXAMPLES / READ_WRITE).read_text().replace("form: 'active-choices'", "form: 'basic'")
    assert "form: 'basic'" in basic
    (path / BASIC).parent.mkdir(parents=True)
    (path / BASIC).write_text(basic)
    repository = Repository(path)
    repository.git('init', '-q', '-b', 'main')
    repository.git('add', '-A')
    repository.git('commit', '-q', '-m', 'examples')
    return repository


def free_port():
    with socket.socket() as probe:
        probe.bind(('127.0.0.1', 0))
        return probe.getsockname()[1]


@pytest.fixture(scope='session')
def jenkins():
    from jenkins_server import server
    process, client = server.start(free_port(), CREDENTIALS, VAULT_PASSWORD_FILE.read_text().strip())
    yield client
    server.stop(process)


def create_registered_job(jenkins, repository, name, script_path):
    """Create a pipeline job and run its first build, which only registers the build parameters."""
    jenkins.delete_job(name)
    jenkins.create_job(name, JOB_XML.format(repo=repository.path, script_path=script_path))
    number = jenkins.start_build(name)
    assert jenkins.wait_for_build(name, number) == 'NOT_BUILT', jenkins.console(name, number)
    return name


def job_name(node_id):
    # parametrized test ids are long and similar; a hash of the full id keeps job names unique
    import hashlib
    readable = re.sub(r'[^A-Za-z0-9_-]', '-', node_id.split('::')[-1])[:50]
    return f'{readable}-{hashlib.sha1(node_id.encode()).hexdigest()[:8]}'


@pytest.fixture
def new_job(jenkins, repository, request):
    """A job of its own for one test."""
    created = []

    def create(script_path):
        created.append(create_registered_job(jenkins, repository, f'{job_name(request.node.nodeid)}-{len(created)}', script_path))
        return created[-1]

    yield create
    for name in created:
        jenkins.delete_job(name)


@pytest.fixture(scope='module')
def shared_job(jenkins, repository, request):
    """One read-write job shared by the tests of a module whose builds do not depend on each other."""
    name = create_registered_job(jenkins, repository, job_name(request.node.nodeid) + '-shared', READ_WRITE)
    yield name
    jenkins.delete_job(name)


@pytest.fixture
def vault_credential_removed(jenkins):
    """Remove the vault credential for one test and put it back afterwards."""
    credential = CREDENTIALS['vaultCredentialsId']
    assert jenkins.run_script(f'''
import com.cloudbees.plugins.credentials.SystemCredentialsProvider
import com.cloudbees.plugins.credentials.domains.Domain
def provider = SystemCredentialsProvider.getInstance()
println provider.getStore().removeCredentials(Domain.global(), provider.getCredentials().find {{ it.id == '{credential}' }})
''').strip() == 'true'
    yield credential
    password = VAULT_PASSWORD_FILE.read_text().strip()
    assert jenkins.run_script(f'''
import com.cloudbees.plugins.credentials.CredentialsScope
import com.cloudbees.plugins.credentials.SystemCredentialsProvider
import com.cloudbees.plugins.credentials.domains.Domain
import org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl
import hudson.util.Secret
println SystemCredentialsProvider.getInstance().getStore().addCredentials(Domain.global(), new StringCredentialsImpl(
    CredentialsScope.GLOBAL, '{credential}', 'ansible-vault password (example)', Secret.fromString('{password}')))
''').strip() == 'true'


TASK_PLAYBOOKS = ('run-task.yml', 'deploy.yml')


def run(jenkins, job, params):
    number = jenkins.start_build(job, params)
    return jenkins.wait_for_build(job, number), jenkins.console(job, number), number


def plan(text):
    return {'DEPLOY_PLAN': text}


def playbook_command_lines(console):
    """The ansible-playbook command lines of tasks and the deployment, leaving out the engine's own checks."""
    lines = [line.split('$ ansible-playbook ', 1)[1] for line in console.splitlines() if '$ ansible-playbook ' in line]
    return [line for line in lines if line.split()[0].rsplit('/', 1)[1] in TASK_PLAYBOOKS]


def playbook_runs(console):
    return [command.split()[0].rsplit('/', 1)[1] for command in playbook_command_lines(console)]
