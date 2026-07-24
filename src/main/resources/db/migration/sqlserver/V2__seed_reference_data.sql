-- V2: Seed a small set of reference rows. Objects are schema-qualified because
-- SQL Server does not honour Flyway's session default schema (see V1).

INSERT INTO app.customer (external_ref, display_name, status)
SELECT v.external_ref, v.display_name, v.status
FROM (VALUES
    (N'CUST-0001', N'Acme Corporation', N'ACTIVE'),
    (N'CUST-0002', N'Globex LLC',       N'INACTIVE'),
    (N'CUST-0003', N'Initech',          N'ACTIVE')
) AS v(external_ref, display_name, status)
WHERE NOT EXISTS (
    SELECT 1 FROM app.customer c WHERE c.external_ref = v.external_ref
);
GO
