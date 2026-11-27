CREATE TABLE roundtable_nominations (
    week_start DATE NOT NULL,
    question_id BIGINT NOT NULL,
    PRIMARY KEY (week_start, question_id),
    FOREIGN KEY (question_id) REFERENCES questions (id)
);

CREATE TABLE roundtable_votes (
    week_start DATE NOT NULL,
    student_id BIGINT NOT NULL,
    question_id BIGINT NOT NULL,
    PRIMARY KEY (week_start, student_id),
    FOREIGN KEY (student_id) REFERENCES users (id),
    FOREIGN KEY (week_start, question_id) REFERENCES roundtable_nominations (week_start, question_id)
);

CREATE INDEX idx_roundtable_votes_topic ON roundtable_votes (week_start, question_id);
