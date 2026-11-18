-- V5: 公开问答 feed 计数（收藏数 + 公开追问数）
ALTER TABLE questions ADD COLUMN favorite_count INT NOT NULL DEFAULT 0;
ALTER TABLE questions ADD COLUMN followup_count INT NOT NULL DEFAULT 0;

UPDATE questions q
SET favorite_count = (
    SELECT COUNT(*) FROM interactions i
    WHERE i.target_type = 'question' AND i.type = 'favorite' AND i.target_id = q.id
);

UPDATE questions q
SET followup_count = (
    SELECT COUNT(*) FROM followups f
    WHERE f.question_id = q.id AND f.scope = 'public' AND f.status = 'published'
);
