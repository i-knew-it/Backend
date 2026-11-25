# 아는 줄 알았어 — 백엔드 CLAUDE.md

> AI 기반 개발자 면접 학습 서비스. 매일 질문 1개에 답하고, AI 피드백과 꼬리 질문으로 개념을 내 말로 설명할 수 있게 돕는다.

---

## 작업 규칙 (Claude 답변·작업 방식)

1. **파일 수정과 명령 실행은 승인받고 진행한다.** 먼저 계획을 세워 보여주고 질문한 뒤, 사용자가 승인하면 작업한다. 파일 읽기와 검색은 승인 없이 해도 된다.
2. **항상 한국어로 답변한다.** 코드 주석과 커밋 메시지도 한국어로 쓴다. 단, 코드 식별자와 기술 용어는 원문 그대로 둔다.

---

## 기술 스택

| 항목 | 선택 |
|------|------|
| Language | Java 17 |
| Framework | Spring Boot 4.2.0 |
| DB | PostgreSQL (JSONB 활용) |
| ORM | Spring Data JPA |
| 인증 | JWT (Access Token 메모리, Refresh Token HttpOnly 쿠키) |
| AI | Spring AI + OpenAI GPT-4o mini |
| 알림 | FCM (Web Push), 이메일(AWS SES) |
| 배포 | AWS EC2 (무료 티어) |
| PK | ULID (`com.github.f4b6a3:ulid-creator`) |

---

## 패키지 구조

```
src/main/java/com/<패키지명>/
├── auth/           # 인증·JWT (가입, 로그인, 소셜, 토큰 갱신)
├── user/           # users, user_levels, user_streaks
├── question/       # questions, daily_question_assignments
├── answer/         # answers, answer_feedback_items, chat_messages
├── note/           # learning_notes
├── card/           # study_cards
├── notification/   # user_notification_settings, user_fcm_tokens
├── ai/             # ai_usage_logs, OpenAI 호출 공통
└── common/         # BaseEntity, 공통 예외, 응답 형식
```

---

## DB 테이블 목록 (13개)

| 도메인 | 테이블 | 설명 |
|--------|--------|------|
| 사용자 | users | 계정 기본 정보 |
| 사용자 | user_levels | 분야·수준 (1:N) |
| 사용자 | user_streaks | 연속 학습 (1:1) |
| 질문 | questions | 질문 풀 |
| 질문 | daily_question_assignments | 날짜별 배정 (멱등성 보장) |
| 답변 | answers | 답변 원문 + 상태 (`feedback_viewed_at` 컬럼 포함) |
| 답변 | answer_feedback_items | AI 피드백 항목 |
| 답변 | chat_messages | 꼬리 질문 대화 |
| 노트 | learning_notes | 자동 생성 마크다운 노트 |
| 카드 | study_cards | 스터디 카드 |
| 알림 | user_notification_settings | 알림 설정 |
| 알림 | user_fcm_tokens | FCM 토큰 (멀티 디바이스) |
| AI | ai_usage_logs | 토큰·비용 추적 |

### 핵심 설계 결정

- **ULID 기본키**: 시간순 정렬 + UUID 충돌 없음
- **daily_question_assignments 별도 테이블**: 멱등성 + 질문/답변 도메인 분리
- **user_id 직접 참조** (answers, notes): JOIN 없이 403 접근 제어
- **evaluation_criteria jsonb**: 채점 기준 유연 변경
- **concept_tag UNIQUE(user_id, concept_tag)**: 사용자당 같은 concept_tag 카드 1개 보장 + UPSERT
- **soft delete (deleted_at)**: 계정 복구 가능성 보존
- **ai_usage_logs 소프트 참조**: 답변 삭제 후도 비용 로그 보존

---

## API 명세 요약

**Base URL:** `https://api.anunjul.app` (경로 버전 접두사 `/v1` 없음)  
**인증:** `Authorization: Bearer {access_token}`  
**날짜:** ISO 8601  
**페이지네이션:** cursor 기반 (`?cursor=<ulid>&limit=20`, 불투명 커서)

### Access Token
- 저장: JS 메모리 (프론트)
- 만료: **15분**
- Refresh Token: HttpOnly Secure 쿠키, 만료 **30일**

### 엔드포인트

```
# 인증
POST   /auth/signup
POST   /auth/login
POST   /auth/social
POST   /auth/token/refresh
POST   /auth/logout
POST   /auth/verify-email

# 온보딩
GET    /onboarding/status
POST   /onboarding/complete

# 사용자
GET    /users/me
PATCH  /users/me/settings
DELETE /users/me

# 오늘의 질문
GET    /questions/today

# 답변 · AI 피드백
POST   /answers
GET    /answers/:answerId/feedback
POST   /answers/:answerId/feedback/retry
POST   /answers/:answerId/complete

# 꼬리 질문 채팅 (SSE 스트리밍)
GET    /answers/:answerId/chat
POST   /answers/:answerId/chat

# 학습 기록
GET    /history
GET    /history/:date
DELETE /history/:date

# 학습 노트
GET    /notes/:answerId
POST   /notes/:answerId/regenerate

# 스터디 카드
GET    /cards
PATCH  /cards/:cardId/status

# 알림
POST   /notifications/fcm-token
GET    /notifications/settings
PATCH  /notifications/settings

# Admin
GET    /admin/ai-usage
```

### Path parameter 규칙
- camelCase 사용: `:answerId`, `:cardId`

### 주요 에러 코드

| HTTP | code | 설명 |
|------|------|------|
| 400 | VALIDATION_ERROR | 요청 파라미터 오류 |
| 400 | ANSWER_TOO_SHORT | 답변 10자 미만 |
| 401 | TOKEN_EXPIRED | Access Token 만료 |
| 403 | FORBIDDEN | 타계정 리소스 접근 |
| 409 | EMAIL_ALREADY_EXISTS | 이메일 중복 |
| 409 | ALREADY_ANSWERED_TODAY | 오늘 이미 제출 |
| 422 | AI_ANALYSIS_FAILED | AI 분석 실패 (답변은 보존) |

---

## AI 연동 방식

- **라이브러리:** Spring AI (`spring-ai-openai-spring-boot-starter`)
- **모델:** `gpt-4o-mini`
- **Spring에서 직접 OpenAI API 호출**
- **AI 호출 실패 → 답변 저장/완료 처리에 영향 없음** (격리 필수)
- **AI에 PII 미전달**: 사용자 답변 텍스트만 전달, 이름·이메일 미포함
- **기능별 토큰·비용 로깅** (`ai_usage_logs`): 피드백 / 꼬리 질문 / 노트 생성 구분

---

## 프론트-백 협의 확정 사항

| 항목 | 결정 |
|------|------|
| Access Token 저장 | JS 메모리. Refresh Token은 HttpOnly 쿠키 |
| AI 피드백 수신 | 채팅: SSE 스트리밍. 피드백 판정 완료: SSE 이벤트 트리거 |
| 에러 메시지 | 서버가 `message` 필드 직접 제공 |
| 학습 완료 이탈 처리 | 세션 기반. `feedback_viewed_at` 저장 + 1분 주기 스케줄러 |
| 타임존 | 온보딩 때 저장, 설정에서 변경 가능 |
| 커서 페이지네이션 | 불투명 커서 (ULID 그대로, 프론트는 의미 해석 안 함) |

---

## 보안 규칙

- 비밀번호: bcrypt 해시 저장
- Refresh Token: HttpOnly Secure 쿠키
- HTTPS 필수
- 모든 개인 데이터 엔드포인트: 인증된 본인만 접근. 미인증/타계정 → 401/403
- **멱등성 보장**: 재시도·새로고침으로 답변 저장, 알림 발송, 카드 생성 중복 없음

---

## MVP 우선순위

- **P0 (Week 1~2):** 인증, 온보딩, 오늘의 질문, AI 피드백, 꼬리 질문, 알림
- **P1 (Week 3):** 학습 기록, 학습 노트, 스터디 카드
- **보류:** 돌발 면접 (Week 4 시간 여유 시)
- **Month 2:** 팀 베타 (팀 생성·초대·순위·채팅)

---

## 개발 주의사항

1. **답변은 AI 분석 전에 먼저 저장** (AI 실패해도 답변 보존)
2. **학습 노트·스터디 카드 업데이트는 비동기 처리** (사용자 응답 흐름 분리)
3. **오늘의 질문은 하루 고정** (`daily_question_assignments`로 멱등성 보장)
4. **같은 concept_tag 스터디 카드는 1개만** (UPSERT로 처리)
5. **알림 중복 발송 없음** (하루 1회 발송 보장)
6. **API 키는 환경변수로 관리** (코드에 직접 넣지 않음)
