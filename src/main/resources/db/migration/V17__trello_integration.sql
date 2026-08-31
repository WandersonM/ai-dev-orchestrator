create table if not exists trello_work_item_link (
    id uuid primary key,
    project_id uuid not null references project(id) on delete cascade,
    work_item_id uuid not null unique references work_item(id) on delete cascade,
    card_id varchar(120) not null unique,
    card_short_link varchar(80),
    card_url text,
    last_question_round integer not null default 0,
    last_seen_comment_at timestamptz,
    created_at timestamptz not null,
    updated_at timestamptz not null
);
create index if not exists idx_trello_link_project on trello_work_item_link(project_id);
