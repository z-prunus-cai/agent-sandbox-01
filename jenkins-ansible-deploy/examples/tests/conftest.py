import json
import os
import re
import shutil
import subprocess
from pathlib import Path

import pytest

EXAMPLES = Path(__file__).resolve().parent.parent
ANSIBLE_DIR = EXAMPLES / 'ansible'
VAULT_PASSWORD_FILE = EXAMPLES / 'tests' / 'example-vault-password.txt'


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
    return subprocess.run(args, cwd=project, env=env, capture_output=True, text=True)


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


@pytest.fixture(scope='session')
def jenkins():
    from jenkins_server.client import Jenkins
    if not os.environ.get('JENKINS_URL') or not os.environ.get('DEPLOY_REPO_URL'):
        pytest.skip('set JENKINS_URL and DEPLOY_REPO_URL (printed by tests/jenkins_server/setup_jenkins.py)')
    return Jenkins(os.environ['JENKINS_URL'])


JOB_XML = '''<?xml version="1.1" encoding="UTF-8"?>
<flow-definition plugin="workflow-job">
  <definition class="org.jenkinsci.plugins.workflow.cps.CpsScmFlowDefinition" plugin="workflow-cps">
    <scm class="hudson.plugins.git.GitSCM" plugin="git">
      <configVersion>2</configVersion>
      <userRemoteConfigs><hudson.plugins.git.UserRemoteConfig><url>{repo}</url></hudson.plugins.git.UserRemoteConfig></userRemoteConfigs>
      <branches><hudson.plugins.git.BranchSpec><name>*/main</name></hudson.plugins.git.BranchSpec></branches>
    </scm>
    <scriptPath>jenkins-ansible-deploy/examples/{jenkinsfile}</scriptPath>
    <lightweight>true</lightweight>
  </definition>
</flow-definition>'''


@pytest.fixture
def new_job(jenkins, request):
    """Create a pipeline job for one test and run its first build, which registers the build parameters."""
    created = []

    def create(jenkinsfile):
        name = re.sub(r'[^A-Za-z0-9_-]', '-', request.node.name)[:80] + f'-{len(created)}'
        jenkins.delete_job(name)
        jenkins.create_job(name, JOB_XML.format(repo=os.environ['DEPLOY_REPO_URL'], jenkinsfile=jenkinsfile))
        created.append(name)
        number = jenkins.start_build(name)
        assert jenkins.wait_for_build(name, number) == 'NOT_BUILT', jenkins.console(name, number)
        return name

    yield create
    for name in created:
        jenkins.delete_job(name)
