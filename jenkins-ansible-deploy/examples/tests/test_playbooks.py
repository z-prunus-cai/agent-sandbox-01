import pytest

from conftest import recap, run_ansible


def run_task(tmp_path, role, params, limit=None, vault=True):
    """Run run-task.yml the way the Jenkins engine does."""
    hosts = 'localhost' if role == 'task_b' else 'deploy_targets'
    command = ['ansible-playbook', 'run-task.yml'] + (['--limit', limit] if limit else [])
    return run_ansible(command, extra_vars={'task_role': role, 'task_hosts': hosts, **params}, tmp_path=tmp_path, vault=vault)


def test_deploy_runs_only_on_the_limited_groups_with_each_hosts_own_variables():
    result = run_ansible(['ansible-playbook', 'deploy.yml', '--limit', 'web:api'])

    assert result.returncode == 0, result.stdout
    assert set(recap(result.stdout)) == {'web-01', 'web-02', 'api-01'}
    assert 'deploy web 2.3.1 to web-02 (192.0.2.12) dir=/opt/apps/web port=8080 max_connections=200 user=app' in result.stdout
    assert 'deploy api 1.8.0 to api-01 (192.0.2.13) dir=/opt/apps/api port=9000 max_connections=300 user=app' in result.stdout


def test_task_on_targets_runs_only_on_the_limited_group(tmp_path):
    result = run_task(tmp_path, 'task_a', {'task_a_message': 'hello', 'task_a_repeat': 2}, limit='api')

    assert result.returncode == 0, result.stdout
    assert set(recap(result.stdout)) == {'api-01'}
    assert '"msg": "api-01: hello (2/2)"' in result.stdout


def test_controller_task_runs_on_localhost_without_limit(tmp_path):
    result = run_task(tmp_path, 'task_b', {})

    assert result.returncode == 0, result.stdout
    assert set(recap(result.stdout)) == {'localhost'}
    assert 'inventory/hosts.yml: ' in result.stdout


def test_every_run_needs_the_vault_password_even_on_localhost(tmp_path):
    result = run_task(tmp_path, 'task_b', {}, vault=False)

    assert result.returncode != 0
    assert 'no vault secrets' in result.stdout + result.stderr
    assert 'PLAY RECAP' not in result.stdout


@pytest.mark.parametrize('role, params, limit, message, skipped_task', [
    ('task_a', {}, 'web', 'missing required arguments: task_a_message', 'task_a : Check task_a_repeat range'),
    ('task_a', {'task_a_message': 'x', 'task_a_repeat': 'two'}, 'web',
     "argument 'task_a_repeat' is of type str and we were unable to convert to int", 'task_a : Check task_a_repeat range'),
    ('task_a', {'task_a_message': 'x', 'task_a_repeat': 9}, 'web', 'task_a_repeat must be between 1 and 5, got 9',
     'task_a : Print the message'),
    ('task_b', {'task_b_path': 'no-such-file.yml'}, None, 'no-such-file.yml is missing or smaller than 1 bytes', None),
    ('task_c', {'task_c_mode': 'destroy'}, 'web', 'value of task_c_mode must be one of: plan, apply, got: destroy',
     'task_c : Run in the selected mode'),
    ('check_a', {}, 'web', 'missing required arguments: check_a_min_connections', 'check_a : Compare'),
])
def test_task_fails_with_a_clear_message_on_invalid_parameters(tmp_path, role, params, limit, message, skipped_task):
    result = run_task(tmp_path, role, params, limit=limit)

    assert result.returncode == 2
    assert message in result.stdout
    if skipped_task:
        assert f'TASK [{skipped_task}' not in result.stdout


def test_check_compares_each_hosts_own_inventory_value(tmp_path):
    result = run_task(tmp_path, 'check_a', {'check_a_min_connections': 150}, limit='web')

    assert result.returncode == 2
    assert {host: counters['failed'] for host, counters in recap(result.stdout).items()} == {'web-01': 1, 'web-02': 0}
    assert 'web-01: app_max_connections=100 < 150' in result.stdout


def test_task_without_parameters_runs(tmp_path):
    result = run_task(tmp_path, 'check_c', {}, limit='worker')

    assert result.returncode == 0, result.stdout
    assert '"msg": "worker-02: check_c passed"' in result.stdout


def test_role_defaults_apply_when_jenkins_passes_no_value(tmp_path):
    result = run_task(tmp_path, 'task_c', {}, limit='api')

    assert result.returncode == 0, result.stdout
    assert '"msg": "api-01: task_c mode=plan label="' in result.stdout
    assert 'app=api' not in result.stdout  # task_c_verbose defaults to false
