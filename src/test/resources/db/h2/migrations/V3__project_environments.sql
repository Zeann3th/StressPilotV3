ALTER TABLE projects ADD COLUMN active_environment_id BIGINT;
ALTER TABLE environments ADD COLUMN project_id BIGINT;
ALTER TABLE environments ADD COLUMN name VARCHAR(255);

UPDATE projects
SET active_environment_id = environment_id;

UPDATE environments e
SET project_id = (
        SELECT p.id
        FROM projects p
        WHERE p.environment_id = e.id
    ),
    name = 'Default';

ALTER TABLE environments ALTER COLUMN name SET NOT NULL;

ALTER TABLE projects
    ADD CONSTRAINT fk_projects_active_environment
    FOREIGN KEY (active_environment_id) REFERENCES environments (id);
