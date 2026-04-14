CREATE TABLE IF NOT EXISTS tags (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    CONSTRAINT uk_tags_name UNIQUE (name)
);

CREATE TABLE IF NOT EXISTS post_tags (
    id BIGSERIAL PRIMARY KEY,
    post_id BIGINT NOT NULL,
    tag_id BIGINT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_post_tag_post_id ON post_tags(post_id);
