package com.example.dbmigration;

/**
 * Immutable database connection credentials resolved at startup.
 *
 * @param url      full JDBC URL, e.g. {@code jdbc:sqlserver://host:1433;databaseName=appdb;encrypt=true}
 * @param username login name
 * @param password secret (never logged)
 */
public record DbCredentials(String url, String username, String password) {
}
