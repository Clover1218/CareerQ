CREATE TABLE student_notifications (
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
CREATE UNIQUE INDEX uk_student_notifications_event ON student_notifications (recipient_id, event_key);
CREATE INDEX idx_student_notifications_recipient ON student_notifications (recipient_id, id);
CREATE INDEX idx_student_notifications_unread ON student_notifications (recipient_id, read_at, id);

-- Recover genuine existing replies and likes once, using their original timestamps.
-- Moderation decisions without reliable public history are not inferred.
INSERT INTO student_notifications (recipient_id, event_key, type, category, question_id, answer_id, followup_id, created_at)
SELECT q.author_id, CONCAT('answer_published:', a.id), 'answer_published', 'reply', q.id, a.id, a.followup_id, a.created_at
FROM answers a JOIN questions q ON q.id = a.question_id JOIN users u ON u.id = q.author_id
WHERE q.scope = 'public' AND q.status = 'published' AND a.status = 'published' AND u.role = 'student'
AND (a.followup_id IS NULL OR EXISTS (
    SELECT 1 FROM followups f WHERE f.id = a.followup_id AND f.question_id = q.id
    AND f.root_answer_id IS NOT NULL AND f.sender_id = q.author_id AND f.status IN ('published', 'removed')
));

INSERT INTO student_notifications (recipient_id, event_key, type, category, question_id, created_at)
SELECT q.author_id, CONCAT('question_liked:', q.id, ':', i.user_id), 'question_liked', 'like', q.id, i.created_at
FROM interactions i JOIN questions q ON i.target_type = 'question' AND i.target_id = q.id
JOIN users u ON u.id = q.author_id
WHERE i.type = 'like' AND i.user_id <> q.author_id AND q.scope = 'public' AND q.status = 'published' AND u.role = 'student';
