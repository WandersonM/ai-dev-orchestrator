create table if not exists audit_event (
    id uuid primary key,
    work_item_id uuid,
    session_id uuid,
    event_type varchar(120) not null,
    actor_type varchar(40) not null,
    actor_id varchar(200),
    entity_type varchar(120) not null,
    entity_id varchar(200),
    payload_json text,
    created_at timestamptz not null
);
create index if not exists idx_audit_event_work_item on audit_event(work_item_id, created_at);
create index if not exists idx_audit_event_session on audit_event(session_id, created_at);
create index if not exists idx_audit_event_type on audit_event(event_type, created_at);
