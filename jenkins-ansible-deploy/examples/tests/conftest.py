import hashlib
import json
import os
import re
import shutil
import subprocess
from pathlib import Path

import pytest
import yaml

EXAMPLES = Path(__file__).resolve().parent.parent
ANSIBLE_DIR = EXAMPLES / 'ansible'
VAULT_PASSWORD_FILE = EXAMPLES / 'tests' / 'example-vault-password.txt'
CATALOG = yaml.safe_load((EXAMPLES / 'jenkins' / 'tasks.yml').read_text())['tasks']


def run_ansible(command, project=ANSIBLE_DIR, vault=True, extra_vars=None, tmp_path=None):
    """Run an Ansible command the way Jenkins does: ansible.cfg through ANSIBLE_CONFIG, the vault
    password from a file, task parameters as a JSON file passed with -e @file."""
    args = list(command)
    if extra_vars is not None:
        vars_file = tmp_path / 'extra-vars.json'
        vars_file.write_text(json.dumps(extra_vars))
        args += ['-e', f'@{vars_file}']
    env = {key: value for key, value in os.environ.items() if not key.startswith('ANSIBLE_')}
    env.update(ANSIBLE_CONFIG=str(project / 'ansible.cfg'), ANSIBLE_NOCOLOR='1')
    if vault:
        env['ANSIBLE_VAULT_PASSWORD_FILE'] = str(VAULT_PASSWORD_FILE)
    return subprocess.run(args, cwd=project, env=env, capture_output=True, text=True, timeout=300)


def recap(stdout):
    """Parse PLAY RECAP into {host: {'ok': n, 'failed': n, ...}}."""
    result = {}
    for line in stdout[stdout.index('PLAY RECAP'):].splitlines()[1:]:
        if ':' in line:
            host, counters = line.split(':', 1)
            result[host.strip()] = {key: int(value) for key, value in (item.split('=') for item in counters.split())}
    return result


@pytest.fixture
def ansible_copy(tmp_path):
    """A writable copy of the Ansible directory for tests that break the inventory on purpose."""
    target = tmp_path / 'ansible'
    shutil.copytree(ANSIBLE_DIR, target)
    return target


JOB_XML = '''<?xml version="1.1" encoding="UTF-8"?>
<flow-definition plugin="workflow-job">
  <definition class="org.jenkinsci.plugins.workflow.cps.CpsScmFlowDefinition" plugin="workflow-cps">
    <scm class="hudson.plugins.git.GitSCM" plugin="git">
      <configVersion>2</configVersion>
      <userRemoteConfigs><hudson.plugins.git.UserRemoteConfig><url>{repo}</url></hudson.plugins.git.UserRemoteConfig></userRemoteConfigs>
      <branches><hudson.plugins.git.BranchSpec><name>*/main</name></hudson.plugins.git.BranchSpec></branches>
    </scm>
    <scriptPath>{script_path}</scriptPath>
    <lightweight>true</lightweight>
  </definition>
</flow-definition>'''


@pytest.fixture(scope='session')
def jenkins():
    from jenkins_server.client import Jenkins
    if not os.environ.get('JENKINS_URL') or not os.environ.get('DEPLOY_REPO_URL'):
        pytest.skip('set JENKINS_URL and DEPLOY_REPO_URL (printed by tests/jenkins_server/setup_jenkins.py)')
    return Jenkins(os.environ['JENKINS_URL'])


def create_registered_job(jenkins, name, jenkinsfile):
    """Create a pipeline job and run its first build, which only registers the build parameters."""
    jenkins.delete_job(name)
    script_path = f'{EXAMPLES.parent.name}/{EXAMPLES.name}/{jenkinsfile}'
    jenkins.create_job(name, JOB_XML.format(repo=os.environ['DEPLOY_REPO_URL'], script_path=script_path))
    number = jenkins.start_build(name)
    assert jenkins.wait_for_build(name, number) == 'NOT_BUILT', jenkins.console(name, number)
    return name


def job_name(node_id):
    # parametrized test ids are long and similar; a hash of the full id keeps job names unique
    readable = re.sub(r'[^A-Za-z0-9_-]', '-', node_id.split('::')[-1])[:50]
    return f'{readable}-{hashlib.sha1(node_id.encode()).hexdigest()[:8]}'


@pytest.fixture
def new_job(jenkins, request):
    """A job of its own for one test."""
    created = []

    def create(jenkinsfile):
        created.append(create_registered_job(jenkins, f'{job_name(request.node.nodeid)}-{len(created)}', jenkinsfile))
        return created[-1]

    yield create
    for name in created:
        jenkins.delete_job(name)


@pytest.fixture(scope='module')
def shared_active_job(jenkins, request):
    """One job of the Active Choices Jenkinsfile, shared by the tests of a module whose builds do not depend on each other."""
    name = create_registered_job(jenkins, job_name(request.node.nodeid) + '-shared', 'Jenkinsfile')
    yield name
    jenkins.delete_job(name)


@pytest.fixture
def vault_credential_removed(jenkins):
    """Remove the vault credential for one test and put it back afterwards."""
    jenkins.run_script('''
import com.cloudbees.plugins.credentials.SystemCredentialsProvider
import com.cloudbees.plugins.credentials.domains.Domain
def provider = SystemCredentialsProvider.getInstance()
provider.getStore().removeCredentials(Domain.global(), provider.getCredentials().find { it.id == 'ansible-vault-password' })
''')
    yield
    password = VAULT_PASSWORD_FILE.read_text().strip()
    jenkins.run_script(f'''
import com.cloudbees.plugins.credentials.CredentialsScope
import com.cloudbees.plugins.credentials.SystemCredentialsProvider
import com.cloudbees.plugins.credentials.domains.Domain
import org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl
import hudson.util.Secret
SystemCredentialsProvider.getInstance().getStore().addCredentials(Domain.global(), new StringCredentialsImpl(
    CredentialsScope.GLOBAL, 'ansible-vault-password', 'ansible-vault password (example)', Secret.fromString('{password}')))
''')
