package deploy

/**
 * Names Ansible keeps for itself that a "<task>_" prefix can produce: keywords and special variables with an underscore.
 * Taken from ansible-core 2.21.5 (ansible.vars.reserved and the special variables).
 */
class AnsibleNames {
    public static final List<String> RESERVED = [
            'any_errors_fatal', 'async_val', 'become_exe', 'become_flags', 'become_method', 'become_user', 'changed_when',
            'check_mode', 'delegate_facts', 'delegate_to', 'fact_path', 'failed_when', 'force_handlers', 'gather_facts',
            'gather_timeout', 'group_names', 'ignore_errors', 'ignore_unreachable', 'inventory_dir', 'inventory_file',
            'inventory_hostname', 'inventory_hostname_short', 'local_action', 'loop_control', 'loop_with', 'max_fail_percentage',
            'module_defaults', 'no_log', 'play_hosts', 'playbook_dir', 'post_tasks', 'pre_tasks', 'remote_user', 'role_name',
            'role_names', 'role_path', 'run_once', 'validate_argspec', 'vars_files', 'vars_prompt', 'with_']
}
