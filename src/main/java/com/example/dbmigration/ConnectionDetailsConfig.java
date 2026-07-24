package com.example.dbmigration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Bridges a user-supplied {@link DbCredentialsProvider} into the
 * {@link JdbcConnectionDetails} that Spring Boot uses to build the DataSource
 * and Flyway.
 *
 * <p>The bean is created <em>only</em> when:
 * <ul>
 *   <li>a {@link DbCredentialsProvider} bean exists (someone opted in), and</li>
 *   <li>no {@link JdbcConnectionDetails} is already defined — so it never fights
 *       Testcontainers' {@code @ServiceConnection}, which registers its own.</li>
 * </ul>
 *
 * <p>When no provider is present, this backs off entirely and connection details
 * come from {@code spring.datasource.*} as usual.
 */
@Configuration(proxyBeanMethods = false)
public class ConnectionDetailsConfig {

    private static final Logger log = LoggerFactory.getLogger(ConnectionDetailsConfig.class);

    @Bean
    @ConditionalOnBean(DbCredentialsProvider.class)
    @ConditionalOnMissingBean(JdbcConnectionDetails.class)
    public JdbcConnectionDetails providedJdbcConnectionDetails(DbCredentialsProvider provider) {
        DbCredentials credentials = provider.get();
        if (credentials == null || credentials.url() == null) {
            throw new IllegalStateException(
                    "DbCredentialsProvider " + provider.getClass().getName() + " returned no connection URL");
        }
        log.info("Using database connection details from {} (URL host resolved at runtime)",
                provider.getClass().getSimpleName());
        return new JdbcConnectionDetails() {
            @Override
            public String getUsername() {
                return credentials.username();
            }

            @Override
            public String getPassword() {
                return credentials.password();
            }

            @Override
            public String getJdbcUrl() {
                return credentials.url();
            }
            // getDriverClassName() is derived from the URL by the default method.
        };
    }
}
