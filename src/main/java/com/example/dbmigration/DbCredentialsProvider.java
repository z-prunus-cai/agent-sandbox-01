package com.example.dbmigration;

/**
 * Extension point for sourcing database connection info from somewhere other
 * than static YAML / environment variables — e.g. HashiCorp Vault, AWS/Azure
 * secrets managers, a config server, or any in-house credential service.
 *
 * <p>By default this framework has <strong>no</strong> {@code DbCredentialsProvider}
 * bean, so connection details come from {@code spring.datasource.*} (the profile
 * files / env vars). To take over, publish a single bean implementing this
 * interface; {@link ConnectionDetailsConfig} adapts it into the connection
 * details Spring Boot uses to build both the {@code DataSource} and Flyway:
 *
 * <pre>{@code
 * @Component
 * class VaultCredentialsProvider implements DbCredentialsProvider {
 *     private final VaultClient vault;
 *     VaultCredentialsProvider(VaultClient vault) { this.vault = vault; }
 *     @Override public DbCredentials get() {
 *         var s = vault.read("secret/data/appdb");
 *         return new DbCredentials(s.get("url"), s.get("username"), s.get("password"));
 *     }
 * }
 * }</pre>
 *
 * <p>Resolution happens once, during startup, before migrations run. Throw from
 * {@link #get()} to fail fast (and fail the migration job) if the secret cannot
 * be fetched.
 */
@FunctionalInterface
public interface DbCredentialsProvider {

    /**
     * @return the credentials to connect with; must not be {@code null}
     */
    DbCredentials get();
}
