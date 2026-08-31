CREATE TABLE planning_session (
    id UUID PRIMARY KEY,
    work_item_id UUID NOT NULL UNIQUE REFERENCES work_item(id) ON DELETE CASCADE,
    version BIGINT NOT NULL DEFAULT 0,
    status VARCHAR(50) NOT NULL,
    round INTEGER NOT NULL DEFAULT 0,
    max_rounds INTEGER NOT NULL,
    latest_summary TEXT,
    last_analysis_json TEXT,
    final_specification TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    approved_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_planning_session_work_item ON planning_session(work_item_id);

CREATE TABLE planning_question (
    id UUID PRIMARY KEY,
    session_id UUID NOT NULL REFERENCES planning_session(id) ON DELETE CASCADE,
    round INTEGER NOT NULL,
    category VARCHAR(50) NOT NULL,
    question TEXT NOT NULL,
    rationale TEXT NOT NULL,
    blocking BOOLEAN NOT NULL,
    options_json TEXT,
    answer TEXT,
    answered_by VARCHAR(200),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    answered_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_planning_question_session_round ON planning_question(session_id, round, created_at);
