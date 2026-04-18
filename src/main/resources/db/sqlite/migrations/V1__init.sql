CREATE TABLE environments
(
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE TABLE configs
(
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    created_at   TIMESTAMP,
    updated_at   TIMESTAMP,
    config_key   VARCHAR(255) NOT NULL,
    config_value TEXT,
    CONSTRAINT uc_configs_config_key UNIQUE (config_key)
);

CREATE TABLE projects
(
    id             INTEGER PRIMARY KEY AUTOINCREMENT,
    created_at     TIMESTAMP,
    updated_at     TIMESTAMP,
    name           VARCHAR(255) NOT NULL,
    description    TEXT,
    environment_id INTEGER      NOT NULL,
    CONSTRAINT FK_PROJECTS_ON_ENVIRONMENT FOREIGN KEY (environment_id) REFERENCES environments (id)
);

CREATE TABLE environment_variables
(
    id             INTEGER PRIMARY KEY AUTOINCREMENT,
    created_at     TIMESTAMP,
    updated_at     TIMESTAMP,
    environment_id INTEGER      NOT NULL,
    "key"          VARCHAR(255) NOT NULL,
    "value"        TEXT,
    is_active      BOOLEAN      NOT NULL,
    CONSTRAINT uc_2e64ec9f3bcb663bf5dad176e UNIQUE (environment_id, "key"),
    CONSTRAINT FK_ENVIRONMENT_VARIABLES_ON_ENVIRONMENT FOREIGN KEY (environment_id) REFERENCES environments (id) ON DELETE CASCADE
);

CREATE TABLE flows
(
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    created_at  TIMESTAMP,
    updated_at  TIMESTAMP,
    project_id  INTEGER      NOT NULL,
    name        VARCHAR(255) NOT NULL,
    description TEXT,
    type        VARCHAR(50)  NOT NULL DEFAULT 'DEFAULT',
    CONSTRAINT FK_FLOWS_ON_PROJECT FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE
);

CREATE TABLE endpoints
(
    id                INTEGER PRIMARY KEY AUTOINCREMENT,
    created_at        TIMESTAMP,
    updated_at        TIMESTAMP,
    name              VARCHAR(255) NOT NULL,
    description       TEXT,
    type              VARCHAR(20)  NOT NULL,
    project_id        INTEGER      NOT NULL,
    url               VARCHAR(255),
    body              TEXT,
    success_condition TEXT,
    http_method       VARCHAR(10),
    http_headers      TEXT,
    http_parameters   TEXT,
    grpc_service_name VARCHAR(255),
    grpc_method_name  VARCHAR(255),
    grpc_stub_path    TEXT,
    CONSTRAINT FK_ENDPOINTS_ON_PROJECT FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE
);

CREATE TABLE runs
(
    id               VARCHAR(20) PRIMARY KEY NOT NULL,
    flow_id          INTEGER                 NOT NULL,
    status           VARCHAR(10)             NOT NULL,
    threads          INTEGER                 NOT NULL,
    duration         INTEGER                 NOT NULL,
    ramp_up_duration INTEGER                 NOT NULL,
    metrics_endpoint VARCHAR(255),
    started_at       TIMESTAMP               NOT NULL,
    completed_at     TIMESTAMP,
    CONSTRAINT FK_RUNS_ON_FLOW FOREIGN KEY (flow_id) REFERENCES flows (id) ON DELETE CASCADE
);

CREATE TABLE flow_steps
(
    id             VARCHAR(36)  NOT NULL,
    created_at     TIMESTAMP,
    updated_at     TIMESTAMP,
    flow_id        INTEGER      NOT NULL,
    type           VARCHAR(10)  NOT NULL,
    endpoint_id    INTEGER,
    pre_processor  TEXT,
    post_processor TEXT,
    next_if_true   VARCHAR(255),
    next_if_false  VARCHAR(255),
    condition      TEXT,
    CONSTRAINT pk_flow_steps PRIMARY KEY (id),
    CONSTRAINT FK_FLOW_STEPS_ON_ENDPOINT FOREIGN KEY (endpoint_id) REFERENCES endpoints (id) ON DELETE CASCADE,
    CONSTRAINT FK_FLOW_STEPS_ON_FLOW FOREIGN KEY (flow_id) REFERENCES flows (id) ON DELETE CASCADE
);

CREATE TABLE request_logs
(
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    run_id        VARCHAR(20) NOT NULL,
    endpoint_id   INTEGER     NOT NULL,
    status_code   INTEGER   NOT NULL,
    is_success    BOOLEAN   NOT NULL,
    response_time INTEGER   NOT NULL,
    request       TEXT,
    response      TEXT,
    created_at    TIMESTAMP NOT NULL,
    CONSTRAINT FK_REQUEST_LOGS_ON_ENDPOINT FOREIGN KEY (endpoint_id) REFERENCES endpoints (id) ON DELETE CASCADE,
    CONSTRAINT FK_REQUEST_LOGS_ON_RUN FOREIGN KEY (run_id) REFERENCES runs (id) ON DELETE CASCADE
);

CREATE TABLE functions
(
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    created_at  TIMESTAMP,
    updated_at  TIMESTAMP,
    name        VARCHAR(100) NOT NULL,
    body        TEXT         NOT NULL,
    description VARCHAR(500),
    is_active   BOOLEAN      NOT NULL,
    CONSTRAINT uc_functions_name UNIQUE (name)
);

CREATE TABLE metric_defs (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name VARCHAR(255) NOT NULL UNIQUE,
    unit VARCHAR(50),
    description TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE metric_scrape_events (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    run_id VARCHAR(20) NOT NULL,
    host VARCHAR(255) NOT NULL,
    collected_at TIMESTAMP NOT NULL,
    source VARCHAR(10),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (run_id) REFERENCES runs(id)
);

CREATE INDEX idx_metric_scrape_events_run_id ON metric_scrape_events(run_id);
CREATE INDEX idx_metric_scrape_events_collected_at ON metric_scrape_events(collected_at);

CREATE TABLE metric_values (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    event_id INTEGER NOT NULL,
    def_id INTEGER NOT NULL,
    value DOUBLE NOT NULL,
    labels TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (event_id) REFERENCES metric_scrape_events(id),
    FOREIGN KEY (def_id) REFERENCES metric_defs(id)
);

CREATE INDEX idx_metric_values_event_id ON metric_values(event_id);
CREATE INDEX idx_metric_values_def_id ON metric_values(def_id);

INSERT INTO configs (id, config_key, config_value)
VALUES (1, 'HTTP_CONNECT_TIMEOUT', '10'),
       (2, 'HTTP_READ_TIMEOUT', '30'),
       (3, 'HTTP_WRITE_TIMEOUT', '30'),
       (4, 'HTTP_MAX_POOL_SIZE', '100'),
       (5, 'HTTP_KEEP_ALIVE_DURATION', '5'),
       (6, 'FLOW_ENDPOINT_STRICT_LINEAR', 'false'),
       (7, 'HTTP_PROXY_HOST', null),
       (8, 'HTTP_PROXY_PORT', null),
       (9, 'HTTP_PROXY_USERNAME', null),
       (10, 'HTTP_PROXY_PASSWORD', null),
       (11, 'BREAKPOINT_ERROR_THRESHOLD', '0.5');