-- V8: 公开列表的时间筛选与最新排序复合索引
CREATE INDEX idx_questions_public_created ON questions (scope, status, created_at);
