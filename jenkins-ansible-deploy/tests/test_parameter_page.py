"""Integration tests in a real browser: the Active Choices parameter page of the read-write job."""
import os
import re

import pytest
from playwright.sync_api import expect, sync_playwright

from conftest import ENGINE_SOURCE, READ_WRITE, phase_tasks, task_label

pytestmark = pytest.mark.jenkins

FORM_STYLES = dict(re.findall(r"^\s+(\w+)\s*: '([^']*)'", (ENGINE_SOURCE / 'FormStyle.groovy').read_text(), re.MULTILINE))
SPECIAL_TEXT = 'a,"b" c\\d, @@x caf\u00e9=1+2'


# Active Choices regenerates the form through a server round trip; the default 5 s can be short on a cold Jenkins
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


@pytest.fixture
def parameter_page(jenkins, new_job, page):
    job = new_job(READ_WRITE)
    page.goto(f'{jenkins.url}/job/{job}/build?delay=0sec')
    page.get_by_text('No task selected').first.wait_for()
    return job, page


def tick(page, list_id, value):
    # Jenkins draws its own checkbox over the input; the label receives the click
    page.locator(f'#{list_id} input[value="{value}"] + label').click()


def field(page, marker):
    # every form input follows a hidden marker input whose value is @@task.parameter
    return page.locator(f'input[type=hidden][value="@@{marker}"] + *')


def show_more(page, phase):
    # Jenkins draws its own checkbox over the input; the label receives the click
    page.locator('label', has_text=f'{phase}_SHOW_MORE').first.click()


def test_lists_show_inventory_groups_and_only_main_tasks_until_more_is_shown(parameter_page):
    _, page = parameter_page

    expect(page.locator('#ecp_choice-target-groups label')).to_have_text(
        ['web (web-01, web-02)', 'api (api-01)', 'worker (worker-01, worker-02)'])
    expect(page.locator('#ecp_choice-pre-tasks label')).to_have_text([task_label(task) for task in phase_tasks('pre', 'main')])
    expect(page.locator('#ecp_choice-post-tasks label')).to_have_text([task_label(task) for task in phase_tasks('post', 'main')])
    expect(page.locator('#ecp_choice-pre-more-tasks')).to_have_count(1)
    expect(page.locator('#ecp_choice-post-more-tasks')).to_have_count(1)
    expect(page.locator('#ecp_choice-pre-more-tasks label')).to_have_count(0)
    expect(page.locator('#ecp_choice-post-more-tasks label')).to_have_count(0)


def test_showing_more_lists_the_less_used_tasks_and_hiding_them_drops_their_selection(parameter_page):
    _, page = parameter_page

    show_more(page, 'PRE')
    expect(page.locator('#ecp_choice-pre-more-tasks label')).to_have_text([task_label(task) for task in phase_tasks('pre', 'more')])
    tick(page, 'ecp_choice-pre-more-tasks', 'task_d')
    expect(field(page, 'task_d.task_d_items')).to_be_visible()
    show_more(page, 'PRE')

    expect(page.locator('#ecp_choice-pre-more-tasks label')).to_have_count(0)
    expect(page.locator('input[type=hidden][value^="@@task_d."]')).to_have_count(0)
    expect(page.locator('#ecp_choice-post-more-tasks label')).to_have_count(0)


def test_filter_box_narrows_a_task_list(parameter_page):
    _, page = parameter_page

    # the plugin filters on keyup, which fill() does not send
    page.locator('#ecp_choice-pre-tasks').locator('xpath=..').locator('input.uno_choice_filter').press_sequentially('task_b')

    # the filter matches anywhere in the label, so another task naming task_b in its description also stays
    expect(page.locator('#ecp_choice-pre-tasks label:visible')).to_have_text(
        [task_label(task) for task in phase_tasks('pre', 'main') if 'task_b' in task_label(task)])


def test_order_field_is_prefilled_with_the_catalog_order(parameter_page):
    _, page = parameter_page

    tick(page, 'ecp_choice-pre-tasks', 'task_c')

    expect(field(page, 'task_c.order')).to_have_attribute('type', 'number')
    expect(field(page, 'task_c.order')).to_have_value('30')


def test_every_form_style_survives_the_sanitizer(parameter_page):
    _, page = parameter_page

    tick(page, 'ecp_choice-pre-tasks', 'task_a')                   # text and number inputs, and the policy select
    field(page, 'task_a.order').wait_for()

    styles = page.locator('[style]').evaluate_all('elements => elements.map(element => element.getAttribute("style"))')
    assert set(FORM_STYLES) == {'group', 'title', 'table', 'label', 'value', 'text', 'number', 'select', 'hint'}
    assert {name: style for name, style in FORM_STYLES.items() if style not in styles} == {}


def test_selecting_a_task_shows_only_its_fields_prefilled_with_role_defaults(parameter_page):
    _, page = parameter_page

    tick(page, 'ecp_choice-pre-tasks', 'task_c')

    expect(field(page, 'task_c.policy').locator('option')).to_have_text(['abort', 'warn'])
    expect(page.get_by_text('abort: stop the pipeline; warn: mark the build unstable and continue')).to_have_count(1)
    expect(field(page, 'task_c.policy')).to_have_value('abort')
    expect(field(page, 'task_c.task_c_mode').locator('option')).to_have_text(['plan', 'apply'])
    expect(field(page, 'task_c.task_c_mode')).to_have_value('plan')
    expect(field(page, 'task_c.task_c_verbose')).to_have_value('false')
    expect(field(page, 'task_c.task_c_label')).to_have_attribute('type', 'text')
    expect(page.locator('input[type=hidden][value^="@@task_a."]')).to_have_count(0)


def test_integer_parameter_uses_number_input_and_required_parameter_is_marked_in_text(parameter_page):
    _, page = parameter_page

    tick(page, 'ecp_choice-pre-tasks', 'task_a')

    expect(field(page, 'task_a.task_a_repeat')).to_have_attribute('type', 'number')
    expect(field(page, 'task_a.task_a_repeat')).to_have_value('1')
    expect(field(page, 'task_a.task_a_message')).to_have_value('')
    # placeholder is the only accessible name the Active Choices sanitizer leaves on an input
    expect(page.get_by_role('textbox', name='task_a_message (required)')).to_be_visible()
    expect(page.get_by_role('spinbutton', name='task_a_repeat')).to_have_value('1')
    expect(page.locator('tr', has=field(page, 'task_a.task_a_message')).locator('td').first).to_have_text('task_a_message (required)')


def test_changing_the_selection_regenerates_the_form_and_drops_typed_values(parameter_page):
    _, page = parameter_page
    tick(page, 'ecp_choice-pre-tasks', 'task_a')
    field(page, 'task_a.task_a_message').fill('typed first')

    tick(page, 'ecp_choice-pre-tasks', 'task_c')

    expect(field(page, 'task_c.task_c_mode')).to_be_visible()
    expect(field(page, 'task_a.task_a_message')).to_have_value('')


def test_submitted_form_values_reach_ansible_unchanged(jenkins, parameter_page):
    job, page = parameter_page
    tick(page, 'ecp_choice-target-groups', 'api')
    tick(page, 'ecp_choice-pre-tasks', 'task_a')
    tick(page, 'ecp_choice-pre-tasks', 'task_c')
    # changing the selection regenerates the form: fill in only after every selected task is shown
    expect(field(page, 'task_c.task_c_mode')).to_be_visible()
    expect(field(page, 'task_a.task_a_message')).to_be_visible()
    field(page, 'task_a.task_a_message').fill(SPECIAL_TEXT)
    field(page, 'task_a.task_a_repeat').fill('2')
    field(page, 'task_a.policy').select_option('warn')
    field(page, 'task_c.task_c_mode').select_option('apply')
    field(page, 'task_c.task_c_verbose').select_option('true')
    number = jenkins.next_build_number(job)

    page.get_by_role('button', name='Build', exact=True).click()

    assert jenkins.wait_for_build(job, number) == 'SUCCESS'
    console = jenkins.console(job, number)
    assert '    - task_a [warn] [task_a_message:a,"b" c\\d, @@x caf\u00e9=1+2, task_a_repeat:2, task_a_delay_seconds:0]' in console
    assert '"msg": "api-01: a,\\"b\\" c\\\\d, @@x caf\u00e9=1+2 (2/2)"' in console
    assert '"msg": "api-01: task_c mode=apply label="' in console
    assert '"msg": "api-01: app=api version=1.8.0"' in console      # task_c_verbose arrived as a boolean
