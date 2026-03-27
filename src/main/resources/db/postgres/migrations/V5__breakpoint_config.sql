INSERT INTO configs (config_key, config_value, created_at, updated_at)
VALUES ('BREAKPOINT_ERROR_THRESHOLD', '0.5', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (config_key) DO NOTHING;

SELECT setval(pg_get_serial_sequence('configs', 'id'), (SELECT MAX(id) FROM configs));