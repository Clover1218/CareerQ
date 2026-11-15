-- V1: 职问桥核心表结构（MySQL / H2 MySQL 兼容模式）
-- 约定：软删除用 status 字段；时间统一 DATETIME，由服务端生成。

CREATE TABLE users (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    role          VARCHAR(16) NOT NULL DEFAULT 'student',
    phone_hash    VARCHAR(64) NULL,
    phone_enc     VARCHAR(255) NULL,
    phone_verified_at DATETIME NULL,
    nickname      VARCHAR(64) NOT NULL,
    school        VARCHAR(128) NULL,
    major         VARCHAR(128) NULL,
    grade         VARCHAR(32) NULL,
    age_group     VARCHAR(16) NULL,
    status        VARCHAR(16) NOT NULL DEFAULT 'active',
    created_at    DATETIME NOT NULL,
    updated_at    DATETIME NOT NULL
);
CREATE UNIQUE INDEX uk_users_phone_hash ON users (phone_hash);
CREATE INDEX idx_users_role_status ON users (role, status);

CREATE TABLE hr_profiles (
    user_id            BIGINT PRIMARY KEY,
    company_display    VARCHAR(128) NOT NULL,
    industry           VARCHAR(64) NULL,
    position           VARCHAR(64) NULL,
    verify_status      VARCHAR(16) NOT NULL DEFAULT 'pending',
    verify_material_ref VARCHAR(255) NULL,
    verified_at        DATETIME NULL
);

CREATE TABLE questions (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    scope           VARCHAR(16) NOT NULL DEFAULT 'public',
    author_id       BIGINT NOT NULL,
    title           VARCHAR(200) NOT NULL,
    body            TEXT NOT NULL,
    status          VARCHAR(32) NOT NULL DEFAULT 'pending_machine',
    assigned_hr_id  BIGINT NULL,
    closed_at       DATETIME NULL,
    first_answer_at DATETIME NULL,
    answer_count    INT NOT NULL DEFAULT 0,
    useful_count    INT NOT NULL DEFAULT 0,
    created_at      DATETIME NOT NULL,
    updated_at      DATETIME NOT NULL
);
CREATE INDEX idx_questions_status_created ON questions (status, created_at);
CREATE INDEX idx_questions_scope_status ON questions (scope, status);
CREATE INDEX idx_questions_author ON questions (author_id);
CREATE INDEX idx_questions_assigned ON questions (assigned_hr_id);

CREATE TABLE answers (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    question_id BIGINT NOT NULL,
    author_id   BIGINT NOT NULL,
    body        TEXT NOT NULL,
    length_type VARCHAR(16) NOT NULL DEFAULT 'detailed',
    status      VARCHAR(32) NOT NULL DEFAULT 'pending_machine',
    like_count  INT NOT NULL DEFAULT 0,
    useful_count INT NOT NULL DEFAULT 0,
    created_at  DATETIME NOT NULL,
    updated_at  DATETIME NOT NULL
);
CREATE INDEX idx_answers_question_status ON answers (question_id, status);
CREATE INDEX idx_answers_author_created ON answers (author_id, created_at);

CREATE TABLE followups (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    question_id BIGINT NOT NULL,
    sender_id   BIGINT NOT NULL,
    sender_role VARCHAR(16) NOT NULL,
    body        TEXT NOT NULL,
    scope       VARCHAR(16) NOT NULL DEFAULT 'public',
    status      VARCHAR(16) NOT NULL DEFAULT 'published',
    created_at  DATETIME NOT NULL
);
CREATE INDEX idx_followups_question ON followups (question_id, created_at);

CREATE TABLE posts (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    author_id  BIGINT NOT NULL,
    title      VARCHAR(200) NOT NULL,
    body       TEXT NOT NULL,
    status     VARCHAR(32) NOT NULL DEFAULT 'pending_machine',
    like_count INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL
);
CREATE INDEX idx_posts_status_created ON posts (status, created_at);

CREATE TABLE tags (
    id       BIGINT AUTO_INCREMENT PRIMARY KEY,
    name     VARCHAR(32) NOT NULL,
    slug     VARCHAR(32) NOT NULL,
    category VARCHAR(32) NULL,
    weight   INT NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX uk_tags_name ON tags (name);
CREATE UNIQUE INDEX uk_tags_slug ON tags (slug);

CREATE TABLE question_tags (
    question_id BIGINT NOT NULL,
    tag_id      BIGINT NOT NULL,
    PRIMARY KEY (question_id, tag_id)
);
CREATE INDEX idx_question_tags_tag ON question_tags (tag_id, question_id);

CREATE TABLE interactions (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT NOT NULL,
    target_type VARCHAR(16) NOT NULL,
    target_id   BIGINT NOT NULL,
    type        VARCHAR(16) NOT NULL,
    created_at  DATETIME NOT NULL
);
CREATE UNIQUE INDEX uk_interactions_unique ON interactions (user_id, target_type, target_id, type);

CREATE TABLE reports (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    reporter_id BIGINT NOT NULL,
    target_type VARCHAR(16) NOT NULL,
    target_id   BIGINT NOT NULL,
    reason      VARCHAR(32) NOT NULL,
    detail      VARCHAR(500) NULL,
    status      VARCHAR(16) NOT NULL DEFAULT 'pending',
    handled_by  BIGINT NULL,
    handled_at  DATETIME NULL,
    created_at  DATETIME NOT NULL
);
CREATE INDEX idx_reports_status_created ON reports (status, created_at);

CREATE TABLE moderation_records (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    target_type    VARCHAR(16) NOT NULL,
    target_id      BIGINT NOT NULL,
    stage          VARCHAR(16) NOT NULL,
    model_version  VARCHAR(64) NULL,
    risk_labels    TEXT NULL,
    decision       VARCHAR(16) NOT NULL,
    operator_id    BIGINT NULL,
    note           VARCHAR(500) NULL,
    created_at     DATETIME NOT NULL
);
CREATE INDEX idx_moderation_target ON moderation_records (target_type, target_id, created_at);

CREATE TABLE moderation_words (
    id        BIGINT AUTO_INCREMENT PRIMARY KEY,
    word      VARCHAR(64) NOT NULL,
    category  VARCHAR(32) NOT NULL,
    action    VARCHAR(16) NOT NULL DEFAULT 'review',
    updated_at DATETIME NOT NULL
);
CREATE UNIQUE INDEX uk_moderation_words ON moderation_words (word);

CREATE TABLE audit_logs (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    actor_id   BIGINT NULL,
    action     VARCHAR(64) NOT NULL,
    target_type VARCHAR(16) NULL,
    target_id  BIGINT NULL,
    detail     TEXT NULL,
    created_at DATETIME NOT NULL
);
CREATE INDEX idx_audit_created ON audit_logs (created_at);

CREATE TABLE public_posts (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    original_question_id BIGINT NULL,
    title               VARCHAR(200) NOT NULL,
    question_body       TEXT NOT NULL,
    hr_answer           TEXT NOT NULL,
    anonymized          TINYINT NOT NULL DEFAULT 1,
    status              VARCHAR(32) NOT NULL DEFAULT 'pending_human',
    published_at        DATETIME NULL,
    created_at          DATETIME NOT NULL
);
