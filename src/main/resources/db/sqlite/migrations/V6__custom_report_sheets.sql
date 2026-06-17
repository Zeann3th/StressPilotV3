CREATE TABLE custom_report_sheets
(
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    name          TEXT    NOT NULL,
    display_order INTEGER NOT NULL DEFAULT 0,
    created_at    DATETIME,
    updated_at    DATETIME
);

CREATE TABLE custom_report_elements
(
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    sheet_id      INTEGER NOT NULL REFERENCES custom_report_sheets (id) ON DELETE CASCADE,
    name          TEXT    NOT NULL,
    type          TEXT    NOT NULL CHECK (type IN ('LINE', 'BAR', 'PIE', 'STAT')),
    config        TEXT,
    display_order INTEGER NOT NULL DEFAULT 0,
    created_at    DATETIME,
    updated_at    DATETIME
);
