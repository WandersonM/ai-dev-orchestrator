create table tool_execution (
    id uuid primary key,
    work_item_id uuid not null,
    agent_type varchar(60) not null,
    step_number integer not null,
    tool_name varchar(120) not null,
    arguments_json text not null,
    status varchar(30) not null,
    output_text text,
    error_message text,
    started_at timestamptz not null,
    finished_at timestamptz,
    duration_ms bigint,
    constraint uk_tool_execution_work_item_step unique (work_item_id, agent_type, step_number)
);

create index idx_tool_execution_work_item on tool_execution (work_item_id);
create index idx_tool_execution_status on tool_execution (status);
