"""Start a local, unsecured Jenkins for the integration tests (test use only, listens on 127.0.0.1).

    python3 tests/jenkins_server/setup_jenkins.py --home /path/to/empty/dir

It downloads Jenkins and the pinned plugins, copies this example into a local git repository,
starts Jenkins and creates the two credentials the Jenkinsfiles use. Running it again while Jenkins
is up only commits the current files to that repository again.
At the end it prints the two environment variables the integration tests read.
"""
import argparse
import hashlib
import os
import shutil
import subprocess
import sys
import time
import urllib.request
from pathlib import Path

from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric import ed25519

sys.path.insert(0, str(Path(__file__).resolve().parent))
from client import Jenkins  # noqa: E402

HERE = Path(__file__).resolve().parent
SAMPLE_DIR = HERE.parent.parent  # the Jenkinsfiles' baseDir is <SAMPLE_DIR name>/examples inside the repository
JENKINS_WAR = ('https://get.jenkins.io/war-stable/2.580.1/jenkins.war',
               '393bf2476352dd726519fd1f92ce66eac7d23c0936e6967420b717060c4c40d0')
PLUGIN_MANAGER = ('https://github.com/jenkinsci/plugin-installation-manager-tool/releases/download/2.15.0/'
                  'jenkins-plugin-manager-2.15.0.jar',
                  'a86853ec2e2933f37a4b471ba65099b61e03c87a80c2ef8fe2315eb135672d43')

CREDENTIALS_SCRIPT = '''
import com.cloudbees.plugins.credentials.CredentialsScope
import com.cloudbees.plugins.credentials.SystemCredentialsProvider
import com.cloudbees.plugins.credentials.domains.Domain
import com.cloudbees.jenkins.plugins.sshcredentials.impl.BasicSSHUserPrivateKey
import org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl
import hudson.util.Secret
def store = SystemCredentialsProvider.getInstance().getStore()
def existing = SystemCredentialsProvider.getInstance().getCredentials()*.id
if (!('ansible-vault-password' in existing)) {
    store.addCredentials(Domain.global(), new StringCredentialsImpl(CredentialsScope.GLOBAL,
        'ansible-vault-password', 'ansible-vault password (example)', Secret.fromString(VAULT_PASSWORD)))
}
if (!('deploy-ssh-key' in existing)) {
    store.addCredentials(Domain.global(), new BasicSSHUserPrivateKey(CredentialsScope.GLOBAL, 'deploy-ssh-key', 'deploy',
        new BasicSSHUserPrivateKey.DirectEntryPrivateKeySource(PRIVATE_KEY), null, 'SSH key (example)'))
}
println SystemCredentialsProvider.getInstance().getCredentials()*.id
'''


def download(url, sha256, target):
    if not target.exists():
        with urllib.request.urlopen(url) as response, open(target, 'wb') as out:
            shutil.copyfileobj(response, out)
    digest = hashlib.sha256(target.read_bytes()).hexdigest()
    if digest != sha256:
        target.unlink()
        sys.exit(f'{url}: sha256 {digest} does not match {sha256}')


def groovy_string(text):
    return "'''" + text.replace('\\', '\\\\').replace("'''", "\\'\\'\\'") + "'''"


def sync_repository(repo):
    """Copy the sample directory into a git repository that the Jenkins jobs check out."""
    if not (repo / '.git').exists():
        repo.mkdir(parents=True)
        subprocess.run(['git', 'init', '-q', '-b', 'main', str(repo)], check=True)
    target = repo / SAMPLE_DIR.name
    shutil.rmtree(target, ignore_errors=True)
    shutil.copytree(SAMPLE_DIR, target, ignore=shutil.ignore_patterns('__pycache__', '.pytest_cache'))
    git = ['git', '-C', str(repo), '-c', 'user.name=test', '-c', 'user.email=test@example.com']
    subprocess.run(git + ['add', '-A'], check=True)
    subprocess.run(git + ['commit', '-q', '--allow-empty', '-m', 'sync'], check=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--home', required=True, type=Path, help='working directory for Jenkins, downloads and the repository')
    parser.add_argument('--port', type=int, default=8080)
    args = parser.parse_args()
    home = args.home.resolve()
    url = f'http://127.0.0.1:{args.port}'
    repo = home / 'repo'
    jenkins = Jenkins(url)

    sync_repository(repo)
    if not jenkins.is_ready():
        downloads = home / 'downloads'
        downloads.mkdir(parents=True, exist_ok=True)
        war = downloads / 'jenkins.war'
        plugin_manager = downloads / 'jenkins-plugin-manager.jar'
        download(*JENKINS_WAR, war)
        download(*PLUGIN_MANAGER, plugin_manager)
        jenkins_home = home / 'jenkins-home'
        subprocess.run(['java', '-jar', str(plugin_manager), '--war', str(war),
                        '--plugin-download-directory', str(jenkins_home / 'plugins'),
                        '--plugin-file', str(HERE / 'plugins.txt')], check=True)
        log = open(home / 'jenkins.log', 'ab')
        subprocess.Popen(['java', '-Djenkins.install.runSetupWizard=false',
                          # allows the jobs to check out the local file:// repository
                          '-Dhudson.plugins.git.GitSCM.ALLOW_LOCAL_CHECKOUT=true',
                          '-jar', str(war), f'--httpPort={args.port}', '--httpListenAddress=127.0.0.1'],
                         env={**os.environ, 'JENKINS_HOME': str(jenkins_home)},
                         stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        deadline = time.time() + 300
        while not jenkins.is_ready():
            if time.time() > deadline:
                sys.exit(f'Jenkins did not start, see {home / "jenkins.log"}')
            time.sleep(3)

    key = ed25519.Ed25519PrivateKey.generate().private_bytes(
        serialization.Encoding.PEM, serialization.PrivateFormat.OpenSSH, serialization.NoEncryption()).decode()
    vault_password = (SAMPLE_DIR / 'tests' / 'example-vault-password.txt').read_text().strip()
    print(jenkins.run_script(f'def VAULT_PASSWORD = {groovy_string(vault_password)}\n'
                             f'def PRIVATE_KEY = {groovy_string(key)}\n' + CREDENTIALS_SCRIPT).strip())
    print(f'export JENKINS_URL={url}')
    print(f'export DEPLOY_REPO_URL=file://{repo}')


if __name__ == '__main__':
    main()
