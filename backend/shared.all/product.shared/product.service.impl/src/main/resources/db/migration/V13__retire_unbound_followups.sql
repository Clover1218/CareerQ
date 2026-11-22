-- 新版追问必须关联具体主回答。停用无法确定归属的旧追问及其回复，保留原始行。
UPDATE answers a
SET status = 'removed', updated_at = CURRENT_TIMESTAMP
WHERE a.followup_id IS NOT NULL AND a.status <> 'removed'
  AND EXISTS (SELECT 1 FROM followups f
              WHERE f.id = a.followup_id AND f.question_id = a.question_id
                AND f.scope = 'public' AND f.root_answer_id IS NULL)
  AND EXISTS (SELECT 1 FROM questions q WHERE q.id = a.question_id AND q.scope = 'public');

UPDATE followups
SET status = 'removed'
WHERE scope = 'public' AND root_answer_id IS NULL AND status <> 'removed';

-- 只重算包含此类旧记录的公开问题，避免隐藏内容仍计入页面数量。
UPDATE questions q
SET followup_count = (SELECT COUNT(*) FROM followups f
                      WHERE f.question_id = q.id AND f.scope = 'public'
                        AND f.root_answer_id IS NOT NULL AND f.status = 'published'),
    answer_count = (SELECT COUNT(*) FROM answers a
                    WHERE a.question_id = q.id AND a.status = 'published')
WHERE q.scope = 'public'
  AND EXISTS (SELECT 1 FROM followups f
              WHERE f.question_id = q.id AND f.scope = 'public' AND f.root_answer_id IS NULL);
