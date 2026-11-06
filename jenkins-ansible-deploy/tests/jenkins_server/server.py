"""The local, unsecured Jenkins the integration tests start (test use only, listens on 127.0.0.1).

Jenkins, the plugin manager and the pinned plugins are downloaded once into tests/.jenkins/, which git ignores.
"""
import hashlib
import os
import shutil
import subprocess
import time
import urllib.request
from pathlib import Path

from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric import ed25519

from jenkins_server.client import Jenkins

HERE = Path(__file__).resolve().parent
CACHE = HERE.parent / '.jenkins'
JENKINS_WAR = ('https://get.jenkins.io/war-stable/2.580.1/jenkins.war',
               '393bf2476352dd726519fd1f92ce66eac7d23c0936e6967420b717060c4c40d0')
PLUGIN_MANAGER = ('https://github.com/jenkinsci/plugin-installation-manager-tool/releases/download/2.15.0/'
                  'jenkins-plugin-manager-2.15.0.jar',
                  'a86853ec2e2933f37a4b471ba65099b61e03c87a80c2ef8fe2315eb135672d43')
START_SECONDS = 300
# executors of the built-in node; tests that take them away give back this number
EXECUTORS = 2

CREDENTIALS_SCRIPT = '''
import com.cloudbees.plugins.credentials.CredentialsScope
import com.cloudbees.plugins.credentials.SystemCredentialsProvider
import com.cloudbees.plugins.credentials.domains.Domain
import com.cloudbees.jenkins.plugins.sshcredentials.impl.BasicSSHUserPrivateKey
import org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl
import hudson.util.Secret

def store = SystemCredentialsProvider.getInstance().getStore()
def existing = SystemCredentialsProvider.getInstance().getCredentials()*.id
if (!(VAULT_ID in existing)) {
    store.addCredentials(Domain.global(), new StringCredentialsImpl(CredentialsScope.GLOBAL,
        VAULT_ID, 'ansible-vault password (example)', Secret.fromString(VAULT_PASSWORD)))
}
if (!(SSH_ID in existing)) {
    store.addCredentials(Domain.global(), new BasicSSHUserPrivateKey(CredentialsScope.GLOBAL, SSH_ID, 'deploy',
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
        raise RuntimeError(f'{url}: sha256 {digest} does not match {sha256}')


def groovy_string(text):
    return "'''" + text.replace('\\', '\\\\').replace("'''", "\\'\\'\\'") + "'''"


def install():
    """Download Jenkins and install the plugins of plugins.txt, once per plugins.txt content."""
    downloads = CACHE / 'downloads'
    downloads.mkdir(parents=True, exist_ok=True)
    war = downloads / 'jenkins.war'
    plugin_manager = downloads / 'jenkins-plugin-manager.jar'
    download(*JENKINS_WAR, war)
    download(*PLUGIN_MANAGER, plugin_manager)
    plugins = CACHE / 'jenkins-home' / 'plugins'
    marker = plugins / '.installed'
    wanted = hashlib.sha256((HERE / 'plugins.txt').read_bytes()).hexdigest()
    if not marker.exists() or marker.read_text() != wanted:
        subprocess.run(['java', '-jar', str(plugin_manager), '--war', str(war), '--plugin-download-directory', str(plugins),
                        '--plugin-file', str(HERE / 'plugins.txt')], check=True, capture_output=True)
        marker.write_text(wanted)
    return war


def start(port, credentials, vault_password):
    """Start Jenkins on port and create the credentials the Jenkinsfiles name. Returns (process, client)."""
    war = install()
    log = open(CACHE / 'jenkins.log', 'ab')
    process = subprocess.Popen(['java', '-Djenkins.install.runSetupWizard=false',
                                # lets the jobs check out the local file:// repository
                                '-Dhudson.plugins.git.GitSCM.ALLOW_LOCAL_CHECKOUT=true',
                                '-jar', str(war), f'--httpPort={port}', '--httpListenAddress=127.0.0.1'],
                               env={**os.environ, 'JENKINS_HOME': str(CACHE / 'jenkins-home')},
                               stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
    jenkins = Jenkins(f'http://127.0.0.1:{port}')
    deadline = time.monotonic() + START_SECONDS
    while not jenkins.is_ready():
        if process.poll() is not None or time.monotonic() > deadline:
            process.kill()
            raise RuntimeError(f'Jenkins did not start, see {CACHE / "jenkins.log"}')
        time.sleep(2)
    key = ed25519.Ed25519PrivateKey.generate().private_bytes(
        serialization.Encoding.PEM, serialization.PrivateFormat.OpenSSH, serialization.NoEncryption()).decode()
    jenkins.run_script(f'def VAULT_ID = {groovy_string(credentials["vaultCredentialsId"])}\n'
                       f'def SSH_ID = {groovy_string(credentials["sshCredentialsId"])}\n'
                       f'def VAULT_PASSWORD = {groovy_string(vault_password)}\ndef PRIVATE_KEY = {groovy_string(key)}\n'
                       + CREDENTIALS_SCRIPT)
    # set on every start: the home is reused, and a test killed while it had taken the executors away saved 0
    jenkins.run_script(f'Jenkins.get().setNumExecutors({EXECUTORS})')
    return process, jenkins


def stop(process):
    process.terminate()
    try:
        process.wait(60)
    except subprocess.TimeoutExpired:
        process.kill()
