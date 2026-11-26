-- =====================================================================
-- 사용자 이름(name)을 닉네임(nickname)으로 변경하고, 단일 디바이스 로그인 정책을 문서화
-- =====================================================================

ALTER TABLE users RENAME COLUMN name TO nickname;

COMMENT ON COLUMN users.nickname IS '닉네임 (화면에서 사용자를 부르는 이름, 중복 허용)';

-- 정책 변경: 사용자당 1개 디바이스만 로그인 (새 로그인 시 기존 Refresh Token 전체 삭제)
COMMENT ON TABLE refresh_tokens IS 'Refresh Token. 사용자당 1개 디바이스만 로그인(새 로그인 시 기존 토큰 전체 삭제). Rotation 시 이전 토큰은 revoked_at 기록(재사용 감지용)';
