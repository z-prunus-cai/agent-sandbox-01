-- Repeatable migration (R__): re-applied automatically whenever its checksum
-- changes. Ideal for views, stored procedures and functions. On SQL Server,
-- CREATE VIEW must be the only statement in its batch, so we use CREATE OR ALTER.
-- Objects are schema-qualified (SQL Server ignores Flyway's session default schema).

CREATE OR ALTER VIEW app.vw_active_customer AS
SELECT
    id,
    external_ref,
    display_name,
    created_at
FROM app.customer
WHERE status = N'ACTIVE';
GO
