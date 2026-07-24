-- V1: Initial schema.
-- Flyway creates and owns the "app" schema (spring.flyway.schemas/default-schema
-- + create-schemas). NOTE: on SQL Server, Flyway cannot switch the session's
-- default schema, so migration objects MUST be schema-qualified explicitly.
-- "GO" is the SQL Server batch separator understood by the flyway-sqlserver module.

CREATE TABLE app.customer (
    id            BIGINT        IDENTITY(1,1) NOT NULL,
    external_ref  NVARCHAR(64)  NOT NULL,
    display_name  NVARCHAR(200) NOT NULL,
    status        NVARCHAR(20)  NOT NULL CONSTRAINT DF_customer_status  DEFAULT (N'ACTIVE'),
    created_at    DATETIME2(3)  NOT NULL CONSTRAINT DF_customer_created DEFAULT (SYSUTCDATETIME()),
    CONSTRAINT PK_customer              PRIMARY KEY (id),
    CONSTRAINT UQ_customer_external_ref UNIQUE (external_ref),
    CONSTRAINT CK_customer_status       CHECK (status IN (N'ACTIVE', N'INACTIVE'))
);
GO

CREATE INDEX IX_customer_status ON app.customer (status);
GO
