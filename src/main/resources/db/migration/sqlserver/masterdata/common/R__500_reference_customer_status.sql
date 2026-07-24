-- COMMON reference/master data (all environments), repeatable + IDEMPOTENT.
-- Flyway officially recommends repeatable migrations for "bulk reference data
-- reinserts"; because they re-run whenever the checksum changes, the statement
-- MUST be idempotent — here a MERGE that inserts missing rows and updates drift.
-- R__5xx runs after structure (R__1xx) and before env data (R__8xx).

MERGE INTO app.customer_status AS tgt
USING (VALUES
    (N'ACTIVE',    N'Active'),
    (N'INACTIVE',  N'Inactive'),
    (N'SUSPENDED', N'Suspended')
) AS src(code, label)
ON (tgt.code = src.code)
WHEN MATCHED AND tgt.label <> src.label
    THEN UPDATE SET tgt.label = src.label
WHEN NOT MATCHED BY TARGET
    THEN INSERT (code, label) VALUES (src.code, src.label);
GO
