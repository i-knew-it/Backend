# 아는 줄 알았어 — 백엔드 CLAUDE.md

> AI 기반 개발자 면접 학습 서비스. 매일 질문 1개에 답하고, AI 피드백과 꼬리 질문으로 개념을 내 말로 설명할 수 있게 돕는다.

---

## 작업 규칙 (Claude 답변·작업 방식)

1. **파일 수정과 명령 실행은 승인받고 진행한다.** 먼저 계획을 세워 보여주고 질문한 뒤, 사용자가 승인하면 작업한다. 파일 읽기와 검색은 승인 없이 해도 된다.
2. **항상 한국어로 답변한다.** 코드 주석과 커밋 메시지도 한국어로 쓴다. 단, 코드 식별자와 기술 용어는 원문 그대로 둔다.

---

## Git 규칙 (브랜치·커밋·PR)

프론트엔드·백엔드가 같은 형식으로 작성하기 위한 팀 규칙. Claude가 git 명령어나 커밋 메시지, PR 본문을 안내할 때 항상 이 규칙을 따른다.

### 브랜치 전략

- `main`: 운영 배포 브랜치. 직접 커밋하지 않음
- `develop`: 개발 통합 브랜치. **모든 작업 브랜치는 여기서 따고 여기로 PR**
- 작업 브랜치: `타입/작업명` (예: `feat/auth`, `chore/coderabbit`)

흐름: `작업 브랜치` → PR → `develop` → 배포 시점에 `main`

### 타입

| 타입 | 용도 |
| --- | --- |
| feat | 새 기능 |
| fix | 버그 수정 |
| refactor | 동작 변경 없는 코드 개선 |
| style | UI, 스타일 변경 |
| chore | 설정, 패키지, 빌드 등 |
| docs | 문서 |

### 커밋 메시지

```
타입: 무엇을 했는지
```

예: `feat: Zustand 인증 상태 저장소 도입` (한국어로 작성, 코드 식별자·기술 용어는 원문 유지)

### PR

- **제목**: 커밋 메시지와 같은 형식. 예: `feat: 이메일 로그인 및 토큰 재발급 구현`
- **base 브랜치**: `develop` (GitHub 기본값이 `main`이면 직접 바꿀 것)
- **라벨**: 제목의 타입과 같은 이름의 라벨을 붙임 (`feat`, `fix`, `refactor`, `style`, `chore`, `docs`)
- **설명**:

```markdown
## 작업 내용
-

## 관련 일정
노션 Schedule 링크

## 참고 사항
(선택) 리뷰 시 봐줬으면 하는 부분, 스크린샷 등
```

### 리뷰

- CodeRabbit이 `develop` 대상 PR을 자동 리뷰함 (Frontend 저장소)

### Claude 작업 시 유의

- 커밋·푸시·PR 생성은 사용자가 직접 한다. Claude는 명령어와 PR 본문 초안만 안내한다.
- PR 본문 초안의 "관련 일정"은 노션 링크를 Claude가 알 수 없으므로 비워 둔다.

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

## DB 테이블 목록 (15개)

| 도메인 | 테이블 | 설명 |
|--------|--------|------|
| 사용자 | users | 계정 기본 정보 |
| 사용자 | user_levels | 분야·수준 (1:N) |
| 사용자 | user_streaks | 연속 학습 (1:1) |
| 인증 | refresh_tokens | Refresh Token 해시 (사용자당 1개 디바이스, Rotation) |
| 인증 | password_reset_tokens | 비밀번호 재설정 토큰 해시 (1회용, 30분, 사용자당 1개) |
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
- 만료: **30분**
- Refresh Token: HttpOnly Secure SameSite=Strict 쿠키(Path=/auth), 만료 **7일**. DB에는 SHA-256 해시만 저장
- **단일 디바이스 로그인**: 새 로그인(가입·이메일·소셜)이 성공하면 해당 사용자의 기존 Refresh Token을 모두 삭제한다. 이전 기기는 Access Token이 만료되어 갱신을 시도할 때(최대 30분 뒤) 로그아웃된다
- **Rotation**: 갱신 때마다 Refresh Token 교체. 폐기된 토큰이 재사용되면 탈취로 보고 사용자 전체 토큰 삭제. 단, 교체 직후 10초 안의 재사용은 동시 요청(여러 탭 등)으로 보고 허용
- 갱신 실패(TOKEN_EXPIRED/TOKEN_INVALID) 시 서버가 Refresh 쿠키를 만료(Max-Age=0)시킨다. 만료된 토큰 행은 매일 03:00(KST) 스케줄러가 삭제

### 엔드포인트

```
# 인증
POST   /auth/signup
POST   /auth/login
GET    /auth/oauth2/authorize/{provider}   # 소셜 로그인 시작 (현재 google만, 카카오 예정)
GET    /auth/oauth2/callback/{provider}    # 소셜 로그인 콜백 (Google이 호출, 프론트가 직접 호출하지 않음)
POST   /auth/token/refresh
POST   /auth/logout
POST   /auth/password/forgot               # 비밀번호 재설정 메일 요청 (가입 여부와 무관하게 항상 200)
POST   /auth/password/reset                # 메일 링크의 token으로 새 비밀번호 설정
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
| 400 | INVALID_RESET_TOKEN | 비밀번호 재설정 링크가 만료·이미 사용·존재하지 않음 (셋을 구분하지 않음) |
| 401 | TOKEN_EXPIRED | Access/Refresh Token 만료 |
| 401 | TOKEN_INVALID | 위조·형식 오류·용도 불일치·폐기된 토큰 |
| 401 | INVALID_CREDENTIALS | 이메일 없음 또는 비밀번호 불일치 |
| 401 | UNAUTHORIZED | 토큰 없이 인증 필요 경로 접근 |
| 429 | TOO_MANY_REQUESTS | 로그인 연속 실패(5회) 또는 IP당 분당 20회 초과. `Retry-After` 헤더(차단이 풀릴 때까지 남은 시간, 초, 올림) 제공 |
| 403 | FORBIDDEN | 타계정 리소스 접근 |
| 409 | EMAIL_ALREADY_EXISTS | 이메일 중복 |
| 409 | ALREADY_ANSWERED_TODAY | 오늘 이미 제출 |
| 422 | AI_ANALYSIS_FAILED | AI 분석 실패 (답변은 보존) |

에러 응답 형식: `{ "error": { "code": "ERROR_CODE", "message": "사용자에게 보여줄 메시지" } }`

소셜 로그인 실패는 JSON이 아니라 프론트 `/login?error=코드`로 리다이렉트하며, 코드는 `EMAIL_ALREADY_EXISTS`(같은 이메일의 일반 계정 존재, 자동 연동 안 함), `EMAIL_NOT_VERIFIED`(이메일 미인증 소셜 계정), `OAUTH_FAILED`(취소·기타 실패) 중 하나다.

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
| 소셜 로그인 | 프론트가 `GET /auth/oauth2/authorize/google`로 이동 → 성공 시 서버가 Refresh 쿠키만 세팅하고 `{FRONTEND_URL}/auth/callback`으로 리다이렉트(토큰은 URL에 싣지 않음) → 프론트가 `POST /auth/token/refresh`로 Access Token 수령 → `GET /users/me`로 사용자 정보 조회(새로고침 후 복구도 같은 순서: refresh → me). 실패 시 `{FRONTEND_URL}/login?error=코드` |
| 회원가입 | 이메일·비밀번호·비밀번호 확인·닉네임. 닉네임 2~20자(한글·영문·숫자·`_`), 중복 허용. 비밀번호 8~72자, 영문 대·소문자·숫자·특수문자 각 1개 이상, 공백 불가 |
| 비밀번호 재설정 | 프론트가 `POST /auth/password/forgot {email}` (항상 200, 가입 여부를 알려주지 않음) → 메일의 링크 `{FRONTEND_URL}/reset-password?token=...`(30분, 1회용) → 프론트의 해당 페이지가 `POST /auth/password/reset {token, newPassword, newPasswordConfirm}` 호출 → 성공하면 모든 기기가 로그아웃되므로 로그인 화면으로 이동. 같은 이메일은 1시간 3회까지 요청 가능 |
| 쿠키 인증 | 프론트 요청에 `credentials: 'include'` 필요. SameSite=Strict라 프론트와 API는 같은 사이트(예: app.도메인, api.도메인)여야 함. CORS 허용 origin은 환경변수 `FRONTEND_URL` |

---

## 보안 규칙

- 비밀번호: bcrypt 해시 저장
- 무차별 대입 방어: 같은 이메일+IP 로그인 5회 연속 실패 시 15분 차단, IP당 분당 20회 제한(로그인·가입 합산). 서버 메모리 기반이라 재시작 시 초기화되고 서버를 여러 대로 늘리면 Redis 등으로 교체 필요. Nginx 뒤에서는 실제 클라이언트 IP를 받도록 `server.forward-headers-strategy` 설정 필요
- 로그인 실패 응답은 이메일 존재 여부와 무관하게 동일(INVALID_CREDENTIALS)
- 비밀번호 재설정: 토큰은 SHA-256 해시만 저장하고 1회용·30분이며, 새로 요청하면 이전 링크는 무효. 요청 응답과 횟수 제한(같은 이메일 1시간 3회)은 가입 여부와 무관하게 동일해야 한다. 재설정에 성공하면 해당 사용자의 모든 Refresh Token을 삭제한다. 메일은 SMTP(로컬 Mailpit, 운영 AWS SES SMTP)로 트랜잭션 커밋 후 비동기 발송하며, 로그에 이메일·토큰을 남기지 않는다
- 소셜 로그인은 이메일이 인증된(email_verified) 계정만 허용하고, 같은 이메일의 일반 계정과 자동 연동하지 않는다
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
7. **같은 사용자의 인증 작업은 사용자 행을 먼저 잠근다**: 로그인, 비밀번호 재설정, 재설정 메일 요청은 `SELECT ... FOR UPDATE`(`findByEmailForUpdate`, `findByIdForUpdate`)로 사용자 행을 잠근 뒤 토큰 테이블을 건드린다. 잠금 순서는 항상 "사용자 행 → 토큰 행"이어야 서로 기다리는 교착이 생기지 않는다. (Hibernate는 PostgreSQL에서 `for no key update`를 써서 외래키 검사와 충돌하지 않으므로, 사용자 행을 잠그지 않는 `refresh`와도 교착이 없음을 테스트로 확인했다)
