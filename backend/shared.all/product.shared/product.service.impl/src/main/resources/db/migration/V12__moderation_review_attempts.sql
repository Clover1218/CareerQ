-- V12: 人工审核次数、撤销与仲裁所需的额外权限
-- 说明：review_attempts 只统计人工裁定（approve/reject，含仲裁），撤销不回退。

-- 1) 审核次数
ALTER TABLE questions ADD COLUMN review_attempts INT NOT NULL DEFAULT 0;
ALTER TABLE answers   ADD COLUMN review_attempts INT NOT NULL DEFAULT 0;
ALTER TABLE followups ADD COLUMN review_attempts INT NOT NULL DEFAULT 0;

-- 2) 回填：兼容 V11 把人工记录写成 stage='L3' 的存量
UPDATE questions q SET review_attempts = (
    SELECT COUNT(*) FROM moderation_records r
    WHERE r.target_type = 'question' AND r.target_id = q.id
      AND r.stage IN ('human', 'L3') AND r.decision IN ('approve', 'reject')
);
UPDATE answers a SET review_attempts = (
    SELECT COUNT(*) FROM moderation_records r
    WHERE r.target_type = 'answer' AND r.target_id = a.id
      AND r.stage IN ('human', 'L3') AND r.decision IN ('approve', 'reject')
);
UPDATE followups f SET review_attempts = (
    SELECT COUNT(*) FROM moderation_records r
    WHERE r.target_type = 'followup' AND r.target_id = f.id
      AND r.stage IN ('human', 'L3') AND r.decision IN ('approve', 'reject')
);

-- 3) 归一化人工审核记录的 stage（代码实际写 'human'）
UPDATE moderation_records SET stage = 'human' WHERE stage = 'L3';

-- 4) 额外权限表：只存 operator/admin 之外的额外授权
CREATE TABLE user_permissions (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT      NOT NULL,
    permission VARCHAR(32) NOT NULL,
    created_at DATETIME    NOT NULL
);
CREATE UNIQUE INDEX uk_user_permissions ON user_permissions (user_id, permission);