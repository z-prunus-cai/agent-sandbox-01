"""Drives the confirmation PoC (alice and bob may build the job, viewer may only read) through the Jenkins REST API. Usage: drive.py <jenkins home> <scenario>...
CONFIRM_REPO names the git repository holding examples/ (default: ./repo next to this script)."""
import base64, json, os, shutil, sqlite3, subprocess, sys, tempfile, time, urllib.error, urllib.parse, urllib.request
from pathlib import Path

URL = "http://127.0.0.1:8093"
HOME = Path(sys.argv[1])
TOKENS = dict(line.split() for line in (HOME / "poc-tokens.txt").read_text().splitlines())
REPO = Path(os.environ.get("CONFIRM_REPO", Path(__file__).parent / "repo")).resolve()
DB_DIR = Path(__file__).parent / "databases"
DB_DIR.mkdir(exist_ok=True)
PROMPT = "复核以下 SQL"

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
        wait_for(number, PROMPT)
        call("alice", "POST", f"/job/sql-confirm/{number}/input/ConfirmSql/abort")
        print("parameter registration run:", number, finish(number))

def start(parameters, user="alice"):
    query = urllib.parse.urlencode(parameters)
    status, body, headers = call(user, "POST", "/job/sql-confirm/buildWithParameters?" + query)
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

def show(number, lines=(PROMPT, "Approved by", "SQL confirmed by", "SQL check", "Rejected by", "Timeout", "SQL changed", "FAILED", "Finished:")):
    for line in console(number).splitlines():
        if any(marker in line for marker in lines):
            print("  console:", line.strip()[:200])

def press(user, number, button):
    status, _, _ = call(user, "POST", f"/job/sql-confirm/{number}/input/ConfirmSql/" + ("proceedEmpty" if button == "confirm" else "abort"))
    print(f"{user} presses {button}: HTTP", status)
    return status

def scenario_self():
    """alice starts the build and confirms it herself; viewer cannot press either button."""
    database = DB_DIR / "self.sqlite"
    database.unlink(missing_ok=True)
    number = start({"SQL_TABLE": "items", "SQL_VALUE": "first", "DATABASE": str(database)})
    print(f"build #{number} started by alice; waiting for the confirmation prompt:", wait_for(number, PROMPT))
    time.sleep(3)
    print("busy executors while waiting:", busy_executors())
    print("database before confirming:", rows(database))
    for button in ("confirm", "abort"):
        press("viewer", number, button)
        time.sleep(2)
        print("build still waiting:", build(number)["building"], "| database:", rows(database))
    press("alice", number, "confirm")
    print("result:", finish(number))
    show(number)
    print("database after:", rows(database))
    print("archived artifacts:", [artifact["relativePath"] for artifact in build(number)["artifacts"]])

def scenario_other():
    """alice starts the build, bob confirms it."""
    database = DB_DIR / "other.sqlite"
    database.unlink(missing_ok=True)
    number = start({"SQL_VALUE": "confirmed by someone else", "DATABASE": str(database)})
    print(f"build #{number} started by alice; waiting:", wait_for(number, PROMPT))
    press("bob", number, "confirm")
    print("result:", finish(number))
    show(number)
    print("database after:", rows(database))

def scenario_abort():
    """alice starts the build, bob aborts it."""
    database = DB_DIR / "abort.sqlite"
    database.unlink(missing_ok=True)
    number = start({"SQL_VALUE": "second", "DATABASE": str(database)})
    print(f"build #{number} waiting:", wait_for(number, PROMPT))
    press("bob", number, "abort")
    print("result:", finish(number))
    show(number)
    print("apply playbook ran:", "apply-sql.yml" in console(number), "| database:", rows(database))

def scenario_timeout():
    database = DB_DIR / "timeout.sqlite"
    database.unlink(missing_ok=True)
    number = start({"SQL_VALUE": "third", "DATABASE": str(database), "CONFIRM_MINUTES": "1"})
    print(f"build #{number} waiting:", wait_for(number, PROMPT))
    started = time.time()
    print("result:", finish(number, 200), f"after {time.time() - started:.0f} s of waiting")
    show(number)
    print("apply playbook ran:", "apply-sql.yml" in console(number), "| database:", rows(database))

def scenario_invalid():
    database = DB_DIR / "invalid.sqlite"
    number = start({"SQL_VALUE": "it's", "DATABASE": str(database)})
    print(f"build #{number} result:", finish(number))
    show(number, ("SQL check", "Finished:"))
    print("reached confirmation:", PROMPT in console(number))

def scenario_injected():
    database = DB_DIR / "injected.sqlite"
    database.unlink(missing_ok=True)
    number = start({"SQL_VALUE": "x'); DROP TABLE items; --", "DATABASE": str(database)})
    print(f"build #{number} waiting:", wait_for(number, PROMPT))
    text = console(number)
    print("prompt shows:\n" + "\n".join("    " + line for line in text[text.index(PROMPT):].splitlines()[:8]))
    press("alice", number, "abort")
    print("result:", finish(number))

def scenario_restart():
    database = DB_DIR / "restart.sqlite"
    database.unlink(missing_ok=True)
    number = start({"SQL_VALUE": "after restart", "DATABASE": str(database)})
    print(f"build #{number} waiting:", wait_for(number, PROMPT))
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
    press("alice", number, "confirm")
    print("result:", finish(number))
    show(number, (PROMPT, "Resuming build", "SQL confirmed by", "Finished:"))
    print("database after:", rows(database))

def scenario_tamper():
    """Without Jenkins: plan, edit the SQL afterwards, then apply with the planned checksum."""
    work = Path(tempfile.mkdtemp())
    shutil.copytree(REPO / "ansible", work / "ansible")
    database = DB_DIR / "tamper.sqlite"
    database.unlink(missing_ok=True)
    (work / "plan.json").write_text(json.dumps({"sql_table": "items", "sql_value": "first"}))
    def playbook(name, variables):
        return subprocess.run(["ansible-playbook", "ansible/" + name, "-i", "ansible/inventory.yml", "-e", "@" + variables],
                              cwd=work, capture_output=True, text=True)
    print("plan exit code:", playbook("plan-sql.yml", "plan.json").returncode)
    planned = (work / "plan" / "change.sql.sha256").read_text().strip()
    sql = work / "plan" / "change.sql"
    sql.write_text(sql.read_text().replace("first", "changed"))
    (work / "apply.json").write_text(json.dumps({"confirmed_sha256": planned, "database": str(database)}))
    result = playbook("apply-sql.yml", "apply.json")
    print("apply exit code:", result.returncode)
    for line in (result.stdout + result.stderr).splitlines():
        if "SQL changed after confirmation" in line:
            print("  output:", line.strip()[:200])
            break
    print("database:", rows(database))
    shutil.rmtree(work)

ensure_job()
for name in sys.argv[2:]:
    print(f"=== {name}")
    globals()["scenario_" + name]()
