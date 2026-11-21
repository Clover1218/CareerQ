-- 区分作者撤销与运营下架；旧数据不推断来源，保留原始内容供内部审查。
ALTER TABLE answers ADD COLUMN removal_source VARCHAR(16) NULL;
ALTER TABLE answers ADD COLUMN removed_by BIGINT NULL;
ALTER TABLE answers ADD COLUMN removed_at DATETIME NULL;

UPDATE answers SET removal_source = 'unknown' WHERE status = 'removed';

-- 修复旧运营下架遗漏的计数。既有口径：已发布 HR 主回答及追问回复的总数。
UPDATE questions q
SET answer_count = (SELECT COUNT(*) FROM answers a
                    WHERE a.question_id = q.id AND a.status = 'published'),
    updated_at = CURRENT_TIMESTAMP
WHERE q.scope = 'public'
  AND q.answer_count <> (SELECT COUNT(*) FROM answers a
                         WHERE a.question_id = q.id AND a.status = 'published');
