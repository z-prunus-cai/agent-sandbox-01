"""The platform playbooks: deploy.yml, check-variable-names.yml and what every run of run-task.yml needs.
The cases of each task are in business/tasks/<task>/tests/cases.yml (tests/test_task_cases.py)."""
from conftest import TASK_NAMES, recap, run_ansible, run_location_hosts, run_task, task_metadata


def test_deploy_runs_only_on_the_limited_groups_with_each_hosts_own_variables():
    result = run_ansible(['ansible-playbook', 'deploy.yml', '--limit', 'web:api'])

    assert result.returncode == 0, result.stdout
    assert set(recap(result.stdout)) == {'web-01', 'web-02', 'api-01'}
    assert 'TASK [deploy : Show the deployment that would run]' in result.stdout
    assert 'deploy web 2.3.1 to web-02 (192.0.2.12) dir=/opt/apps/web port=8080 max_connections=200 user=app' in result.stdout
    assert 'deploy api 1.8.0 to api-01 (192.0.2.13) dir=/opt/apps/api port=9000 max_connections=300 user=app' in result.stdout


def test_deploy_validates_host_variables_before_doing_anything(examples_copy):
    web = examples_copy / 'business' / 'inventory' / 'group_vars' / 'web.yml'
    web.write_text(web.read_text().replace('app_port: 8080', 'app_port: eighty'))

    result = run_ansible(['ansible-playbook', 'deploy.yml', '--limit', 'web'], root=examples_copy)

    assert result.returncode == 2
    assert "argument 'app_port' is of type str and we were unable to convert to int" in result.stdout
    assert 'TASK [deploy : Show the deployment that would run]' not in result.stdout


def test_every_run_needs_the_vault_password_even_on_localhost(tmp_path):
    # any task the engine runs on the Jenkins node will do: the vault comes with the inventory, not the task
    task = next(task for task in TASK_NAMES if task_metadata(task)['run_on'] == 'controller')

    result = run_task(tmp_path, task, {}, vault=False)

    assert result.returncode != 0
    assert 'no vault secrets' in result.stdout + result.stderr
    assert 'PLAY RECAP' not in result.stdout


# Stays here, not in task_b's cases.yml: it needs a symbolic link in a copy of the examples
def test_controller_task_rejects_a_symbolic_link_that_leaves_the_template_directory(tmp_path, examples_copy):
    outside = tmp_path / 'outside'
    outside.mkdir()
    (outside / 'secret.txt').write_text('outside the template directory')
    (examples_copy / 'link').symlink_to(outside)

    result = run_ansible(['ansible-playbook', 'run-task.yml'], root=examples_copy, tmp_path=tmp_path,
                         extra_vars={'task_role': 'task_b', 'task_hosts': run_location_hosts()['controller'],
                                     'task_b_path': 'link/secret.txt'})

    assert result.returncode == 2
    assert "task_b_path must be a non-empty path relative to the template directory" in result.stdout
    assert 'TASK [task_b : Read file status]' not in result.stdout


def test_variable_name_check_passes_for_names_the_inventory_does_not_use(tmp_path):
    result = run_ansible(['ansible-playbook', 'check-variable-names.yml'], tmp_path=tmp_path,
                         extra_vars={'task_parameter_names': ['task_a_message', 'task_b_path']})

    assert result.returncode == 0, result.stdout
    assert recap(result.stdout)['localhost']['failed'] == 0


def test_variable_name_check_reports_names_the_inventory_already_uses(tmp_path):
    result = run_ansible(['ansible-playbook', 'check-variable-names.yml'], tmp_path=tmp_path,
                         extra_vars={'task_parameter_names': ['task_a_message', 'app_port', 'vault_app_api_token']})

    assert result.returncode == 2
    assert "task parameters also defined in the inventory: ['app_port', 'vault_app_api_token']" in result.stdout
