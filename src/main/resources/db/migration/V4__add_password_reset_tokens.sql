-- =====================================================================
-- 비밀번호 재설정 토큰 (1회용, 짧은 만료)
-- 토큰 원문은 저장하지 않고 SHA-256 해시(hex 64자)만 저장
-- =====================================================================

CREATE TABLE password_reset_tokens (
    id          char(26)     PRIMARY KEY,
    user_id     char(26)     NOT NULL REFERENCES users (id),
    token_hash  char(64)     NOT NULL UNIQUE,
    expires_at  timestamptz  NOT NULL,
    used_at     timestamptz,
    created_at  timestamptz  NOT NULL DEFAULT now()
);

CREATE INDEX idx_password_reset_tokens_user    ON password_reset_tokens (user_id);
CREATE INDEX idx_password_reset_tokens_expires ON password_reset_tokens (expires_at);

COMMENT ON TABLE  password_reset_tokens IS '비밀번호 재설정 토큰. 사용자당 유효한 토큰은 1개(새로 요청하면 이전 토큰 삭제), 1회 사용 후 used_at 기록';
COMMENT ON COLUMN password_reset_tokens.token_hash IS '재설정 토큰의 SHA-256 해시(hex). 원문은 저장하지 않음 (메일 링크에만 존재)';
COMMENT ON COLUMN password_reset_tokens.expires_at IS '만료 시각 (발급 후 30분)';
COMMENT ON COLUMN password_reset_tokens.used_at IS '사용된 시각. 값이 있으면 다시 사용할 수 없음';
