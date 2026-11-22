-- 每条主回答独立成楼；旧追问只有能唯一确定主回答时才回填。
ALTER TABLE followups ADD COLUMN root_answer_id BIGINT NULL;
CREATE INDEX idx_followups_root_status ON followups (root_answer_id, status, id);

UPDATE followups f
SET root_answer_id = (
    SELECT MIN(a.id) FROM answers a
    WHERE a.question_id = f.question_id AND a.author_id = f.hr_id
      AND a.followup_id IS NULL AND a.status IN ('published', 'removed')
)
WHERE f.scope = 'public' AND f.hr_id IS NOT NULL
  AND (SELECT COUNT(*) FROM answers a
       WHERE a.question_id = f.question_id AND a.author_id = f.hr_id
         AND a.followup_id IS NULL AND a.status IN ('published', 'removed')) = 1;
