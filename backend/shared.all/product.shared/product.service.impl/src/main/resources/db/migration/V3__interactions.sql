-- V3: 互动补全（问题点赞计数 + 收藏/互动查询索引）
ALTER TABLE questions ADD COLUMN like_count INT NOT NULL DEFAULT 0;
CREATE INDEX idx_interactions_user_type ON interactions (user_id, type, id);