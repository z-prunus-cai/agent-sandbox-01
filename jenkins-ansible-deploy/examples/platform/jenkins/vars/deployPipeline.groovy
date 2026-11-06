import deploy.AccessMode
import deploy.Catalog
import deploy.EngineVariable
import deploy.Labels
import deploy.Limits
import deploy.ParameterForm
import deploy.ParameterPage
import deploy.Phase
import deploy.Plan
import deploy.PlanParser
import deploy.RequestKind
import deploy.TaskRunner

/**
 * The deployment pipeline of one job. A Jenkinsfile loads this library from its own commit and calls
 * deployPipeline(commit: ..., mode: 'read-only' or 'read-write', form: 'active-choices' or 'basic',
 *                vaultCredentialsId: ..., sshCredentialsId: ... or null).
 */
def call(Map config) {
    List<String> errors = []
    AccessMode mode = Labels.find(AccessMode.values() as List, config.mode, 'mode', errors)
    ParameterForm form = Labels.find(ParameterForm.values() as List, config.form, 'form', errors)
    if (errors) error "[deploy] Jenkinsfile settings: ${errors.join('; ')}"
    // properties() fills params with the defaults it registers, so the request is read before it runs
    Map requested = [:] + params
    TaskRunner runner = new TaskRunner(this, [vaultCredentialsId: config.vaultCredentialsId, sshCredentialsId: config.sshCredentialsId])
    Catalog catalog = null
    stage('Prepare') {
        node {
            checkout([$class: 'GitSCM', branches: [[name: config.commit]], userRemoteConfigs: scm.userRemoteConfigs])
            catalog = readCatalog(errors)
            if (errors) error '[deploy] task catalog or inventory:\n  - ' + errors.join('\n  - ')
            // a change to the tasks or groups shows on the parameter page from the next build on
            properties([disableConcurrentBuilds(), parameters(new ParameterPage(this, catalog, mode, form).definitions())])
            // after properties(), so a wrong credential still leaves the parameter page registered
            runner.checkCredentials()
            writeJSON file: "${TaskRunner.RUN_DIRECTORY}/variable-names.json", json: [(EngineVariable.PARAMETER_NAMES.label): catalog.parameterNames()]
            runner.playbook([playbook: 'platform/ansible/check-variable-names.yml', extras: "-e @${TaskRunner.RUN_DIRECTORY}/variable-names.json"])
            stash name: TaskRunner.SOURCE_STASH
        }
    }
    RequestKind kind = RequestKind.of(requested)
    if (kind == RequestKind.UNREGISTERED) {
        currentBuild.result = 'NOT_BUILT'
        currentBuild.description = 'Parameters registered; run again with Build with Parameters'
        echo "[deploy] ${currentBuild.description}"
        return
    }
    Plan plan = null
    stage('Plan') {
        Map raw = kind == RequestKind.PLAN_TEXT ? readPlan(requested[Limits.PLAN_PARAMETER].toString(), errors) :
                new ParameterPage(this, catalog, mode, form).draft(requested, errors)
        if (raw != null) plan = PlanParser.parse(raw, catalog, mode, errors)
        if (errors) error '[deploy] the plan has problems:\n  - ' + errors.join('\n  - ')
        echo plan.describe()
    }
    try {
        runner.runPhase(plan, Phase.PRE)
        if (plan.deploy) {
            stage('Deploy') {
                runner.deploy(plan.targets)
            }
        }
        runner.runPhase(plan, Phase.POST)
    } finally {
        stage('Report') {
            runner.report()
        }
    }
}

private Catalog readCatalog(List<String> errors) {
    Map<String, Map> sources = [:]
    for (def file in findFiles(glob: 'business/tasks/*/meta/task.yml')) {
        String directory = file.path.substring(0, file.path.length() - 'meta/task.yml'.length())
        String name = directory.tokenize('/').last()
        sources[name] = [task    : readYaml(file: file.path),
                         spec    : fileExists("${directory}meta/argument_specs.yml") ? readYaml(file: "${directory}meta/argument_specs.yml") : null,
                         defaults: fileExists("${directory}defaults/main.yml") ? readYaml(file: "${directory}defaults/main.yml") : null]
    }
    return Catalog.parse(sources, readYaml(file: 'business/inventory/hosts.yml'), errors)
}

private Map readPlan(String text, List<String> errors) {
    echo "[deploy] using ${Limits.PLAN_PARAMETER}; every other parameter is ignored"
    if (text.length() > Limits.MAX_PLAN_CHARACTERS) {
        errors << "${Limits.PLAN_PARAMETER} is longer than ${Limits.MAX_PLAN_CHARACTERS} characters".toString()
        return null
    }
    Object raw
    try {
        raw = readYaml(text: text)
    } catch (Exception e) {
        errors << "${Limits.PLAN_PARAMETER} is not valid YAML: ${e.message}".toString()
        return null
    }
    if (raw instanceof Map) return raw
    errors << "${Limits.PLAN_PARAMETER} must be a YAML mapping".toString()
    return null
}
