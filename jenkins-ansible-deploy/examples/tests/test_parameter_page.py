"""Integration tests in a real browser: the Active Choices parameter page of examples/Jenkinsfile."""
import os

import pytest
from playwright.sync_api import sync_playwright

pytestmark = pytest.mark.jenkins

SPECIAL_TEXT = 'a,"b" c\\d, @@x 中文=1+2'


@pytest.fixture
def page():
    with sync_playwright() as playwright:
        # CHROMIUM_PATH: use an already installed Chromium instead of the one `playwright install` downloads
        browser = playwright.chromium.launch(executable_path=os.environ.get('CHROMIUM_PATH'))
        yield browser.new_page()
        browser.close()


@pytest.fixture
def parameter_page(jenkins, new_job, page):
    job = new_job('Jenkinsfile')
    page.goto(f'{jenkins.url}/job/{job}/build?delay=0sec')
    page.get_by_text('未选择任务').first.wait_for()
    return job, page


def tick(page, list_id, value):
    # Jenkins draws its own checkbox over the input; the label receives the click
    page.locator(f'#{list_id} input[value="{value}"] + label').click()


def field(page, marker):
    # every form input follows a hidden marker input whose value is @@task.parameter
    return page.locator(f'input[type=hidden][value="@@{marker}"] + *')


def test_lists_show_inventory_groups_and_catalog_tasks_per_phase(parameter_page):
    _, page = parameter_page

    assert page.locator('#ecp_choice-target-groups label').all_inner_texts() == [
        'web (web-01, web-02)', 'api (api-01)', 'worker (worker-01, worker-02)']
    assert page.locator('#ecp_choice-pre-tasks input').evaluate_all('inputs => inputs.map(i => i.value)') == [
        'task_a', 'task_b', 'task_c', 'check_c']
    assert page.locator('#ecp_choice-post-tasks input').evaluate_all('inputs => inputs.map(i => i.value)') == [
        'task_c', 'check_c', 'check_a']


def test_selecting_a_task_shows_only_its_fields_prefilled_with_role_defaults(parameter_page):
    _, page = parameter_page

    tick(page, 'ecp_choice-pre-tasks', 'task_c')
    field(page, 'task_c.task_c_mode').wait_for()

    assert field(page, 'task_c.policy').evaluate('e => [...e.options].map(o => o.value)') == ['abort', 'warn']
    assert field(page, 'task_c.policy').input_value() == 'abort'
    assert field(page, 'task_c.task_c_mode').evaluate('e => [...e.options].map(o => o.value)') == ['plan', 'apply']
    assert field(page, 'task_c.task_c_mode').input_value() == 'plan'
    assert field(page, 'task_c.task_c_verbose').input_value() == 'false'
    assert field(page, 'task_c.task_c_label').get_attribute('type') == 'text'
    assert page.locator('input[type=hidden][value^="@@task_a."]').count() == 0


def test_integer_parameter_uses_number_input_and_required_parameter_is_marked(parameter_page):
    _, page = parameter_page

    tick(page, 'ecp_choice-pre-tasks', 'task_a')
    field(page, 'task_a.task_a_message').wait_for()

    assert field(page, 'task_a.task_a_repeat').get_attribute('type') == 'number'
    assert field(page, 'task_a.task_a_repeat').input_value() == '1'
    assert field(page, 'task_a.task_a_message').input_value() == ''
    assert page.locator('tr', has=field(page, 'task_a.task_a_message')).locator('span').inner_text() == '*'


def test_changing_the_selection_regenerates_the_form_and_drops_typed_values(parameter_page):
    _, page = parameter_page
    tick(page, 'ecp_choice-pre-tasks', 'task_a')
    field(page, 'task_a.task_a_message').fill('typed first')

    tick(page, 'ecp_choice-pre-tasks', 'task_c')
    field(page, 'task_c.task_c_mode').wait_for()

    assert field(page, 'task_a.task_a_message').input_value() == ''


def test_submitted_form_values_reach_ansible_unchanged(jenkins, parameter_page):
    job, page = parameter_page
    tick(page, 'ecp_choice-target-groups', 'api')
    tick(page, 'ecp_choice-pre-tasks', 'task_a')
    tick(page, 'ecp_choice-pre-tasks', 'task_c')
    field(page, 'task_c.task_c_mode').wait_for()
    field(page, 'task_a.task_a_message').wait_for()
    field(page, 'task_a.task_a_message').fill(SPECIAL_TEXT)
    field(page, 'task_a.task_a_repeat').fill('2')
    field(page, 'task_a.policy').select_option('warn')
    field(page, 'task_c.task_c_mode').select_option('apply')

    page.get_by_role('button', name='Build', exact=True).click()
    page.wait_for_url(f'**/job/{job}/**')

    assert jenkins.wait_for_build(job, 2) == 'SUCCESS'
    console = jenkins.console(job, 2)
    assert '    - task_a [warn] [task_a_message:a,"b" c\\d, @@x 中文=1+2, task_a_repeat:2, task_a_delay_seconds:0]' in console
    assert '"msg": "api-01: a,\\"b\\" c\\\\d, @@x 中文=1+2 (2/2)"' in console
    assert '"msg": "api-01: task_c mode=apply label="' in console
