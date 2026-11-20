ALTER TABLE answers ADD COLUMN followup_id BIGINT NULL;
CREATE INDEX idx_answers_followup ON answers (followup_id, status);
