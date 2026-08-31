create table work_item_artifact (
    id uuid primary key,
    work_item_id uuid not null,
    session_id uuid,
    repository_alias varchar(120),
    artifact_type varchar(40) not null,
    relative_path text not null,
    content_type varchar(200),
    description text,
    sha256 varchar(64) not null,
    size_bytes bigint not null,
    created_at timestamptz not null
);
create index idx_work_item_artifact_work_item on work_item_artifact(work_item_id, created_at);
create index idx_work_item_artifact_session on work_item_artifact(session_id, created_at);
