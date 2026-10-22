import jenkins.model.Jenkins
import hudson.model.Item
import hudson.model.User
import hudson.security.HudsonPrivateSecurityRealm
import hudson.security.GlobalMatrixAuthorizationStrategy
import org.jenkinsci.plugins.matrixauth.PermissionEntry
import org.jenkinsci.plugins.matrixauth.AuthorizationType
import jenkins.security.ApiTokenProperty

// PoC users: alice and bob may build the job (and therefore confirm), viewer may only look
def jenkins = Jenkins.get()
def realm = jenkins.securityRealm instanceof HudsonPrivateSecurityRealm ? jenkins.securityRealm : new HudsonPrivateSecurityRealm(false)
def tokens = new StringBuilder()
['admin', 'alice', 'bob', 'viewer'].each { name ->
    def user = User.getById(name, false) ?: realm.createAccount(name, name + '-example-password')
    def token = user.getProperty(ApiTokenProperty).tokenStore.generateNewToken('poc-' + System.currentTimeMillis())
    user.save()
    tokens.append(name).append(' ').append(token.plainValue).append('\n')
}
jenkins.setSecurityRealm(realm)
def strategy = new GlobalMatrixAuthorizationStrategy()
def user = { name -> new PermissionEntry(AuthorizationType.USER, name) }
strategy.add(Jenkins.ADMINISTER, user('admin'))
['alice', 'bob', 'viewer'].each { name ->
    strategy.add(Jenkins.READ, user(name))
    strategy.add(Item.READ, user(name))
}
// Without a submitter, Job/Build is what lets a user press the confirm or the abort button; nobody gets Job/Cancel
['alice', 'bob'].each { name -> strategy.add(Item.BUILD, user(name)) }
jenkins.setAuthorizationStrategy(strategy)
jenkins.save()
new File(jenkins.rootDir, 'poc-tokens.txt').text = tokens.toString()
