CREATE TABLE wave_execution (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES project(id) ON DELETE CASCADE,
    status VARCHAR(30) NOT NULL,
    max_parallel INTEGER NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    finished_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_wave_execution_project_started ON wave_execution(project_id, started_at DESC);

CREATE TABLE wave_execution_item (
    id UUID PRIMARY KEY,
    wave_execution_id UUID NOT NULL REFERENCES wave_execution(id) ON DELETE CASCADE,
    work_item_id UUID NOT NULL REFERENCES work_item(id) ON DELETE CASCADE,
    status_before VARCHAR(60) NOT NULL,
    status_after VARCHAR(60),
    error_message TEXT,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    finished_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_wave_execution_item_wave ON wave_execution_item(wave_execution_id);
