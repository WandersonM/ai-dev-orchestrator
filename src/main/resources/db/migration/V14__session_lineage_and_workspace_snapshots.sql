alter table agent_session add column if not exists parent_session_id uuid;
alter table agent_session add column if not exists forked_from_checkpoint_id uuid;
alter table agent_session add column if not exists attempt_number integer not null default 1;
alter table agent_session add column if not exists workspace_path text;

alter table agent_session
    add constraint fk_agent_session_parent
    foreign key (parent_session_id) references agent_session(id);

alter table agent_session
    add constraint fk_agent_session_fork_checkpoint
    foreign key (forked_from_checkpoint_id) references agent_checkpoint(id);

alter table tool_execution add column if not exists session_id uuid;
alter table tool_execution drop constraint if exists uk_tool_execution_work_item_step;
alter table tool_execution
    add constraint fk_tool_execution_session
    foreign key (session_id) references agent_session(id);
create unique index if not exists uk_tool_execution_session_step
    on tool_execution(session_id, step_number)
    where session_id is not null;
create index if not exists idx_tool_execution_session on tool_execution(session_id);

alter table work_item add column if not exists active_workspace_path text;

create table if not exists agent_workspace_snapshot (
    id uuid primary key,
    checkpoint_id uuid not null references agent_checkpoint(id) on delete cascade,
    session_id uuid not null references agent_session(id) on delete cascade,
    repository_alias varchar(120) not null,
    worktree_path text not null,
    head_sha varchar(64) not null,
    snapshot_commit_sha varchar(64) not null,
    branch_name varchar(300),
    created_at timestamptz not null,
    constraint uk_agent_workspace_snapshot_checkpoint_repo unique (checkpoint_id, repository_alias)
);
create index if not exists idx_agent_workspace_snapshot_session on agent_workspace_snapshot(session_id, created_at);
