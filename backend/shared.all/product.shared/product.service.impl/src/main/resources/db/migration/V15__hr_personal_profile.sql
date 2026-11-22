ALTER TABLE hr_profiles ADD COLUMN bio VARCHAR(500) NULL;
ALTER TABLE hr_profiles ADD COLUMN specialties_json TEXT NULL;
CREATE INDEX idx_answers_author_id ON answers (author_id, id);
