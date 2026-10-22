import jenkins.model.Jenkins
import hudson.model.Item
import hudson.model.User
import hudson.security.HudsonPrivateSecurityRealm
import hudson.security.GlobalMatrixAuthorizationStrategy
import org.jenkinsci.plugins.matrixauth.PermissionEntry
import org.jenkinsci.plugins.matrixauth.AuthorizationType
import jenkins.security.ApiTokenProperty

def jenkins = Jenkins.get()
def realm = jenkins.securityRealm instanceof HudsonPrivateSecurityRealm ? jenkins.securityRealm : new HudsonPrivateSecurityRealm(false)
def tokens = new StringBuilder()
['admin', 'dba', 'dev'].each { name ->
    def user = User.getById(name, false) ?: realm.createAccount(name, name + '-example-password')
    def token = user.getProperty(ApiTokenProperty).tokenStore.generateNewToken('poc-' + System.currentTimeMillis())
    user.save()
    tokens.append(name).append(' ').append(token.plainValue).append('\n')
}
jenkins.setSecurityRealm(realm)
def strategy = new GlobalMatrixAuthorizationStrategy()
def user = { name -> new PermissionEntry(AuthorizationType.USER, name) }
strategy.add(Jenkins.ADMINISTER, user('admin'))
['dba', 'dev'].each { name ->
    strategy.add(Jenkins.READ, user(name))
    strategy.add(Item.READ, user(name))
    strategy.add(Item.BUILD, user(name))
}
// Job/Cancel lets a user answer any input with Abort (pipeline-input-step README:27); only dba gets it
strategy.add(Item.CANCEL, user('dba'))
jenkins.setAuthorizationStrategy(strategy)
jenkins.save()
new File(jenkins.rootDir, 'poc-tokens.txt').text = tokens.toString()
