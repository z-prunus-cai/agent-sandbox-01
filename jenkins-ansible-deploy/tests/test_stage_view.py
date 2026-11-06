"""Integration test in a real browser: the review is read in the Stage View popup on the job page (pipeline-stage-view)."""
import os

import pytest
from playwright.sync_api import expect, sync_playwright

from conftest import READ_WRITE, plan

pytestmark = pytest.mark.jenkins

expect.set_options(timeout=15_000)


@pytest.fixture
def page():
    with sync_playwright() as playwright:
        # CHROMIUM_PATH: a Chromium already on the machine, where the browser Playwright downloads cannot be installed
        browser = playwright.chromium.launch(executable_path=os.environ.get('CHROMIUM_PATH'))
        context = browser.new_context()
        yield context.new_page()
        context.close()
        browser.close()


def test_stage_view_popup_shows_the_review_file_line_by_line_with_run_and_abort(jenkins, new_job, page):
    job = new_job(READ_WRITE)
    number = jenkins.start_build(job, plan("{skip_deploy: true, pre: [{name: task_d, params: {task_d_items: 'a, b'}}]}"))
    [pending] = jenkins.wait_for_input(job, number)
    try:
        page.goto(f'{jenkins.url}/job/{job}/')
        page.locator('td.stage-cell.PAUSED_PENDING_INPUT').last.locator('div').first.click()

        # the popup is the element around the prompt; its text keeps the review file's line breaks
        popup = page.get_by_text('as reviewed below?').first.locator('xpath=..')
        expect(popup).to_be_visible()
        text = popup.inner_text()
        assert text.startswith('Run task_d as reviewed below? (sha256 ')
        assert '\n\nchange a\nchange b\n' in text
        expect(popup.get_by_text('Run', exact=True)).to_be_visible()
        expect(popup.get_by_text('Abort', exact=True)).to_be_visible()
    finally:
        jenkins.abort_input(job, number, pending['id'])
        jenkins.wait_for_build(job, number)
