ALTER TABLE projects ADD COLUMN active_environment_id INTEGER;
ALTER TABLE environments ADD COLUMN project_id INTEGER;
ALTER TABLE environments ADD COLUMN name VARCHAR(255);

UPDATE projects
SET active_environment_id = environment_id;

UPDATE environments
SET project_id = (
        SELECT p.id
        FROM projects p
        WHERE p.environment_id = environments.id
    ),
    name = 'Default';
