ALTER TABLE outbox_event
    ADD COLUMN next_attempt_at TIMESTAMP(6) NULL AFTER attempts,
    ADD COLUMN lock_owner VARCHAR(64) NULL AFTER next_attempt_at,
    ADD COLUMN locked_until TIMESTAMP(6) NULL AFTER lock_owner;

CREATE INDEX idx_outbox_dispatch
    ON outbox_event(published_at, next_attempt_at, locked_until, created_at);
