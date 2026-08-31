CREATE TABLE planning_feedback (
    id UUID PRIMARY KEY,
    session_id UUID NOT NULL REFERENCES planning_session(id) ON DELETE CASCADE,
    round INTEGER NOT NULL,
    feedback TEXT NOT NULL,
    provided_by VARCHAR(200) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_planning_feedback_session_created ON planning_feedback(session_id, created_at);
