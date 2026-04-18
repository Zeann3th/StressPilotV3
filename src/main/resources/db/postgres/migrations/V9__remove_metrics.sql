DROP TABLE IF EXISTS metric_values;
DROP TABLE IF EXISTS metric_scrape_events;
DROP TABLE IF EXISTS metric_defs;

ALTER TABLE runs DROP COLUMN IF EXISTS metrics_endpoint;
