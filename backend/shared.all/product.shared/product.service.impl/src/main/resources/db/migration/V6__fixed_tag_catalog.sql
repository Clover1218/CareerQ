-- V5: 固定标签目录（产品决定不再支持自定义标签）
-- 只注入/校准 tags 目录，不改动既有 question_tags 关联；已存在的同 slug 标签保留原 id。
-- 新增标签时通过后续迁移追加，不在运行时自动建标签。

INSERT INTO tags (name, slug, category, weight)
SELECT v.tag_name, v.tag_slug, v.tag_category, v.tag_weight
FROM (
    SELECT '实习' AS tag_name, '实习' AS tag_slug, '求职阶段' AS tag_category, 100 AS tag_weight
    UNION ALL SELECT '暑期实习', '暑期实习', '求职阶段', 90
    UNION ALL SELECT '秋招', '秋招', '求职阶段', 100
    UNION ALL SELECT '春招', '春招', '求职阶段', 90
    UNION ALL SELECT '校招', '校招', '求职阶段', 95
    UNION ALL SELECT '社招', '社招', '求职阶段', 60
    UNION ALL SELECT '实习转正', '实习转正', '求职阶段', 70
    UNION ALL SELECT '简历', '简历', '求职准备', 100
    UNION ALL SELECT '面试准备', '面试准备', '求职准备', 100
    UNION ALL SELECT '笔试', '笔试', '求职准备', 85
    UNION ALL SELECT '群面', '群面', '求职准备', 70
    UNION ALL SELECT '单面', '单面', '求职准备', 70
    UNION ALL SELECT '项目经历', '项目经历', '求职准备', 75
    UNION ALL SELECT '作品集', '作品集', '求职准备', 60
    UNION ALL SELECT '职业规划', '职业规划', '方向选择', 95
    UNION ALL SELECT '行业选择', '行业选择', '方向选择', 90
    UNION ALL SELECT '岗位选择', '岗位选择', '方向选择', 85
    UNION ALL SELECT '考研', '考研', '方向选择', 80
    UNION ALL SELECT '保研', '保研', '方向选择', 75
    UNION ALL SELECT '留学', '留学', '方向选择', 75
    UNION ALL SELECT '考公', '考公', '方向选择', 80
    UNION ALL SELECT '跨专业求职', '跨专业求职', '方向选择', 70
    UNION ALL SELECT '应届生身份', '应届生身份', '方向选择', 65
    UNION ALL SELECT 'Offer选择', 'offer选择', 'Offer 与入职', 90
    UNION ALL SELECT '薪资谈判', '薪资谈判', 'Offer 与入职', 80
    UNION ALL SELECT '三方协议', '三方协议', 'Offer 与入职', 65
    UNION ALL SELECT '入职准备', '入职准备', 'Offer 与入职', 60
    UNION ALL SELECT '试用期', '试用期', '职场成长', 60
    UNION ALL SELECT '转正', '转正', '职场成长', 60
    UNION ALL SELECT '晋升', '晋升', '职场成长', 55
    UNION ALL SELECT '跳槽', '跳槽', '职场成长', 60
    UNION ALL SELECT '求职焦虑', '求职焦虑', '求职心态', 55
    UNION ALL SELECT '面试复盘', '面试复盘', '求职心态', 65
) AS v
WHERE NOT EXISTS (SELECT 1 FROM tags t WHERE t.slug = v.tag_slug);

-- 兼容历史上自动创建的标签：把已存在的同 slug 记录校准到目录的分类和权重。
UPDATE tags SET
    category = CASE slug
        WHEN '实习' THEN '求职阶段'
        WHEN '暑期实习' THEN '求职阶段'
        WHEN '秋招' THEN '求职阶段'
        WHEN '春招' THEN '求职阶段'
        WHEN '校招' THEN '求职阶段'
        WHEN '社招' THEN '求职阶段'
        WHEN '实习转正' THEN '求职阶段'
        WHEN '简历' THEN '求职准备'
        WHEN '面试准备' THEN '求职准备'
        WHEN '笔试' THEN '求职准备'
        WHEN '群面' THEN '求职准备'
        WHEN '单面' THEN '求职准备'
        WHEN '项目经历' THEN '求职准备'
        WHEN '作品集' THEN '求职准备'
        WHEN '职业规划' THEN '方向选择'
        WHEN '行业选择' THEN '方向选择'
        WHEN '岗位选择' THEN '方向选择'
        WHEN '考研' THEN '方向选择'
        WHEN '保研' THEN '方向选择'
        WHEN '留学' THEN '方向选择'
        WHEN '考公' THEN '方向选择'
        WHEN '跨专业求职' THEN '方向选择'
        WHEN '应届生身份' THEN '方向选择'
        WHEN 'offer选择' THEN 'Offer 与入职'
        WHEN '薪资谈判' THEN 'Offer 与入职'
        WHEN '三方协议' THEN 'Offer 与入职'
        WHEN '入职准备' THEN 'Offer 与入职'
        WHEN '试用期' THEN '职场成长'
        WHEN '转正' THEN '职场成长'
        WHEN '晋升' THEN '职场成长'
        WHEN '跳槽' THEN '职场成长'
        WHEN '求职焦虑' THEN '求职心态'
        WHEN '面试复盘' THEN '求职心态'
        ELSE category
    END,
    weight = CASE slug
        WHEN '实习' THEN 100
        WHEN '暑期实习' THEN 90
        WHEN '秋招' THEN 100
        WHEN '春招' THEN 90
        WHEN '校招' THEN 95
        WHEN '社招' THEN 60
        WHEN '实习转正' THEN 70
        WHEN '简历' THEN 100
        WHEN '面试准备' THEN 100
        WHEN '笔试' THEN 85
        WHEN '群面' THEN 70
        WHEN '单面' THEN 70
        WHEN '项目经历' THEN 75
        WHEN '作品集' THEN 60
        WHEN '职业规划' THEN 95
        WHEN '行业选择' THEN 90
        WHEN '岗位选择' THEN 85
        WHEN '考研' THEN 80
        WHEN '保研' THEN 75
        WHEN '留学' THEN 75
        WHEN '考公' THEN 80
        WHEN '跨专业求职' THEN 70
        WHEN '应届生身份' THEN 65
        WHEN 'offer选择' THEN 90
        WHEN '薪资谈判' THEN 80
        WHEN '三方协议' THEN 65
        WHEN '入职准备' THEN 60
        WHEN '试用期' THEN 60
        WHEN '转正' THEN 60
        WHEN '晋升' THEN 55
        WHEN '跳槽' THEN 60
        WHEN '求职焦虑' THEN 55
        WHEN '面试复盘' THEN 65
        ELSE weight
    END
WHERE slug IN (
    '实习', '暑期实习', '秋招', '春招', '校招', '社招', '实习转正',
    '简历', '面试准备', '笔试', '群面', '单面', '项目经历', '作品集',
    '职业规划', '行业选择', '岗位选择', '考研', '保研', '留学', '考公',
    '跨专业求职', '应届生身份',
    'offer选择', '薪资谈判', '三方协议', '入职准备',
    '试用期', '转正', '晋升', '跳槽',
    '求职焦虑', '面试复盘'
);
