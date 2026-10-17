"""Integration tests in a real browser: the Active Choices parameter page of examples/Jenkinsfile."""
import os

import pytest
from playwright.sync_api import expect, sync_playwright

pytestmark = pytest.mark.jenkins

SPECIAL_TEXT = 'a,"b" c\\d, @@x 中文=1+2'


# Active Choices regenerates the form through a server round trip; the default 5 s can be short on a cold Jenkins
expect.set_options(timeout=15_000)


@pytest.fixture
def page():
    with sync_playwright() as playwright:
        # CHROMIUM_PATH: use an already installed Chromium instead of the one `playwright install` downloads
        browser = playwright.chromium.launch(executable_path=os.environ.get('CHROMIUM_PATH'))
        context = browser.new_context()
        yield context.new_page()
        context.close()
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

    expect(page.locator('#ecp_choice-target-groups label')).to_have_text(
        ['web (web-01, web-02)', 'api (api-01)', 'worker (worker-01, worker-02)'])
    expect(page.locator('#ecp_choice-pre-tasks label')).to_have_text([
        'task_a — 在所选主机组上运行的示例任务', 'task_b — 在 Jenkins 节点本机检查文件的示例任务',
        'task_c — 带枚举参数和布尔参数的示例任务', 'check_c — 在 task_c 之后运行的示例检查'])
    expect(page.locator('#ecp_choice-post-tasks label')).to_have_text([
        'task_c — 带枚举参数和布尔参数的示例任务', 'check_c — 在 task_c 之后运行的示例检查',
        'check_a — 部署后比较主机变量的示例检查'])


def test_selecting_a_task_shows_only_its_fields_prefilled_with_role_defaults(parameter_page):
    _, page = parameter_page

    tick(page, 'ecp_choice-pre-tasks', 'task_c')

    expect(field(page, 'task_c.policy').locator('option')).to_have_text(['abort', 'warn'])
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
    expect(page.get_by_role('textbox', name='task_a_message（必填）')).to_be_visible()
    expect(page.get_by_role('spinbutton', name='task_a_repeat')).to_have_value('1')
    expect(page.locator('tr', has=field(page, 'task_a.task_a_message')).locator('td').first).to_have_text('task_a_message（必填）')


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
    assert '    - task_a [warn] [task_a_message:a,"b" c\\d, @@x 中文=1+2, task_a_repeat:2, task_a_delay_seconds:0]' in console
    assert '"msg": "api-01: a,\\"b\\" c\\\\d, @@x 中文=1+2 (2/2)"' in console
    assert '"msg": "api-01: task_c mode=apply label="' in console
    assert '"msg": "api-01: app=api version=1.8.0"' in console      # task_c_verbose arrived as a boolean
