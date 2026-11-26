-- =====================================================================
-- Refresh Token 저장 (멀티 디바이스, Rotation, 재사용 감지)
-- 토큰 원문은 저장하지 않고 SHA-256 해시(hex 64자)만 저장
-- =====================================================================

CREATE TABLE refresh_tokens (
    id          char(26)     PRIMARY KEY,
    user_id     char(26)     NOT NULL REFERENCES users (id),
    token_hash  char(64)     NOT NULL UNIQUE,
    expires_at  timestamptz  NOT NULL,
    revoked_at  timestamptz,
    created_at  timestamptz  NOT NULL DEFAULT now()
);

CREATE INDEX idx_refresh_tokens_user    ON refresh_tokens (user_id);
CREATE INDEX idx_refresh_tokens_expires ON refresh_tokens (expires_at);

COMMENT ON TABLE  refresh_tokens IS 'Refresh Token (디바이스별 1행, Rotation 시 이전 토큰은 revoked_at 기록)';
COMMENT ON COLUMN refresh_tokens.token_hash IS 'Refresh Token의 SHA-256 해시 (hex). 원문은 저장하지 않음';
COMMENT ON COLUMN refresh_tokens.expires_at IS '만료 시각';
COMMENT ON COLUMN refresh_tokens.revoked_at IS 'Rotation으로 폐기된 시각. 폐기된 토큰이 다시 사용되면 탈취로 보고 해당 사용자의 모든 토큰 삭제';
