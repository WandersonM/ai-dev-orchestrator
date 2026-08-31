create table if not exists repository_verification_profile (
    id uuid primary key,
    project_repository_id uuid not null unique references project_repository(id) on delete cascade,
    lint_command text,
    coverage_command text,
    contract_command text,
    migration_command text,
    enabled boolean not null default true,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create table if not exists verification_run (
    id uuid primary key,
    work_item_id uuid not null references work_item(id) on delete cascade,
    status varchar(30) not null,
    started_at timestamptz not null,
    finished_at timestamptz,
    duration_ms bigint
);
create index if not exists idx_verification_run_work_item on verification_run(work_item_id, started_at desc);

create table if not exists verification_run_item (
    id uuid primary key,
    verification_run_id uuid not null references verification_run(id) on delete cascade,
    project_repository_id uuid,
    repository_alias varchar(120) not null,
    check_type varchar(40) not null,
    command_text text not null,
    status varchar(30) not null,
    exit_code integer,
    output_text text,
    started_at timestamptz not null,
    finished_at timestamptz,
    duration_ms bigint
);
create index if not exists idx_verification_item_run on verification_run_item(verification_run_id, started_at);
