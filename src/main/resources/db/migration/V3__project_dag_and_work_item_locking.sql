CREATE TABLE project (
    id UUID PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    repository_path TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

ALTER TABLE work_item
    ADD COLUMN project_id UUID NULL REFERENCES project(id),
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

CREATE INDEX idx_work_item_project_id ON work_item(project_id);

CREATE TABLE work_item_dependency (
    id UUID PRIMARY KEY,
    work_item_id UUID NOT NULL REFERENCES work_item(id) ON DELETE CASCADE,
    blocked_by_work_item_id UUID NOT NULL REFERENCES work_item(id) ON DELETE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_work_item_dependency UNIQUE (work_item_id, blocked_by_work_item_id),
    CONSTRAINT ck_work_item_dependency_not_self CHECK (work_item_id <> blocked_by_work_item_id)
);

CREATE INDEX idx_work_item_dependency_work_item ON work_item_dependency(work_item_id);
CREATE INDEX idx_work_item_dependency_blocked_by ON work_item_dependency(blocked_by_work_item_id);
