CREATE TABLE pending_notification (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    event_id CHAR(36) NOT NULL UNIQUE,
    user_id BIGINT NULL,
    event_name VARCHAR(100) NOT NULL,
    payload_json JSON NOT NULL,
    status VARCHAR(32) NOT NULL,
    retry_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    delivered_at TIMESTAMP(6) NULL,
    INDEX idx_notification_delivery(status, created_at),
    INDEX idx_notification_user(user_id, status)
);
