CREATE TABLE hr_notifications (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    recipient_id BIGINT NOT NULL,
    event_key VARCHAR(128) NOT NULL,
    type VARCHAR(32) NOT NULL,
    category VARCHAR(16) NOT NULL,
    question_id BIGINT NOT NULL,
    answer_id BIGINT NULL,
    followup_id BIGINT NULL,
    read_at DATETIME NULL,
    created_at DATETIME NOT NULL
);
CREATE UNIQUE INDEX uk_hr_notifications_event ON hr_notifications (recipient_id, event_key);
CREATE INDEX idx_hr_notifications_recipient ON hr_notifications (recipient_id, id);
CREATE INDEX idx_hr_notifications_unread ON hr_notifications (recipient_id, read_at, id);
