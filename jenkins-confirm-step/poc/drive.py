"""Drives the confirmation PoC through the Jenkins REST API. Usage: drive.py <jenkins home> <scenario>...
CONFIRM_REPO names the git repository holding examples/ (default: ./repo next to this script)."""
import base64, json, os, sqlite3, sys, time, urllib.error, urllib.parse, urllib.request
from pathlib import Path

URL = "http://127.0.0.1:8093"
HOME = Path(sys.argv[1])
TOKENS = dict(line.split() for line in (HOME / "poc-tokens.txt").read_text().splitlines())
REPO = Path(os.environ.get("CONFIRM_REPO", Path(__file__).parent / "repo")).resolve()
DB_DIR = Path(__file__).parent / "databases"
DB_DIR.mkdir(exist_ok=True)

def call(user, method, path, data=None, content_type=None):
    request = urllib.request.Request(URL + path, data=data, method=method)
    request.add_header("Authorization", "Basic " + base64.b64encode(f"{user}:{TOKENS[user]}".encode()).decode())
    if content_type:
        request.add_header("Content-Type", content_type)
    try:
        with urllib.request.urlopen(request, timeout=60) as response:
            return response.status, response.read().decode(errors="replace"), response.headers
    except urllib.error.HTTPError as error:
        return error.code, error.read().decode(errors="replace"), error.headers

def ensure_job():
    config = f"""<?xml version='1.1' encoding='UTF-8'?>
<flow-definition plugin="workflow-job">
  <definition class="org.jenkinsci.plugins.workflow.cps.CpsScmFlowDefinition" plugin="workflow-cps">
    <scm class="hudson.plugins.git.GitSCM" plugin="git">
      <configVersion>2</configVersion>
      <userRemoteConfigs><hudson.plugins.git.UserRemoteConfig><url>file://{REPO}</url></hudson.plugins.git.UserRemoteConfig></userRemoteConfigs>
      <branches><hudson.plugins.git.BranchSpec><name>*/main</name></hudson.plugins.git.BranchSpec></branches>
    </scm>
    <scriptPath>Jenkinsfile</scriptPath>
    <lightweight>false</lightweight>
  </definition>
</flow-definition>"""
    status, _, _ = call("admin", "GET", "/job/sql-confirm/api/json")
    if status == 404:
        status, body, _ = call("admin", "POST", "/createItem?name=sql-confirm", config.encode(), "application/xml")
        print("create job:", status)
        # the job only knows its parameters after one run; abort that run at the confirmation
        status, _, headers = call("admin", "POST", "/job/sql-confirm/build")
        number = wait_for_queue(headers["Location"])
        wait_for(number, "Run this SQL")
        call("dba", "POST", f"/job/sql-confirm/{number}/input/ConfirmSql/abort")
        print("parameter registration run:", number, finish(number))

def start(parameters):
    query = urllib.parse.urlencode(parameters)
    status, body, headers = call("admin", "POST", "/job/sql-confirm/buildWithParameters?" + query)
    return wait_for_queue(headers["Location"])

def wait_for_queue(queue):
    for _ in range(120):
        status, body, _ = call("admin", "GET", urllib.parse.urlparse(queue).path + "api/json")
        executable = json.loads(body).get("executable")
        if executable:
            return executable["number"]
        time.sleep(1)
    raise RuntimeError("build did not start")

def build(number):
    return json.loads(call("admin", "GET", f"/job/sql-confirm/{number}/api/json")[1])

def console(number):
    return call("admin", "GET", f"/job/sql-confirm/{number}/consoleText")[1]

def wait_for(number, text, seconds=180):
    for _ in range(seconds):
        if text in console(number):
            return True
        time.sleep(1)
    return False

def finish(number, seconds=240):
    for _ in range(seconds):
        info = build(number)
        if not info["building"]:
            return info["result"]
        time.sleep(1)
    return "STILL RUNNING"

def busy_executors():
    return json.loads(call("admin", "GET", "/computer/api/json")[1])["busyExecutors"]

def rows(database):
    if not database.exists():
        return "no database file"
    with sqlite3.connect(database) as connection:
        tables = [row[0] for row in connection.execute("select name from sqlite_master where type='table'")]
        return {table: connection.execute(f"select * from {table}").fetchall() for table in tables}

def show(number, lines=("Run this SQL", "confirmed by", "SQL check", "applied", "Aborted by", "Rejected by", "Timeout", "SQL changed", "FAILED", "Finished:")):
    for line in console(number).splitlines():
        if any(marker in line for marker in lines):
            print("  console:", line.strip()[:200])

def scenario_approve():
    database = DB_DIR / "approve.sqlite"
    database.unlink(missing_ok=True)
    number = start({"SQL_TABLE": "items", "SQL_VALUE": "first", "DATABASE": str(database)})
    print(f"build #{number} started; waiting for the confirmation prompt:", wait_for(number, "Run this SQL"))
    time.sleep(3)
    print("busy executors while waiting:", busy_executors())
    print("database before confirming:", rows(database))
    status, body, _ = call("dev", "POST", f"/job/sql-confirm/{number}/input/ConfirmSql/proceedEmpty")
    print("dev presses Run: HTTP", status)
    time.sleep(2)
    print("build still waiting after dev:", build(number)["building"], "| database:", rows(database))
    status, body, _ = call("dev", "POST", f"/job/sql-confirm/{number}/input/ConfirmSql/abort")
    print("dev presses Abort: HTTP", status)
    time.sleep(2)
    print("build still waiting after dev:", build(number)["building"], "| database:", rows(database))
    status, body, _ = call("dba", "POST", f"/job/sql-confirm/{number}/input/ConfirmSql/proceedEmpty")
    print("dba presses Run: HTTP", status)
    print("result:", finish(number))
    show(number)
    print("database after:", rows(database))
    artifacts = [artifact["relativePath"] for artifact in build(number)["artifacts"]]
    print("archived artifacts:", artifacts)

def scenario_abort():
    database = DB_DIR / "abort.sqlite"
    database.unlink(missing_ok=True)
    number = start({"SQL_VALUE": "second", "DATABASE": str(database)})
    print(f"build #{number} waiting:", wait_for(number, "Run this SQL"))
    status, _, _ = call("dba", "POST", f"/job/sql-confirm/{number}/input/ConfirmSql/abort")
    print("dba presses Abort: HTTP", status)
    print("result:", finish(number))
    show(number)
    print("apply playbook ran:", "apply-sql.yml" in console(number), "| database:", rows(database))

def scenario_timeout():
    database = DB_DIR / "timeout.sqlite"
    database.unlink(missing_ok=True)
    number = start({"SQL_VALUE": "third", "DATABASE": str(database), "CONFIRM_MINUTES": "1"})
    print(f"build #{number} waiting:", wait_for(number, "Run this SQL"))
    started = time.time()
    print("result:", finish(number, 200), f"after {time.time() - started:.0f} s of waiting")
    show(number)
    print("apply playbook ran:", "apply-sql.yml" in console(number), "| database:", rows(database))

def scenario_invalid():
    database = DB_DIR / "invalid.sqlite"
    number = start({"SQL_VALUE": "it's", "DATABASE": str(database)})
    print(f"build #{number} result:", finish(number))
    show(number)
    print("reached confirmation:", "Run this SQL" in console(number))

def scenario_injected():
    database = DB_DIR / "injected.sqlite"
    database.unlink(missing_ok=True)
    number = start({"SQL_VALUE": "x'); DROP TABLE items; --", "DATABASE": str(database)})
    print(f"build #{number} waiting:", wait_for(number, "Run this SQL"))
    text = console(number)
    start_index = text.index("Run this SQL")
    print("prompt shows:\n" + "\n".join("    " + line for line in text[start_index:].splitlines()[:8]))
    call("dba", "POST", f"/job/sql-confirm/{number}/input/ConfirmSql/abort")
    print("result after dba aborts:", finish(number))

def scenario_restart():
    database = DB_DIR / "restart.sqlite"
    database.unlink(missing_ok=True)
    number = start({"SQL_VALUE": "after restart", "DATABASE": str(database)})
    print(f"build #{number} waiting:", wait_for(number, "Run this SQL"))
    status, _, _ = call("admin", "POST", "/restart")
    print("admin restarts Jenkins: HTTP", status)
    time.sleep(10)
    for _ in range(120):
        try:
            if call("admin", "GET", "/api/json")[0] == 200:
                break
        except OSError:
            pass
        time.sleep(2)
    for _ in range(60):
        try:
            if build(number)["building"] and "Resuming build" in console(number):
                break
        except (OSError, ValueError):
            pass
        time.sleep(2)
    print("after restart, build still waiting:", build(number)["building"], "| console says resumed:", "Resuming build" in console(number))
    status, _, _ = call("dba", "POST", f"/job/sql-confirm/{number}/input/ConfirmSql/proceedEmpty")
    print("dba presses Run: HTTP", status)
    print("result:", finish(number))
    show(number, ("Run this SQL", "Resuming build", "confirmed by", "Finished:"))
    print("database after:", rows(database))

ensure_job()
for name in sys.argv[2:]:
    print(f"=== {name}")
    globals()["scenario_" + name]()
