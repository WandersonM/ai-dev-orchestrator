CREATE TABLE project_repository (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES project(id) ON DELETE CASCADE,
    alias VARCHAR(80) NOT NULL,
    kind VARCHAR(40) NOT NULL,
    repository_path TEXT NOT NULL,
    base_branch VARCHAR(200) NOT NULL,
    branch_prefix VARCHAR(80) NOT NULL,
    instructions_path TEXT,
    build_command TEXT,
    test_command TEXT,
    java_version VARCHAR(30),
    node_version VARCHAR(30),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_project_repository_alias UNIQUE (project_id, alias)
);

CREATE INDEX idx_project_repository_project ON project_repository(project_id);

INSERT INTO project_repository (
    id, project_id, alias, kind, repository_path, base_branch, branch_prefix,
    instructions_path, build_command, test_command, java_version, node_version,
    enabled, created_at, updated_at
)
SELECT gen_random_uuid(), p.id, 'default', 'OTHER', p.repository_path, 'main', 'ai/',
       NULL, NULL, NULL, NULL, NULL, TRUE, p.created_at, p.updated_at
FROM project p;

CREATE TABLE work_item_repository (
    id UUID PRIMARY KEY,
    work_item_id UUID NOT NULL REFERENCES work_item(id) ON DELETE CASCADE,
    project_repository_id UUID NOT NULL REFERENCES project_repository(id) ON DELETE CASCADE,
    purpose VARCHAR(30) NOT NULL,
    base_branch_override VARCHAR(200),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_work_item_repository UNIQUE (work_item_id, project_repository_id)
);

CREATE INDEX idx_work_item_repository_work_item ON work_item_repository(work_item_id);
CREATE INDEX idx_work_item_repository_project_repository ON work_item_repository(project_repository_id);

INSERT INTO work_item_repository (id, work_item_id, project_repository_id, purpose, created_at)
SELECT gen_random_uuid(), wi.id, pr.id, 'PRIMARY', wi.created_at
FROM work_item wi
JOIN project_repository pr ON pr.project_id = wi.project_id AND pr.alias = 'default'
WHERE wi.project_id IS NOT NULL;
