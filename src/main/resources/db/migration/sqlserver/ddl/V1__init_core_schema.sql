-- ============================================================================
-- COMMON DDL (versioned) — the schema is IDENTICAL in every environment.
-- Only master data differs per environment; structure never forks.
-- Flyway owns the "app" schema; objects are schema-qualified because SQL Server
-- does not honour Flyway's session default schema.
-- ============================================================================

-- Lookup / reference table (its rows are common reference data, seeded by R__5xx).
CREATE TABLE app.customer_status (
    code   NVARCHAR(20)  NOT NULL,
    label  NVARCHAR(100) NOT NULL,
    CONSTRAINT PK_customer_status PRIMARY KEY (code)
);
GO

CREATE TABLE app.customer (
    id            BIGINT        IDENTITY(1,1) NOT NULL,
    external_ref  NVARCHAR(64)  NOT NULL,
    display_name  NVARCHAR(200) NOT NULL,
    status        NVARCHAR(20)  NOT NULL CONSTRAINT DF_customer_status  DEFAULT (N'ACTIVE'),
    created_at    DATETIME2(3)  NOT NULL CONSTRAINT DF_customer_created DEFAULT (SYSUTCDATETIME()),
    CONSTRAINT PK_customer              PRIMARY KEY (id),
    CONSTRAINT UQ_customer_external_ref UNIQUE (external_ref),
    CONSTRAINT FK_customer_status       FOREIGN KEY (status) REFERENCES app.customer_status (code)
);
GO

CREATE INDEX IX_customer_status ON app.customer (status);
GO

-- Key/value application configuration. Rows are ENVIRONMENT-SPECIFIC master data
-- (different values per env), seeded by env/{env}/masterdata/R__8xx.
CREATE TABLE app.app_config (
    config_key    NVARCHAR(100) NOT NULL,
    config_value  NVARCHAR(400) NOT NULL,
    CONSTRAINT PK_app_config PRIMARY KEY (config_key)
);
GO
