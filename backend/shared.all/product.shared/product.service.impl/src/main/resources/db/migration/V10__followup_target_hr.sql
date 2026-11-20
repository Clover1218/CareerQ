-- V10: 追问绑定到具体 HR，支持「题主与每位 HR 一对一」的会话线程
ALTER TABLE followups ADD COLUMN hr_id BIGINT NULL;
CREATE INDEX idx_followups_question_hr ON followups (question_id, hr_id, id);

-- 历史数据回填：优先取该追问的回复者；没有回复时，若问题只有一位 HR 回答过，就归给这位 HR。
UPDATE followups f
SET hr_id = (
    SELECT MIN(a.author_id) FROM answers a
    WHERE a.followup_id = f.id AND a.status = 'published'
)
WHERE f.hr_id IS NULL;

UPDATE followups f
SET hr_id = (
    SELECT MIN(a.author_id) FROM answers a
    WHERE a.question_id = f.question_id
      AND a.followup_id IS NULL
      AND a.status = 'published'
)
WHERE f.hr_id IS NULL
  AND (
    SELECT COUNT(DISTINCT a.author_id) FROM answers a
    WHERE a.question_id = f.question_id
      AND a.followup_id IS NULL
      AND a.status = 'published'
  ) = 1;