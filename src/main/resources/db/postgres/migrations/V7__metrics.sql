CREATE TABLE metric_defs (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    unit VARCHAR(50),
    description TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE metric_scrape_events (
    id BIGSERIAL PRIMARY KEY,
    run_id VARCHAR(20) NOT NULL REFERENCES runs(id),
    host VARCHAR(255) NOT NULL,
    collected_at TIMESTAMP NOT NULL,
    source VARCHAR(10),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_metric_scrape_events_run_id ON metric_scrape_events(run_id);
CREATE INDEX idx_metric_scrape_events_collected_at ON metric_scrape_events(collected_at);

CREATE TABLE metric_values (
    id BIGSERIAL PRIMARY KEY,
    event_id BIGINT NOT NULL REFERENCES metric_scrape_events(id),
    def_id BIGINT NOT NULL REFERENCES metric_defs(id),
    value DOUBLE PRECISION NOT NULL,
    labels TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_metric_values_event_id ON metric_values(event_id);
CREATE INDEX idx_metric_values_def_id ON metric_values(def_id);
