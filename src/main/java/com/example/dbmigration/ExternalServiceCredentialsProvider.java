package com.example.dbmigration;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * EXAMPLE {@link DbCredentialsProvider} — a template for fetching connection
 * info from an external secrets service instead of static config.
 *
 * <p>It is inert unless you opt in with {@code db.credentials.source=external-service}.
 * As shipped it reads a small {@code .properties} bundle (keys {@code url},
 * {@code username}, {@code password}) from the path given by
 * {@code db.credentials.secrets-file}, standing in for a real service call.
 *
 * <p>To use a real backend, replace the body of {@link #get()} with your client:
 * <pre>{@code
 *   var secret = vaultClient.read("secret/data/appdb");        // HashiCorp Vault
 *   var secret = secretsManager.getSecretValue(request);       // AWS Secrets Manager
 *   var secret = secretClient.getSecret("appdb-conn");         // Azure Key Vault
 * }</pre>
 * Keep it throwing on failure so the migration job fails fast rather than
 * running against the wrong database.
 */
@Component
@ConditionalOnProperty(name = "db.credentials.source", havingValue = "external-service")
public class ExternalServiceCredentialsProvider implements DbCredentialsProvider {

    private static final Logger log = LoggerFactory.getLogger(ExternalServiceCredentialsProvider.class);

    private final String secretsFile;

    public ExternalServiceCredentialsProvider(
            @org.springframework.beans.factory.annotation.Value("${db.credentials.secrets-file:}") String secretsFile) {
        this.secretsFile = secretsFile;
    }

    @Override
    public DbCredentials get() {
        // --- Replace this block with a call to your real secrets service. ---
        if (secretsFile == null || secretsFile.isBlank()) {
            throw new IllegalStateException(
                    "db.credentials.source=external-service requires db.credentials.secrets-file "
                            + "(or replace ExternalServiceCredentialsProvider.get() with your secrets client)");
        }
        Path path = Path.of(secretsFile);
        Properties secret = new Properties();
        try (InputStream in = Files.newInputStream(path)) {
            secret.load(in);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read database secret bundle from " + path, e);
        }
        log.info("Fetched database credentials from external secret bundle {}", path);
        return new DbCredentials(
                secret.getProperty("url"),
                secret.getProperty("username"),
                secret.getProperty("password"));
        // --------------------------------------------------------------------
    }
}
