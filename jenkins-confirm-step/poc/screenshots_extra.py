"""Second screenshot pass: cropped console views, the stage graph and the executor panel while a build waits.
Usage: screenshots_extra.py <output dir> <database dir> <approved build> <aborted build>"""
import sys, time
from pathlib import Path
from playwright.sync_api import sync_playwright

URL = "http://127.0.0.1:8093"
OUT = Path(sys.argv[1])
DATABASES = Path(sys.argv[2])
APPROVED, ABORTED = int(sys.argv[3]), int(sys.argv[4])

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

def console_clip(page, number, name, start_text, end_text):
    """Saves the console from a little above start_text down to a little below end_text."""
    open_page(page, f"/job/sql-confirm/{number}/console")
    page.evaluate("window.scrollTo(0, 0)")
    top = page.get_by_text(start_text).first.bounding_box()
    bottom = page.get_by_text(end_text).last.bounding_box()
    y = max(0, top["y"] - 60)
    page.screenshot(path=str(OUT / name), full_page=True,
                    clip={"x": 220, "y": y, "width": 1060, "height": bottom["y"] + bottom["height"] + 30 - y})
    print("saved", name)

def shot(page, name):
    page.screenshot(path=str(OUT / name))
    print("saved", name)

with sync_playwright() as playwright:
    browser = playwright.chromium.launch(executable_path="/opt/pw-browsers/chromium")
    dba = login(browser, "dba")

    open_page(dba, "/job/sql-confirm/build?delay=0sec")
    values = dba.locator('input[name="value"]')
    values.nth(1).fill("third")
    values.nth(2).fill(str(DATABASES / "ui-extra.sqlite"))
    dba.get_by_role("button", name="Build").click()
    time.sleep(2)
    open_page(dba, "/job/sql-confirm/")
    number = int(dba.locator("a[href*='/job/sql-confirm/']").filter(has_text="#").first.inner_text().strip().lstrip("#").split()[0])
    for _ in range(120):
        dba.goto(URL + f"/job/sql-confirm/{number}/consoleText")
        if "Run this SQL" in dba.content():
            break
        time.sleep(1)
    print("waiting build", number)

    console_clip(dba, number, "02-console-waiting.png", "[Pipeline] { (Confirm)", "Run or Abort")
    open_page(dba, f"/job/sql-confirm/{number}/stages")
    shot(dba, "03b-stages-waiting.png")
    open_page(dba, "/")
    shot(dba, "06-executors-idle-while-waiting.png")
    open_page(dba, f"/job/sql-confirm/{number}/input/")
    dba.get_by_role("button", name="Abort").click()
    time.sleep(3)

    console_clip(dba, APPROVED, "09-console-approved.png", "[Pipeline] { (Confirm)", "Finished: SUCCESS")
    open_page(dba, f"/job/sql-confirm/{APPROVED}/stages")
    shot(dba, "09b-stages-approved.png")
    console_clip(dba, ABORTED, "11-console-aborted.png", "[Pipeline] { (Confirm)", "Finished: ABORTED")
    open_page(dba, f"/job/sql-confirm/{ABORTED}/stages")
    shot(dba, "11b-stages-aborted.png")
