import ipaddress
import json

import yaml

from conftest import ANSIBLE_DIR, recap, run_ansible


def host_vars(host):
    result = run_ansible(['ansible-inventory', '--host', host])
    assert result.returncode == 0, result.stderr
    return json.loads(result.stdout)


def test_deploy_targets_graph_lists_every_group_with_its_hosts_without_vault_password():
    result = run_ansible(['ansible-inventory', '--graph', 'deploy_targets'], vault=False)

    assert result.returncode == 0, result.stderr
    assert result.stdout.splitlines() == ['@deploy_targets:',
                                          '  |--@web:', '  |  |--web-01', '  |  |--web-02',
                                          '  |--@api:', '  |  |--api-01',
                                          '  |--@worker:', '  |  |--worker-01', '  |  |--worker-02']


def test_listing_variables_without_vault_password_fails():
    result = run_ansible(['ansible-inventory', '--list'], vault=False)

    assert result.returncode != 0
    assert 'no vault secrets' in result.stderr
    assert result.stdout == ''


def test_host_vars_override_group_vars_for_that_host_only():
    assert host_vars('web-02')['app_max_connections'] == 200
    assert host_vars('web-01')['app_max_connections'] == 100


def test_secret_is_stored_in_vault_file_and_referenced_by_name():
    vault_file = ANSIBLE_DIR / 'inventory' / 'group_vars' / 'all' / 'vault.yml'
    variables = host_vars('api-01')

    assert vault_file.read_text().startswith('$ANSIBLE_VAULT;1.1;AES256')
    assert variables['vault_app_api_token'] == 'example-only-token'
    assert variables['app_api_token'] == '{{ vault_app_api_token }}'


def test_deploy_target_groups_in_hosts_yml_match_ansible_inventory():
    # the engine reads hosts.yml with readYaml instead of calling ansible-inventory
    hosts_yml = yaml.safe_load((ANSIBLE_DIR / 'inventory' / 'hosts.yml').read_text())
    from_file = list(hosts_yml['all']['children']['deploy_targets']['children'])
    result = run_ansible(['ansible-inventory', '--list'])

    assert result.returncode == 0, result.stderr
    assert from_file == json.loads(result.stdout)['deploy_targets']['children']


def test_every_host_address_is_a_documentation_address():
    # RFC 5737: 192.0.2.0/24, 198.51.100.0/24, 203.0.113.0/24 are reserved for examples
    networks = [ipaddress.ip_network(network) for network in ('192.0.2.0/24', '198.51.100.0/24', '203.0.113.0/24')]
    result = run_ansible(['ansible-inventory', '--list'])
    assert result.returncode == 0, result.stderr
    host_vars = json.loads(result.stdout)['_meta']['hostvars']

    assert len(host_vars) == 5
    for host, variables in host_vars.items():
        assert any(ipaddress.ip_address(variables['ansible_host']) in network for network in networks), host


def test_check_inventory_passes_for_every_deploy_target():
    result = run_ansible(['ansible-playbook', 'check-inventory.yml'])

    assert result.returncode == 0, result.stdout
    assert {host: counters['failed'] for host, counters in recap(result.stdout).items()} == {
        'web-01': 0, 'web-02': 0, 'api-01': 0, 'worker-01': 0, 'worker-02': 0}


def test_check_inventory_reports_every_broken_host_in_one_run(ansible_copy):
    group_vars = ansible_copy / 'inventory' / 'group_vars'
    (group_vars / 'web.yml').write_text((group_vars / 'web.yml').read_text().replace('app_port: 8080', 'app_port: eighty'))
    (group_vars / 'api.yml').write_text((group_vars / 'api.yml').read_text().replace('app_version: "1.8.0"\n', ''))

    result = run_ansible(['ansible-playbook', 'check-inventory.yml'], project=ansible_copy)

    assert result.returncode == 2
    assert "argument 'app_port' is of type str and we were unable to convert to int" in result.stdout
    assert 'missing required arguments: app_version' in result.stdout
    assert {host: counters['failed'] for host, counters in recap(result.stdout).items()} == {
        'web-01': 1, 'web-02': 1, 'api-01': 1, 'worker-01': 0, 'worker-02': 0}


def test_ansible_lint_passes_with_production_profile():
    result = run_ansible(['ansible-lint', '--offline', '--profile', 'production'])

    assert result.returncode == 0, result.stdout + result.stderr
