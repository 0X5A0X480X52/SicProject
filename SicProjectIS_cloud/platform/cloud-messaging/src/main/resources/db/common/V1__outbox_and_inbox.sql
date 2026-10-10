CREATE TABLE IF NOT EXISTS outbox_event (
    event_id VARCHAR(36) PRIMARY KEY,
    event_type VARCHAR(160) NOT NULL,
    routing_key VARCHAR(160) NOT NULL,
    payload_json JSON NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    published_at TIMESTAMP(6) NULL,
    attempts INT NOT NULL DEFAULT 0,
    last_error VARCHAR(1000) NULL,
    INDEX idx_outbox_pending (published_at, created_at)
);

CREATE TABLE IF NOT EXISTS consumed_event (
    event_id VARCHAR(36) NOT NULL,
    consumer_name VARCHAR(160) NOT NULL,
    consumed_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (event_id, consumer_name)
);
