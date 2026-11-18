-- V6: 用户标签偏好（Feed 推荐派生表）
-- 数据源仍是 interactions；本表是可重建的物化缓存。
-- score 为 computed_at 时刻的分数，读取时按 30 天半衰期继续衰减。

CREATE TABLE user_tag_affinity (
    user_id     BIGINT NOT NULL,
    tag_id      BIGINT NOT NULL,
    score       DECIMAL(10,3) NOT NULL DEFAULT 0,
    computed_at DATETIME NOT NULL,
    created_at  DATETIME NOT NULL,
    updated_at  DATETIME NOT NULL,
    PRIMARY KEY (user_id, tag_id)
);

-- 历史互动回填：一次性近似值，不做时间衰减；后续读取/互动会进入衰减体系。
INSERT INTO user_tag_affinity (user_id, tag_id, score, computed_at, created_at, updated_at)
SELECT i.user_id,
       qt.tag_id,
       LEAST(20, GREATEST(-20, SUM(CASE i.type
           WHEN 'favorite' THEN 3
           WHEN 'like' THEN 1
           WHEN 'useful' THEN 1.5
           ELSE 0 END))),
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP
FROM interactions i
JOIN question_tags qt ON qt.question_id = i.target_id
WHERE i.target_type = 'question'
  AND i.type IN ('like', 'favorite', 'useful')
GROUP BY i.user_id, qt.tag_id;
