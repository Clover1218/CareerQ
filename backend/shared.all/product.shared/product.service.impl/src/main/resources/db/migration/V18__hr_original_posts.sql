-- Independent original articles. Existing legacy posts remain unused.
CREATE TABLE hr_posts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    author_id BIGINT NOT NULL,
    client_key VARCHAR(64) NOT NULL,
    title VARCHAR(200) NOT NULL DEFAULT '',
    body TEXT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'draft',
    version BIGINT NOT NULL DEFAULT 1,
    submitted_at DATETIME NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL
);
CREATE UNIQUE INDEX uk_hr_posts_draft_key ON hr_posts (author_id, client_key);
CREATE INDEX idx_hr_posts_author ON hr_posts (author_id, id);
CREATE INDEX idx_hr_posts_author_status ON hr_posts (author_id, status, id);
CREATE INDEX idx_hr_posts_status ON hr_posts (status, id);
CREATE TABLE hr_post_tags (
    post_id BIGINT NOT NULL,
    tag_id BIGINT NOT NULL,
    PRIMARY KEY (post_id, tag_id)
);
