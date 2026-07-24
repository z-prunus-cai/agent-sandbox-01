-- ENVIRONMENT-SPECIFIC master data for: prod
-- Loaded only when app.env=prod. Repeatable + idempotent MERGE.
-- Prod carries only real configuration — no demo/test data.
MERGE INTO app.app_config AS tgt
USING (VALUES
    (N'env.name',                 N'prod'),
    (N'feature.beta-banner',      N'false'),
    (N'notification.webhook-url', N'https://hooks.example.com/db-events')
) AS src(config_key, config_value)
ON (tgt.config_key = src.config_key)
WHEN MATCHED AND tgt.config_value <> src.config_value
    THEN UPDATE SET tgt.config_value = src.config_value
WHEN NOT MATCHED BY TARGET
    THEN INSERT (config_key, config_value) VALUES (src.config_key, src.config_value);
GO
