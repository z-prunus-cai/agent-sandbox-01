import pytest

from conftest import CATALOG, recap, run_ansible


def run_task(tmp_path, role, params, limit=None, vault=True):
    """Run run-task.yml the way the Jenkins engine does."""
    hosts = 'deploy_targets' if CATALOG[role]['run_on'] == 'targets' else 'localhost'
    command = ['ansible-playbook', 'run-task.yml'] + (['--limit', limit] if limit else [])
    return run_ansible(command, extra_vars={'task_role': role, 'task_hosts': hosts, **params}, tmp_path=tmp_path, vault=vault)


def test_deploy_runs_only_on_the_limited_groups_with_each_hosts_own_variables():
    result = run_ansible(['ansible-playbook', 'deploy.yml', '--limit', 'web:api'])

    assert result.returncode == 0, result.stdout
    assert set(recap(result.stdout)) == {'web-01', 'web-02', 'api-01'}
    assert 'TASK [app_deploy : Show the deployment that would run]' in result.stdout
    assert 'deploy web 2.3.1 to web-02 (192.0.2.12) dir=/opt/apps/web port=8080 max_connections=200 user=app' in result.stdout
    assert 'deploy api 1.8.0 to api-01 (192.0.2.13) dir=/opt/apps/api port=9000 max_connections=300 user=app' in result.stdout


def test_deploy_validates_host_variables_before_doing_anything(ansible_copy):
    web = ansible_copy / 'inventory' / 'group_vars' / 'web.yml'
    web.write_text(web.read_text().replace('app_port: 8080', 'app_port: eighty'))

    result = run_ansible(['ansible-playbook', 'deploy.yml', '--limit', 'web'], project=ansible_copy)

    assert result.returncode == 2
    assert "argument 'app_port' is of type str and we were unable to convert to int" in result.stdout
    assert 'TASK [app_deploy : Show the deployment that would run]' not in result.stdout


def test_task_on_targets_runs_only_on_the_limited_group(tmp_path):
    result = run_task(tmp_path, 'task_a', {'task_a_message': 'hello', 'task_a_repeat': 2}, limit='api')

    assert result.returncode == 0, result.stdout
    assert set(recap(result.stdout)) == {'api-01'}
    assert '"msg": "api-01: hello (2/2)"' in result.stdout
    # the task names that the failure cases below expect to be skipped
    assert all(f'TASK [task_a : {name}]' in result.stdout for name in ('Check task_a_repeat range', 'Print the message'))


def test_controller_task_runs_on_localhost_without_limit(tmp_path):
    result = run_task(tmp_path, 'task_b', {})

    assert result.returncode == 0, result.stdout
    assert set(recap(result.stdout)) == {'localhost'}
    assert 'inventory/hosts.yml: ' in result.stdout
    assert all(f'TASK [task_b : {name}]' in result.stdout for name in ('Check the path stays inside the Ansible directory', 'Read file status'))


def test_every_run_needs_the_vault_password_even_on_localhost(tmp_path):
    result = run_task(tmp_path, 'task_b', {}, vault=False)

    assert result.returncode != 0
    assert 'no vault secrets' in result.stdout + result.stderr
    assert 'PLAY RECAP' not in result.stdout


@pytest.mark.parametrize('role, params, limit, message, skipped_task', [
    pytest.param('task_a', {}, 'web', 'missing required arguments: task_a_message',
                 'task_a : Check task_a_repeat range', id='missing-required'),
    pytest.param('task_a', {'task_a_message': 'x', 'task_a_repeat': 'two'}, 'web',
                 "argument 'task_a_repeat' is of type str and we were unable to convert to int",
                 'task_a : Check task_a_repeat range', id='wrong-type'),
    pytest.param('task_a', {'task_a_message': 'x', 'task_a_repeat': 9}, 'web',
                 'task_a_repeat must be between 1 and 5, got 9', 'task_a : Print the message', id='out-of-range'),
    pytest.param('task_b', {'task_b_path': ''}, None,
                 "task_b_path must be a non-empty path relative to the Ansible directory", 'task_b : Read file status',
                 id='empty-path'),
    pytest.param('task_b', {'task_b_path': '../../etc/hostname'}, None,
                 "task_b_path must be a non-empty path relative to the Ansible directory without '..'", 'task_b : Read file status',
                 id='path-outside-directory'),
    pytest.param('task_c', {'task_c_mode': 'destroy'}, 'web', 'value of task_c_mode must be one of: plan, apply, got: destroy',
                 'task_c : Run in the selected mode', id='not-in-choices'),
    pytest.param('check_a', {}, 'web', 'missing required arguments: check_a_min_connections',
                 'check_a : Compare', id='check-missing-required'),
])
def test_task_fails_with_a_clear_message_and_stops(tmp_path, role, params, limit, message, skipped_task):
    result = run_task(tmp_path, role, params, limit=limit)

    assert result.returncode == 2
    assert message in result.stdout
    assert f'TASK [{skipped_task}' not in result.stdout


def test_controller_task_fails_when_the_file_is_missing(tmp_path):
    result = run_task(tmp_path, 'task_b', {'task_b_path': 'no-such-file.yml'})

    assert result.returncode == 2
    assert 'no-such-file.yml is missing, not a regular file, or smaller than 1 bytes' in result.stdout
    assert recap(result.stdout)['localhost']['failed'] == 1


def test_check_compares_each_hosts_own_inventory_value(tmp_path):
    result = run_task(tmp_path, 'check_a', {'check_a_min_connections': 150}, limit='web')

    assert result.returncode == 2
    assert {host: counters['failed'] for host, counters in recap(result.stdout).items()} == {'web-01': 1, 'web-02': 0}
    assert 'web-01: app_max_connections=100 < 150' in result.stdout


def test_task_without_parameters_runs(tmp_path):
    result = run_task(tmp_path, 'check_c', {}, limit='worker')

    assert result.returncode == 0, result.stdout
    assert set(recap(result.stdout)) == {'worker-01', 'worker-02'}
    assert '"msg": "worker-02: check_c passed"' in result.stdout


def test_role_defaults_apply_when_jenkins_passes_no_value(tmp_path):
    result = run_task(tmp_path, 'task_c', {}, limit='api')

    assert result.returncode == 0, result.stdout
    assert '"msg": "api-01: task_c mode=plan label="' in result.stdout
    assert 'TASK [task_c : Run in the selected mode]' in result.stdout
    assert 'app=api' not in result.stdout  # task_c_verbose defaults to false


def test_check_reads_each_hosts_value_and_reports_its_task(tmp_path):
    result = run_task(tmp_path, 'check_a', {'check_a_min_connections': 1}, limit='api')

    assert result.returncode == 0, result.stdout
    assert 'TASK [check_a : Compare with the host' in result.stdout


def test_bool_parameter_switches_the_verbose_output_on(tmp_path):
    result = run_task(tmp_path, 'task_c', {'task_c_verbose': True}, limit='api')

    assert result.returncode == 0, result.stdout
    assert '"msg": "api-01: app=api version=1.8.0"' in result.stdout
