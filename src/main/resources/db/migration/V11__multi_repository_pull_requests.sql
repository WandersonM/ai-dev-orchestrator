CREATE TABLE work_item_pull_request (
    id UUID PRIMARY KEY,
    work_item_id UUID NOT NULL REFERENCES work_item(id) ON DELETE CASCADE,
    repository_alias VARCHAR(80) NOT NULL,
    repository_slug VARCHAR(300) NOT NULL,
    pull_request_number INTEGER NOT NULL,
    pull_request_url TEXT NOT NULL,
    head_branch VARCHAR(300) NOT NULL,
    base_branch VARCHAR(300) NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_work_item_pr_repo UNIQUE (work_item_id, repository_alias)
);

CREATE INDEX idx_work_item_pull_request_work_item ON work_item_pull_request(work_item_id);
