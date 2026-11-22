-- V11: 审核态（moderation_status）与可见态（visibility）解耦
-- 说明：questions / answers / followups 不再保留 status，统一由审核态与可见态表达。
-- 依赖旧 status 的索引会随列删除由数据库自动清理（MySQL 与 H2 行为一致）。

-- 1) 新增审核态、可见态与审核辅助字段
ALTER TABLE questions ADD COLUMN moderation_status VARCHAR(16) NOT NULL DEFAULT 'L1';
ALTER TABLE questions ADD COLUMN visibility VARCHAR(16) NOT NULL DEFAULT 'HIDDEN';
ALTER TABLE questions ADD COLUMN risk_level VARCHAR(16) NOT NULL DEFAULT 'unrated';
ALTER TABLE questions ADD COLUMN moderation_reason VARCHAR(500) NULL;
ALTER TABLE questions ADD COLUMN moderated_at DATETIME NULL;

ALTER TABLE answers ADD COLUMN moderation_status VARCHAR(16) NOT NULL DEFAULT 'L1';
ALTER TABLE answers ADD COLUMN visibility VARCHAR(16) NOT NULL DEFAULT 'HIDDEN';
ALTER TABLE answers ADD COLUMN risk_level VARCHAR(16) NOT NULL DEFAULT 'unrated';
ALTER TABLE answers ADD COLUMN moderation_reason VARCHAR(500) NULL;
ALTER TABLE answers ADD COLUMN moderated_at DATETIME NULL;

ALTER TABLE followups ADD COLUMN moderation_status VARCHAR(16) NOT NULL DEFAULT 'L1';
ALTER TABLE followups ADD COLUMN visibility VARCHAR(16) NOT NULL DEFAULT 'HIDDEN';
ALTER TABLE followups ADD COLUMN risk_level VARCHAR(16) NOT NULL DEFAULT 'unrated';
ALTER TABLE followups ADD COLUMN moderation_reason VARCHAR(500) NULL;
ALTER TABLE followups ADD COLUMN moderated_at DATETIME NULL;

-- 2) 回填历史状态
UPDATE questions
SET moderation_status = CASE status
        WHEN 'published' THEN 'APPROVED'
        WHEN 'pending_machine' THEN 'L2'
        WHEN 'pending_human' THEN 'L3'
        WHEN 'rejected' THEN 'REJECTED'
        WHEN 'removed' THEN 'APPROVED'
        ELSE 'APPROVED'
    END,
    visibility = CASE status
        WHEN 'published' THEN 'VISIBLE'
        WHEN 'removed' THEN 'REMOVED'
        ELSE 'HIDDEN'
    END,
    risk_level = CASE
        WHEN status IN ('rejected', 'pending_human') THEN 'WATCH'
        ELSE 'UNRATED'
    END;

UPDATE answers
SET moderation_status = CASE status
        WHEN 'published' THEN 'APPROVED'
        WHEN 'pending_machine' THEN 'L2'
        WHEN 'pending_human' THEN 'L3'
        WHEN 'rejected' THEN 'REJECTED'
        WHEN 'removed' THEN 'APPROVED'
        ELSE 'APPROVED'
    END,
    visibility = CASE status
        WHEN 'published' THEN 'VISIBLE'
        WHEN 'removed' THEN 'REMOVED'
        ELSE 'HIDDEN'
    END,
    risk_level = CASE
        WHEN status IN ('rejected', 'pending_human') THEN 'WATCH'
        ELSE 'UNRATED'
    END;

UPDATE followups
SET moderation_status = CASE status
        WHEN 'published' THEN 'APPROVED'
        WHEN 'pending_machine' THEN 'L2'
        WHEN 'pending_human' THEN 'L3'
        WHEN 'rejected' THEN 'REJECTED'
        WHEN 'removed' THEN 'APPROVED'
        ELSE 'APPROVED'
    END,
    visibility = CASE status
        WHEN 'published' THEN 'VISIBLE'
        WHEN 'removed' THEN 'REMOVED'
        ELSE 'HIDDEN'
    END,
    risk_level = CASE
        WHEN status IN ('rejected', 'pending_human') THEN 'WATCH'
        ELSE 'UNRATED'
    END;

-- 已下架内容只有在存在明确 reject 记录时才回填为 REJECTED
UPDATE questions q SET moderation_status = 'REJECTED'
WHERE q.status = 'removed'
  AND EXISTS (SELECT 1 FROM moderation_records r
              WHERE r.target_type = 'question' AND r.target_id = q.id AND r.decision = 'reject');

UPDATE answers a SET moderation_status = 'REJECTED'
WHERE a.status = 'removed'
  AND EXISTS (SELECT 1 FROM moderation_records r
              WHERE r.target_type = 'answer' AND r.target_id = a.id AND r.decision = 'reject');

UPDATE followups f SET moderation_status = 'REJECTED'
WHERE f.status = 'removed'
  AND EXISTS (SELECT 1 FROM moderation_records r
              WHERE r.target_type = 'followup' AND r.target_id = f.id AND r.decision = 'reject');

-- 3) 审核记录补字段并迁移旧 stage/decision 语系
ALTER TABLE moderation_records ADD COLUMN risk_level VARCHAR(16) NULL;
ALTER TABLE moderation_records ADD COLUMN confidence DECIMAL(5,4) NULL;
ALTER TABLE moderation_records ADD COLUMN from_moderation_status VARCHAR(16) NULL;
ALTER TABLE moderation_records ADD COLUMN to_moderation_status VARCHAR(16) NULL;

UPDATE moderation_records SET stage = 'L1' WHERE stage = 'machine';
UPDATE moderation_records SET stage = 'L3' WHERE stage = 'human';

UPDATE moderation_records SET decision = 'pass' WHERE stage = 'L1' AND decision = 'published';
UPDATE moderation_records SET decision = 'review' WHERE stage = 'L1' AND decision = 'pending_human';
UPDATE moderation_records SET decision = 'reject' WHERE stage = 'L1' AND decision = 'rejected';

UPDATE moderation_records SET to_moderation_status = 'APPROVED'
WHERE stage = 'L3' AND decision = 'approve';
UPDATE moderation_records SET to_moderation_status = 'REJECTED'
WHERE stage = 'L3' AND decision = 'reject';

-- 4) 校准计数：只统计当前可见内容
UPDATE questions q
SET answer_count = (SELECT COUNT(*) FROM answers a WHERE a.question_id = q.id AND a.visibility = 'VISIBLE');

UPDATE questions q
SET followup_count = (SELECT COUNT(*) FROM followups f
                      WHERE f.question_id = q.id AND f.scope = 'public' AND f.visibility = 'VISIBLE');

-- 5) 删除依赖旧 status 的索引，再删除列
DROP INDEX idx_questions_status_created ON questions;
DROP INDEX idx_questions_scope_status ON questions;
DROP INDEX idx_questions_public_created ON questions;
DROP INDEX idx_answers_question_status ON answers;
DROP INDEX idx_answers_followup ON answers;

ALTER TABLE questions DROP COLUMN status;
ALTER TABLE answers DROP COLUMN status;
ALTER TABLE followups DROP COLUMN status;

-- 6) 新索引
CREATE INDEX idx_questions_l3 ON questions (moderation_status, risk_level, created_at);
CREATE INDEX idx_answers_l3 ON answers (moderation_status, risk_level, created_at);
CREATE INDEX idx_followups_l3 ON followups (moderation_status, risk_level, created_at);

CREATE INDEX idx_questions_visible ON questions (scope, visibility, created_at);
CREATE INDEX idx_answers_visible ON answers (question_id, visibility, created_at);
CREATE INDEX idx_followups_visible ON followups (question_id, visibility, created_at);

CREATE INDEX idx_moderation_operator ON moderation_records (operator_id, stage, created_at);