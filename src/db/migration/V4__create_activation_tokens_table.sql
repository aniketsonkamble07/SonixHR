-- Create activation_tokens table
CREATE TABLE IF NOT EXISTS activation_tokens (
    id BIGSERIAL PRIMARY KEY,
    token VARCHAR(255) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    expiry_date TIMESTAMP NOT NULL,
    used BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_activation_tokens_user
        FOREIGN KEY (user_id)
        REFERENCES platform_users(id)
        ON DELETE CASCADE
);

-- Create indexes
CREATE INDEX idx_activation_tokens_token ON activation_tokens(token);
CREATE INDEX idx_activation_tokens_user_id ON activation_tokens(user_id);
CREATE INDEX idx_activation_tokens_expiry ON activation_tokens(expiry_date);