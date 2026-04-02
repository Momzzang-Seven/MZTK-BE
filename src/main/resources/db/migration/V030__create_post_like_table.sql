CREATE TABLE post_like (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    target_type VARCHAR(20) NOT NULL,
    target_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_post_like_target_user UNIQUE (target_type, target_id, user_id)
);

CREATE INDEX idx_post_like_target ON post_like (target_type, target_id);
CREATE INDEX idx_post_like_user ON post_like (user_id);
