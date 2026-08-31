create table agent_session (
    id uuid primary key,
    work_item_id uuid not null,
    agent_type varchar(80) not null,
    status varchar(40) not null,
    version bigint not null default 0,
    current_step integer not null default 0,
    checkpoint_seq integer not null default 0,
    started_at timestamp with time zone,
    paused_at timestamp with time zone,
    finished_at timestamp with time zone,
    last_heartbeat_at timestamp with time zone,
    last_error text,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);

create index idx_agent_session_work_item on agent_session(work_item_id, created_at);
create index idx_agent_session_active on agent_session(work_item_id, agent_type, status);

create table agent_session_message (
    id uuid primary key,
    session_id uuid not null references agent_session(id) on delete cascade,
    role varchar(30) not null,
    content text not null,
    provided_by varchar(200),
    consumed_at timestamp with time zone,
    created_at timestamp with time zone not null
);
create index idx_agent_session_message_session on agent_session_message(session_id, created_at);

create table agent_checkpoint (
    id uuid primary key,
    session_id uuid not null references agent_session(id) on delete cascade,
    sequence_number integer not null,
    step_number integer not null,
    checkpoint_type varchar(40) not null,
    summary text,
    provider_turn_id varchar(500),
    created_at timestamp with time zone not null,
    constraint uk_agent_checkpoint_sequence unique(session_id, sequence_number)
);
create index idx_agent_checkpoint_session on agent_checkpoint(session_id, sequence_number);
