"""Walks the confirmation flow in the Jenkins web UI and saves screenshots. Usage: screenshots.py <output dir> <database dir>"""
import sys, time
from pathlib import Path
from playwright.sync_api import sync_playwright

URL = "http://127.0.0.1:8093"
OUT = Path(sys.argv[1])
DATABASES = Path(sys.argv[2])
OUT.mkdir(parents=True, exist_ok=True)

def login(browser, user):
    context = browser.new_context(viewport={"width": 1280, "height": 860}, locale="en-US")
    page = context.new_page()
    page.goto(URL + "/login")
    page.fill('input[name="j_username"]', user)
    page.fill('input[name="j_password"]', user + "-example-password")
    page.click('button[name="Submit"]')
    page.wait_for_load_state("networkidle")
    return page

def shot(page, name, full=True):
    page.screenshot(path=str(OUT / name), full_page=full)
    print("saved", name)

def start_build(page, value, database, prefix):
    page.goto(URL + "/job/sql-confirm/build?delay=0sec")
    page.wait_for_load_state("networkidle")
    values = page.locator('input[name="value"]')
    values.nth(1).fill(value)
    values.nth(2).fill(str(database))
    shot(page, prefix + "-build-with-parameters.png")
    page.get_by_role("button", name="Build").click()
    time.sleep(2)
    page.goto(URL + "/job/sql-confirm/")
    page.wait_for_load_state("networkidle")
    number = int(page.locator("a[href*='/job/sql-confirm/']").filter(has_text="#").first.inner_text().strip().lstrip("#").split()[0])
    for _ in range(120):
        page.goto(URL + f"/job/sql-confirm/{number}/consoleText")
        if "Run this SQL" in page.content():
            break
        time.sleep(1)
    return number

with sync_playwright() as playwright:
    browser = playwright.chromium.launch(executable_path="/opt/pw-browsers/chromium")
    dba = login(browser, "dba")
    dev = login(browser, "dev")

    # approve: dba starts, dev cannot answer, dba approves
    number = start_build(dba, "first", DATABASES / "ui-approve.sqlite", "01")
    dba.goto(URL + f"/job/sql-confirm/{number}/console")
    dba.wait_for_load_state("networkidle")
    shot(dba, "02-console-waiting.png")
    dba.goto(URL + f"/job/sql-confirm/{number}/")
    dba.wait_for_load_state("networkidle")
    shot(dba, "03-build-page-waiting.png")
    dba.goto(URL + f"/job/sql-confirm/{number}/input/")
    dba.wait_for_load_state("networkidle")
    shot(dba, "04-input-page-dba.png")
    dba.goto(URL + f"/job/sql-confirm/{number}/artifact/plan/change.sql/*view*/")
    shot(dba, "05-archived-sql.png", full=False)
    dba.goto(URL + "/computer/")
    dba.wait_for_load_state("networkidle")
    shot(dba, "06-executors-idle-while-waiting.png", full=False)

    dev.goto(URL + f"/job/sql-confirm/{number}/input/")
    dev.wait_for_load_state("networkidle")
    shot(dev, "07-input-page-dev.png")
    run_buttons = dev.get_by_role("button", name="Run")
    if run_buttons.count():
        run_buttons.first.click()
        dev.wait_for_load_state("networkidle")
        shot(dev, "08-dev-presses-run.png")

    dba.goto(URL + f"/job/sql-confirm/{number}/input/")
    dba.wait_for_load_state("networkidle")
    dba.get_by_role("button", name="Run").first.click()
    for _ in range(120):
        dba.goto(URL + f"/job/sql-confirm/{number}/consoleText")
        if "Finished:" in dba.content():
            break
        time.sleep(1)
    dba.goto(URL + f"/job/sql-confirm/{number}/console")
    dba.wait_for_load_state("networkidle")
    shot(dba, "09-console-approved.png")

    # abort: dba rejects
    number = start_build(dba, "second", DATABASES / "ui-abort.sqlite", "10")
    dba.goto(URL + f"/job/sql-confirm/{number}/input/")
    dba.wait_for_load_state("networkidle")
    dba.get_by_role("button", name="Abort").first.click()
    for _ in range(60):
        dba.goto(URL + f"/job/sql-confirm/{number}/consoleText")
        if "Finished:" in dba.content():
            break
        time.sleep(1)
    dba.goto(URL + f"/job/sql-confirm/{number}/console")
    dba.wait_for_load_state("networkidle")
    shot(dba, "11-console-aborted.png")
    dba.goto(URL + "/job/sql-confirm/")
    dba.wait_for_load_state("networkidle")
    shot(dba, "12-job-history.png", full=False)
    browser.close()
