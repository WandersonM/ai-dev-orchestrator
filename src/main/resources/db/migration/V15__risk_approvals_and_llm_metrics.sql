create table if not exists approval_request (
    id uuid primary key,
    work_item_id uuid not null,
    session_id uuid not null references agent_session(id) on delete cascade,
    step_number integer not null,
    tool_name varchar(160) not null,
    arguments_hash varchar(128) not null,
    risk_level varchar(30) not null,
    capabilities varchar(500) not null,
    reason text,
    status varchar(30) not null,
    requested_at timestamptz not null,
    decided_at timestamptz,
    decided_by varchar(200),
    decision_note text,
    constraint uk_approval_request_call unique(session_id, step_number, tool_name, arguments_hash)
);
create index if not exists idx_approval_request_work_item on approval_request(work_item_id, requested_at desc);
create index if not exists idx_approval_request_status on approval_request(status, requested_at);

create table if not exists llm_call_metric (
    id uuid primary key,
    work_item_id uuid,
    session_id uuid references agent_session(id) on delete set null,
    agent_type varchar(80),
    task varchar(80) not null,
    provider varchar(40) not null,
    model varchar(160) not null,
    input_tokens bigint not null default 0,
    output_tokens bigint not null default 0,
    cached_tokens bigint not null default 0,
    total_tokens bigint not null default 0,
    latency_ms bigint not null default 0,
    estimated_cost_usd numeric(18,8) not null default 0,
    created_at timestamptz not null
);
create index if not exists idx_llm_call_metric_work_item on llm_call_metric(work_item_id, created_at);
create index if not exists idx_llm_call_metric_session on llm_call_metric(session_id, created_at);
