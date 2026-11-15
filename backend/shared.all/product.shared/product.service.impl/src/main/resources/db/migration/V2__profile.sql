-- V2: 可选完善资料（学历信息 + 实习/项目经历）
ALTER TABLE users ADD COLUMN degree VARCHAR(32) NULL;
ALTER TABLE users ADD COLUMN graduation_year VARCHAR(16) NULL;

CREATE TABLE user_experiences (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT NOT NULL,
    type        VARCHAR(16) NOT NULL,
    title       VARCHAR(128) NOT NULL,
    role        VARCHAR(64) NULL,
    start_date  VARCHAR(16) NULL,
    end_date    VARCHAR(16) NULL,
    description TEXT NULL,
    created_at  DATETIME NOT NULL,
    updated_at  DATETIME NOT NULL
);
CREATE INDEX idx_user_experiences_user ON user_experiences (user_id);
