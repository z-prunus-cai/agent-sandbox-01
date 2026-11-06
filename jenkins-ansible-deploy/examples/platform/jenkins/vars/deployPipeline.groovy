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

/** The pipeline of one job; config: commit, mode (AccessMode label), form (ParameterForm label), vaultCredentialsId, sshCredentialsId. */
// No enum is held in a variable that lives while tasks run: Jenkins reads the build state back after a restart under a
// sandbox that refuses the static call each Groovy enum makes when its class loads (see deploy.PlannedTask)
def call(Map config) {
    List<String> errors = []
    Labels.find(AccessMode.values() as List, config.mode, 'mode', errors)
    Labels.find(ParameterForm.values() as List, config.form, 'form', errors)
    if (errors) error "[deploy] Jenkinsfile settings: ${errors.join('; ')}"
    // properties() fills params with the defaults it registers, so the request is read before it runs
    Map requested = [:] + params
    TaskRunner runner = new TaskRunner(this, [vaultCredentialsId: config.vaultCredentialsId, sshCredentialsId: config.sshCredentialsId])
    Catalog catalog = null
    ParameterPage page = null
    stage('Prepare') {
        node {
            // leftovers of an earlier build would otherwise be stashed and run with this commit's files
            deleteDir()
            checkout([$class: 'GitSCM', branches: [[name: config.commit]], userRemoteConfigs: scm.userRemoteConfigs])
            catalog = readCatalog(errors)
            if (errors) error '[deploy] task catalog or inventory:\n  - ' + errors.join('\n  - ')
            // a change to the tasks or groups shows on the parameter page from the next build on
            page = new ParameterPage(this, catalog, accessMode(config), Labels.of(ParameterForm.values() as List, config.form))
            properties([disableConcurrentBuilds(), parameters(page.definitions())])
            // after properties(), so a wrong credential still leaves the parameter page registered
            runner.checkCredentials()
            writeJSON file: "${TaskRunner.RUN_DIRECTORY}/variable-names.json", json: [(EngineVariable.PARAMETER_NAMES.label): catalog.parameterNames()]
            runner.playbook([playbook: 'platform/ansible/check-variable-names.yml', extras: "-e @${TaskRunner.RUN_DIRECTORY}/variable-names.json"])
            stash name: TaskRunner.SOURCE_STASH
        }
    }
    if (RequestKind.of(requested) == RequestKind.UNREGISTERED) {
        currentBuild.result = 'NOT_BUILT'
        currentBuild.description = 'Parameters registered; run again with Build with Parameters'
        echo "[deploy] ${currentBuild.description}"
        return
    }
    Plan plan = null
    stage('Plan') {
        RequestKind kind = RequestKind.of(requested)
        echo "[deploy] reading the plan from the ${kind.label}"
        Map raw = kind == RequestKind.PLAN_TEXT ? readPlan(requested[Limits.PLAN_PARAMETER].toString(), errors) : page.draft(requested, errors)
        if (raw != null) plan = PlanParser.parse(raw, catalog, accessMode(config), errors)
        if (errors) error '[deploy] the plan has problems:\n  - ' + errors.join('\n  - ')
        echo plan.describe()
    }
    try {
        runner.runTasks(plan, plan.tasksIn(Phase.PRE))
        if (plan.deploy) {
            stage('Deploy') {
                runner.deploy(plan.targets)
            }
        }
        runner.runTasks(plan, plan.tasksIn(Phase.POST))
    } finally {
        stage('Report') {
            runner.report()
        }
    }
}

private AccessMode accessMode(Map config) {
    return Labels.of(AccessMode.values() as List, config.mode)
}

private Catalog readCatalog(List<String> errors) {
    Map<String, Map> sources = [:]
    for (def file in findFiles(glob: 'business/tasks/*/meta/task.yml')) {
        String name = file.path.tokenize('/')[2]
        String directory = "business/tasks/${name}/"
        sources[name] = [task    : readYaml(file: file.path),
                         role    : fileExists("${directory}tasks/main.yml"),
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
