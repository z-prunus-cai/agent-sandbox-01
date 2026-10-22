"""Walks the review flow in the Jenkins web UI and saves screenshots. Usage: screenshots.py <output dir> <database dir>
alice starts and confirms one build, viewer (read only) tries to confirm it, bob aborts a second build."""
import sys, time
from pathlib import Path
from playwright.sync_api import sync_playwright

URL = "http://127.0.0.1:8093"
OUT = Path(sys.argv[1])
DATABASES = Path(sys.argv[2])
PROMPT = "复核以下 SQL"
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

def open_page(page, path):
    page.goto(URL + path)
    page.wait_for_load_state("networkidle")
    time.sleep(1)

def shot(page, name, full=False):
    page.screenshot(path=str(OUT / name), full_page=full)
    print("saved", name)

def console_clip(page, number, name, end_text):
    """Saves the console from the Confirm stage down to end_text."""
    open_page(page, f"/job/sql-confirm/{number}/console")
    page.evaluate("window.scrollTo(0, 0)")
    top = page.get_by_text("[Pipeline] { (Confirm)").first.bounding_box()
    bottom = page.get_by_text(end_text).last.bounding_box()
    y = max(0, top["y"] - 60)
    page.screenshot(path=str(OUT / name), full_page=True,
                    clip={"x": 220, "y": y, "width": 1060, "height": bottom["y"] + bottom["height"] + 30 - y})
    print("saved", name)

def start_build(page, value, database, screenshot=None):
    open_page(page, "/job/sql-confirm/build?delay=0sec")
    values = page.locator('input[name="value"]')
    values.nth(1).fill(value)
    values.nth(2).fill(str(database))
    if screenshot:
        shot(page, screenshot)
    page.get_by_role("button", name="Build").click()
    time.sleep(2)
    open_page(page, "/job/sql-confirm/")
    number = int(page.locator("a[href*='/job/sql-confirm/']").filter(has_text="#").first.inner_text().strip().lstrip("#").split()[0])
    for _ in range(120):
        page.goto(URL + f"/job/sql-confirm/{number}/consoleText")
        if PROMPT in page.content():
            return number
        time.sleep(1)
    raise RuntimeError(f"build #{number} never asked for confirmation")

with sync_playwright() as playwright:
    browser = playwright.chromium.launch(executable_path="/opt/pw-browsers/chromium")
    alice = login(browser, "alice")
    bob = login(browser, "bob")
    viewer = login(browser, "viewer")

    # alice starts a build and reviews the SQL while it waits
    number = start_build(alice, "first", DATABASES / "ui-confirm.sqlite", "01-build-with-parameters.png")
    open_page(alice, f"/job/sql-confirm/{number}/")
    shot(alice, "02-build-page-waiting.png")
    open_page(alice, f"/job/sql-confirm/{number}/stages")
    shot(alice, "03-stages-waiting.png")
    console_clip(alice, number, "04-console-waiting.png", "中止")
    open_page(alice, f"/job/sql-confirm/{number}/input/")
    shot(alice, "05-input-page.png")
    open_page(alice, f"/job/sql-confirm/{number}/artifact/plan/change.sql/*view*/")
    shot(alice, "06-archived-sql.png")
    open_page(alice, "/")
    shot(alice, "07-executors-idle-while-waiting.png")

    # viewer may read the job but not build it, so cannot confirm
    open_page(viewer, f"/job/sql-confirm/{number}/input/")
    shot(viewer, "08-input-page-viewer.png")
    viewer.get_by_role("button", name="执行").click()
    viewer.wait_for_load_state("networkidle")
    shot(viewer, "09-viewer-presses-confirm.png")

    # alice confirms
    open_page(alice, f"/job/sql-confirm/{number}/input/")
    alice.get_by_role("button", name="执行").click()
    for _ in range(120):
        alice.goto(URL + f"/job/sql-confirm/{number}/consoleText")
        if "Finished:" in alice.content():
            break
        time.sleep(1)
    console_clip(alice, number, "10-console-confirmed.png", "Finished: SUCCESS")
    open_page(alice, f"/job/sql-confirm/{number}/stages")
    alice.get_by_text("Confirm", exact=True).last.click()
    time.sleep(1)
    alice.evaluate("window.scrollTo(0, 0)")
    shot(alice, "11-stages-confirmed.png")

    # alice starts a second build, bob aborts it
    aborted = start_build(alice, "second", DATABASES / "ui-abort.sqlite")
    open_page(bob, f"/job/sql-confirm/{aborted}/input/")
    bob.get_by_role("button", name="中止").click()
    for _ in range(60):
        bob.goto(URL + f"/job/sql-confirm/{aborted}/consoleText")
        if "Finished:" in bob.content():
            break
        time.sleep(1)
    console_clip(bob, aborted, "12-console-aborted.png", "Finished: ABORTED")
    open_page(bob, f"/job/sql-confirm/{aborted}/stages")
    shot(bob, "13-stages-aborted.png")
    open_page(bob, "/job/sql-confirm/")
    shot(bob, "14-job-history.png")
