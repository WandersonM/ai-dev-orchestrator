create table work_item (
    id uuid primary key,
    external_id varchar(120),
    title varchar(300) not null,
    description text,
    status varchar(60) not null,
    repository_path text not null,
    branch_name varchar(255),
    specification text,
    implementation_report text,
    review_report text,
    review_iterations integer not null default 0,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create table agent_execution (
    id uuid primary key,
    work_item_id uuid not null references work_item(id),
    agent_type varchar(60) not null,
    status varchar(60) not null,
    input_summary text,
    output_summary text,
    error_message text,
    started_at timestamptz not null,
    finished_at timestamptz,
    duration_ms bigint
);

create index idx_work_item_status on work_item(status);
create index idx_agent_execution_work_item on agent_execution(work_item_id);
