CREATE TABLE environment_profile (
    id uuid PRIMARY KEY,
    project_repository_id uuid NOT NULL,
    backend_type varchar(40) NOT NULL,
    container_image text,
    network_policy varchar(30) NOT NULL,
    cpu_limit double precision NOT NULL,
    memory_limit_mb integer NOT NULL,
    pids_limit integer NOT NULL,
    timeout_seconds integer NOT NULL,
    setup_command text,
    env_allowlist text,
    secret_allowlist text,
    enabled boolean NOT NULL DEFAULT true,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT uk_environment_profile_repository UNIQUE (project_repository_id),
    CONSTRAINT fk_environment_profile_repository FOREIGN KEY (project_repository_id) REFERENCES project_repository(id) ON DELETE CASCADE,
    CONSTRAINT ck_environment_cpu CHECK (cpu_limit > 0),
    CONSTRAINT ck_environment_memory CHECK (memory_limit_mb > 0),
    CONSTRAINT ck_environment_pids CHECK (pids_limit > 0),
    CONSTRAINT ck_environment_timeout CHECK (timeout_seconds > 0)
);

CREATE INDEX idx_environment_profile_backend ON environment_profile(backend_type, enabled);
