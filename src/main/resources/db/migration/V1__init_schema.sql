-- =====================================================================
-- 아는 줄 알았어 - 초기 스키마 (PostgreSQL)
-- PK: ULID (char(26)), 타임스탬프: timestamptz, soft delete: deleted_at
-- =====================================================================

-- ---------------------------------------------------------------------
-- ENUM 타입
-- ---------------------------------------------------------------------
CREATE TYPE auth_provider    AS ENUM ('EMAIL', 'GOOGLE', 'KAKAO');
CREATE TYPE level_type       AS ENUM ('BEGINNER', 'ELEMENTARY', 'INTERMEDIATE');
CREATE TYPE question_source  AS ENUM ('PRESET', 'AI_GENERATED');
CREATE TYPE answer_status    AS ENUM ('SUBMITTED', 'FEEDBACK_READY', 'COMPLETED');
CREATE TYPE judgment_type    AS ENUM ('CORRECT', 'MISSING', 'WRONG', 'UNCERTAIN');
CREATE TYPE chat_role        AS ENUM ('USER', 'ASSISTANT');
CREATE TYPE note_status      AS ENUM ('PENDING', 'COMPLETED', 'FAILED');
CREATE TYPE review_status    AS ENUM ('DEFAULT', 'REVIEW_AGAIN', 'UNDERSTOOD');
CREATE TYPE ai_feature       AS ENUM ('FEEDBACK', 'CHAT', 'NOTE');

COMMENT ON TYPE auth_provider   IS '가입 방식';
COMMENT ON TYPE level_type      IS '분야별 수준 / 질문 난이도';
COMMENT ON TYPE question_source IS '질문 출처 (사전 등록 / AI 생성)';
COMMENT ON TYPE answer_status   IS '답변 진행 상태';
COMMENT ON TYPE judgment_type   IS 'AI 피드백 판정';
COMMENT ON TYPE chat_role       IS '꼬리 질문 대화 발화자';
COMMENT ON TYPE note_status     IS '학습 노트 생성 상태';
COMMENT ON TYPE review_status   IS '스터디 카드 복습 상태';
COMMENT ON TYPE ai_feature      IS 'AI 호출 기능 구분';

-- ---------------------------------------------------------------------
-- users
-- ---------------------------------------------------------------------
CREATE TABLE users (
    id                    char(26)      PRIMARY KEY,
    email                 varchar(255),
    password_hash         varchar(255),
    name                  varchar(100)  NOT NULL,
    provider              auth_provider NOT NULL,
    provider_id           varchar(255),
    timezone              varchar(50)   NOT NULL DEFAULT 'Asia/Seoul',
    onboarding_completed  boolean       NOT NULL DEFAULT false,
    deleted_at            timestamptz,
    created_at            timestamptz   NOT NULL DEFAULT now(),
    updated_at            timestamptz   NOT NULL DEFAULT now()
);

-- soft delete된 계정의 이메일로 재가입 가능하도록 부분 유니크 인덱스 사용
CREATE UNIQUE INDEX uq_users_email_active
    ON users (email) WHERE deleted_at IS NULL AND email IS NOT NULL;
CREATE UNIQUE INDEX uq_users_provider
    ON users (provider, provider_id) WHERE provider_id IS NOT NULL;

COMMENT ON TABLE  users IS '계정 기본 정보';
COMMENT ON COLUMN users.email IS '이메일 (소셜 전용 계정은 null 가능)';
COMMENT ON COLUMN users.password_hash IS 'bcrypt 해시 (소셜 계정은 null)';
COMMENT ON COLUMN users.provider IS '가입 방식';
COMMENT ON COLUMN users.provider_id IS '소셜 제공자 측 사용자 ID';
COMMENT ON COLUMN users.timezone IS 'IANA 타임존 (온보딩 시 저장)';
COMMENT ON COLUMN users.onboarding_completed IS '온보딩 완료 여부';
COMMENT ON COLUMN users.deleted_at IS 'soft delete 시각';

-- ---------------------------------------------------------------------
-- user_levels
-- ---------------------------------------------------------------------
CREATE TABLE user_levels (
    id           char(26)     PRIMARY KEY,
    user_id      char(26)     NOT NULL REFERENCES users (id),
    concept_tag  varchar(50)  NOT NULL,
    level        level_type   NOT NULL,
    created_at   timestamptz  NOT NULL DEFAULT now(),
    updated_at   timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT uq_user_levels_user_tag UNIQUE (user_id, concept_tag)
);

COMMENT ON TABLE  user_levels IS '사용자별 분야·수준 (1:N)';
COMMENT ON COLUMN user_levels.concept_tag IS '분야 태그 (OS, Network, DB, Java, Spring 등)';
COMMENT ON COLUMN user_levels.level IS '해당 분야 수준';

-- ---------------------------------------------------------------------
-- user_streaks
-- ---------------------------------------------------------------------
CREATE TABLE user_streaks (
    id                 char(26)     PRIMARY KEY,
    user_id            char(26)     NOT NULL UNIQUE REFERENCES users (id),
    current_streak     int          NOT NULL DEFAULT 0,
    longest_streak     int          NOT NULL DEFAULT 0,
    last_learned_date  date,
    created_at         timestamptz  NOT NULL DEFAULT now(),
    updated_at         timestamptz  NOT NULL DEFAULT now()
);

COMMENT ON TABLE  user_streaks IS '연속 학습 기록 (1:1)';
COMMENT ON COLUMN user_streaks.current_streak IS '현재 연속 학습 일수';
COMMENT ON COLUMN user_streaks.longest_streak IS '최장 연속 학습 일수';
COMMENT ON COLUMN user_streaks.last_learned_date IS '마지막 학습 완료일 (사용자 타임존 기준)';

-- ---------------------------------------------------------------------
-- questions
-- ---------------------------------------------------------------------
CREATE TABLE questions (
    id                   char(26)         PRIMARY KEY,
    concept_tag          varchar(50)      NOT NULL,
    difficulty           level_type       NOT NULL,
    source               question_source  NOT NULL,
    content              text             NOT NULL,
    evaluation_criteria  jsonb,
    created_at           timestamptz      NOT NULL DEFAULT now()
);

CREATE INDEX idx_questions_tag_difficulty ON questions (concept_tag, difficulty);

COMMENT ON TABLE  questions IS '질문 풀';
COMMENT ON COLUMN questions.concept_tag IS '분야 태그';
COMMENT ON COLUMN questions.difficulty IS '난이도';
COMMENT ON COLUMN questions.source IS '출처 (PRESET / AI_GENERATED)';
COMMENT ON COLUMN questions.evaluation_criteria IS '채점 기준 (jsonb, 유연 변경)';

-- ---------------------------------------------------------------------
-- daily_question_assignments
-- ---------------------------------------------------------------------
CREATE TABLE daily_question_assignments (
    id             char(26)     PRIMARY KEY,
    user_id        char(26)     NOT NULL REFERENCES users (id),
    question_id    char(26)     NOT NULL REFERENCES questions (id),
    assigned_date  date         NOT NULL,
    created_at     timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT uq_daily_assignment_user_date UNIQUE (user_id, assigned_date)
);

CREATE INDEX idx_daily_assignments_question ON daily_question_assignments (question_id);

COMMENT ON TABLE  daily_question_assignments IS '날짜별 질문 배정 (멱등성 보장)';
COMMENT ON COLUMN daily_question_assignments.assigned_date IS '배정일 (사용자 타임존 기준)';

-- ---------------------------------------------------------------------
-- answers
-- ---------------------------------------------------------------------
CREATE TABLE answers (
    id                 char(26)      PRIMARY KEY,
    user_id            char(26)      NOT NULL REFERENCES users (id),
    question_id        char(26)      NOT NULL REFERENCES questions (id),
    content            text          NOT NULL,
    status             answer_status NOT NULL DEFAULT 'SUBMITTED',
    feedback_viewed_at timestamptz,
    answered_date      date          NOT NULL,
    deleted_at         timestamptz,
    created_at         timestamptz   NOT NULL DEFAULT now(),
    updated_at         timestamptz   NOT NULL DEFAULT now()
);

-- 하루 1회 제출 보장 (ALREADY_ANSWERED_TODAY). soft delete된 답변은 제외
CREATE UNIQUE INDEX uq_answers_user_date_active
    ON answers (user_id, answered_date) WHERE deleted_at IS NULL;
CREATE INDEX idx_answers_user_date ON answers (user_id, answered_date DESC);
CREATE INDEX idx_answers_question ON answers (question_id);
-- 이탈 처리 스케줄러(1분 주기): 피드백 확인 후 미완료 답변 조회
CREATE INDEX idx_answers_pending_complete
    ON answers (feedback_viewed_at)
    WHERE status = 'FEEDBACK_READY' AND deleted_at IS NULL;

COMMENT ON TABLE  answers IS '답변 원문 + 상태';
COMMENT ON COLUMN answers.status IS 'SUBMITTED → FEEDBACK_READY → COMPLETED';
COMMENT ON COLUMN answers.feedback_viewed_at IS '피드백 최초 확인 시각 (이탈 시 자동 완료 처리 기준)';
COMMENT ON COLUMN answers.answered_date IS '답변일 (사용자 타임존 기준)';
COMMENT ON COLUMN answers.deleted_at IS 'soft delete 시각';

-- ---------------------------------------------------------------------
-- answer_feedback_items
-- ---------------------------------------------------------------------
CREATE TABLE answer_feedback_items (
    id           char(26)      PRIMARY KEY,
    answer_id    char(26)      NOT NULL REFERENCES answers (id),
    judgment     judgment_type NOT NULL,
    target_text  text,
    reason       text          NOT NULL,
    created_at   timestamptz   NOT NULL DEFAULT now()
);

CREATE INDEX idx_feedback_items_answer ON answer_feedback_items (answer_id, id);

COMMENT ON TABLE  answer_feedback_items IS 'AI 피드백 항목';
COMMENT ON COLUMN answer_feedback_items.judgment IS '판정 (CORRECT / MISSING / WRONG / UNCERTAIN)';
COMMENT ON COLUMN answer_feedback_items.target_text IS '사용자 답변 중 해당 부분 (MISSING은 null 가능)';
COMMENT ON COLUMN answer_feedback_items.reason IS '판정 이유';

-- ---------------------------------------------------------------------
-- chat_messages
-- ---------------------------------------------------------------------
CREATE TABLE chat_messages (
    id          char(26)     PRIMARY KEY,
    answer_id   char(26)     NOT NULL REFERENCES answers (id),
    role        chat_role    NOT NULL,
    content     text         NOT NULL,
    created_at  timestamptz  NOT NULL DEFAULT now()
);

-- ULID는 시간순 정렬 가능 → (answer_id, id)로 대화 순서 조회
CREATE INDEX idx_chat_messages_answer ON chat_messages (answer_id, id);

COMMENT ON TABLE  chat_messages IS '꼬리 질문 대화';
COMMENT ON COLUMN chat_messages.role IS '발화자 (USER / ASSISTANT)';

-- ---------------------------------------------------------------------
-- learning_notes
-- ---------------------------------------------------------------------
CREATE TABLE learning_notes (
    id          char(26)     PRIMARY KEY,
    answer_id   char(26)     NOT NULL UNIQUE REFERENCES answers (id),
    user_id     char(26)     NOT NULL REFERENCES users (id),
    content     text,
    status      note_status  NOT NULL DEFAULT 'PENDING',
    created_at  timestamptz  NOT NULL DEFAULT now(),
    updated_at  timestamptz  NOT NULL DEFAULT now()
);

CREATE INDEX idx_learning_notes_user ON learning_notes (user_id, id DESC);

COMMENT ON TABLE  learning_notes IS '자동 생성 마크다운 학습 노트';
COMMENT ON COLUMN learning_notes.user_id IS 'JOIN 없이 403 접근 제어를 하기 위한 직접 참조';
COMMENT ON COLUMN learning_notes.content IS '마크다운 (생성 전 PENDING 상태에서는 null)';
COMMENT ON COLUMN learning_notes.status IS 'PENDING / COMPLETED / FAILED';

-- ---------------------------------------------------------------------
-- study_cards
-- ---------------------------------------------------------------------
CREATE TABLE study_cards (
    id             char(26)       PRIMARY KEY,
    user_id        char(26)       NOT NULL REFERENCES users (id),
    concept_tag    varchar(50)    NOT NULL,
    front          text           NOT NULL,
    back           text           NOT NULL,
    review_status  review_status  NOT NULL DEFAULT 'DEFAULT',
    last_error_at  timestamptz,
    created_at     timestamptz    NOT NULL DEFAULT now(),
    updated_at     timestamptz    NOT NULL DEFAULT now(),
    CONSTRAINT uq_study_cards_user_tag UNIQUE (user_id, concept_tag)
);

CREATE INDEX idx_study_cards_user_status ON study_cards (user_id, review_status);

COMMENT ON TABLE  study_cards IS '스터디 카드 (같은 concept_tag는 사용자당 1개, UPSERT)';
COMMENT ON COLUMN study_cards.front IS '질문';
COMMENT ON COLUMN study_cards.back IS '핵심 답변 + 빠뜨렸던 조건';
COMMENT ON COLUMN study_cards.review_status IS 'DEFAULT / REVIEW_AGAIN / UNDERSTOOD';
COMMENT ON COLUMN study_cards.last_error_at IS '마지막으로 틀리거나 빠뜨린 시각';

-- ---------------------------------------------------------------------
-- user_notification_settings
-- ---------------------------------------------------------------------
CREATE TABLE user_notification_settings (
    id          char(26)     PRIMARY KEY,
    user_id     char(26)     NOT NULL UNIQUE REFERENCES users (id),
    enabled     boolean      NOT NULL DEFAULT true,
    notify_at   time         NOT NULL,
    created_at  timestamptz  NOT NULL DEFAULT now(),
    updated_at  timestamptz  NOT NULL DEFAULT now()
);

CREATE INDEX idx_notification_settings_enabled_time
    ON user_notification_settings (notify_at) WHERE enabled;

COMMENT ON TABLE  user_notification_settings IS '알림 설정';
COMMENT ON COLUMN user_notification_settings.enabled IS '알림 사용 여부';
COMMENT ON COLUMN user_notification_settings.notify_at IS '알림 시각 (사용자 로컬 시간 기준)';

-- ---------------------------------------------------------------------
-- user_fcm_tokens
-- ---------------------------------------------------------------------
CREATE TABLE user_fcm_tokens (
    id           char(26)      PRIMARY KEY,
    user_id      char(26)      NOT NULL REFERENCES users (id),
    token        varchar(512)  NOT NULL UNIQUE,
    device_info  varchar(255),
    created_at   timestamptz   NOT NULL DEFAULT now(),
    updated_at   timestamptz   NOT NULL DEFAULT now()
);

CREATE INDEX idx_fcm_tokens_user ON user_fcm_tokens (user_id);

COMMENT ON TABLE  user_fcm_tokens IS 'FCM 토큰 (멀티 디바이스)';
COMMENT ON COLUMN user_fcm_tokens.token IS 'FCM 등록 토큰';
COMMENT ON COLUMN user_fcm_tokens.device_info IS '기기 정보';

-- ---------------------------------------------------------------------
-- ai_usage_logs
-- ---------------------------------------------------------------------
CREATE TABLE ai_usage_logs (
    id                  char(26)       PRIMARY KEY,
    answer_id           char(26),
    feature             ai_feature     NOT NULL,
    prompt_tokens       int            NOT NULL DEFAULT 0,
    completion_tokens   int            NOT NULL DEFAULT 0,
    estimated_cost_usd  numeric(10, 6) NOT NULL DEFAULT 0,
    created_at          timestamptz    NOT NULL DEFAULT now()
);

CREATE INDEX idx_ai_usage_logs_created ON ai_usage_logs (created_at);
CREATE INDEX idx_ai_usage_logs_answer ON ai_usage_logs (answer_id);

COMMENT ON TABLE  ai_usage_logs IS 'AI 토큰·비용 추적';
COMMENT ON COLUMN ai_usage_logs.answer_id IS '소프트 참조 (FK 없음) - 답변 삭제 후에도 비용 로그 보존';
COMMENT ON COLUMN ai_usage_logs.feature IS 'FEEDBACK / CHAT / NOTE';
COMMENT ON COLUMN ai_usage_logs.estimated_cost_usd IS '추정 비용 (USD)';
