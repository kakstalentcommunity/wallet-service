CREATE TABLE audit_log (
    id UUID PRIMARY KEY,
    actor_user_id UUID REFERENCES users(id),
    action VARCHAR(64) NOT NULL,
    detail JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX audit_log_actor_created_idx ON audit_log(actor_user_id, created_at DESC);
CREATE INDEX audit_log_action_created_idx ON audit_log(action, created_at DESC);
