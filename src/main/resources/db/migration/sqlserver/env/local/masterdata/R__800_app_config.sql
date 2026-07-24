-- ENVIRONMENT-SPECIFIC master data for: local
-- Loaded only when app.env=local. Repeatable + idempotent MERGE.
MERGE INTO app.app_config AS tgt
USING (VALUES
    (N'env.name',                 N'local'),
    (N'feature.beta-banner',      N'true'),
    (N'notification.webhook-url', N'https://hooks.local.test/db-events')
) AS src(config_key, config_value)
ON (tgt.config_key = src.config_key)
WHEN MATCHED AND tgt.config_value <> src.config_value
    THEN UPDATE SET tgt.config_value = src.config_value
WHEN NOT MATCHED BY TARGET
    THEN INSERT (config_key, config_value) VALUES (src.config_key, src.config_value);
GO
