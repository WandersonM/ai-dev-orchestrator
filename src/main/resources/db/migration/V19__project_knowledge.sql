create table project_knowledge (
    id uuid primary key,
    project_id uuid not null,
    knowledge_type varchar(40) not null,
    statement text not null,
    source_type varchar(60) not null,
    source_ref text,
    confidence varchar(30) not null,
    active boolean not null default true,
    created_by varchar(200),
    created_at timestamp with time zone not null,
    superseded_at timestamp with time zone
);
create index idx_project_knowledge_project_active on project_knowledge(project_id, active);
