# REST API 레퍼런스

실행 중인 백엔드에서는 살아있는 문서를 제공합니다:

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`
- Health: `http://localhost:8080/actuator/health`

> Docker Compose 기본 포트는 `8357` 입니다 (`http://localhost:8357/swagger-ui.html`).

## 인증 정책

- 인증: JWT — **HttpOnly 쿠키**(`qam_access_token` / `qam_refresh_token`) 우선, `Authorization: Bearer` 헤더 병행 지원
- 인증 없이 접근 가능(permitAll): `/api/ping`, `/api/auth/login`, `/api/auth/login/otp/**`, `/api/auth/refresh`, `/api/teams/messages`(봇 JWT 자체 검증), `/actuator/health`, Swagger 3종
- 그 외 전 엔드포인트 인증 필요. 세션리스(STATELESS), CSRF 비활성

## 인증 / 내 정보

| 메서드 | 경로 | 설명 |
|---|---|---|
| POST | `/api/auth/login` | 1단계 로그인 — 신뢰 IP 면 즉시 토큰, 아니면 `{otpRequired, challengeId, maskedEmail}` |
| POST | `/api/auth/login/otp/verify` | OTP 코드 검증 → 토큰 발급 (실패 시 `remainingAttempts`) |
| POST | `/api/auth/login/otp/resend` | OTP 재발송 (60초 쿨다운) |
| POST | `/api/auth/refresh` | 리프레시 회전 — 이전 refresh 즉시 블랙리스트 |
| POST | `/api/auth/logout` | access/refresh 블랙리스트 + 쿠키 삭제 |
| GET / PATCH | `/api/me` | 내 프로필 조회 / 이름·아바타 수정 |
| POST | `/api/me/password` | 비밀번호 변경 (현재 비밀번호 확인) |
| GET / PUT | `/api/me/notification-settings` | 알림 종류별 토글 + 방해금지 시간대 |

## 프로젝트

| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/api/projects?status=` | 목록 (핀 우선 정렬) |
| GET | `/api/projects/{id}` | 상세 (연결된 GitHub repo 포함) |
| POST / PATCH / DELETE | `/api/projects`, `/{id}` | CRUD (`githubRepos` 배열은 전체 교체 방식) |
| POST | `/api/projects/{id}/pin` | 핀 토글 → `{pinned}` |

## 업데이트(버전)

| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/api/projects/{projectId}/updates` | 프로젝트별 목록 (수동 정렬 순) |
| GET | `/api/updates` | 전체 목록 |
| POST | `/api/projects/{projectId}/updates` | 생성 (맨 위 배치) |
| PUT | `/api/projects/{projectId}/updates/order` | 드래그 재정렬 (전체 id 배열) |
| GET / PATCH / DELETE | `/api/updates/{id}` | 상세 / 수정 / 삭제 |

## QA 항목

| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/api/qa` | 목록 — 필터: `updateId, status, priority, assigneeId, testerId` |
| GET | `/api/qa/page` | 서버 페이징 (size 10/50/100) |
| GET | `/api/qa/dashboard-stats?mine=` | 대시보드 집계 (상태별/긴급/프로젝트별) |
| GET / POST / PATCH / DELETE | `/api/qa`, `/{id}` | CRUD (생성 시 `createGithubIssue` 옵션) |
| GET | `/api/qa/{id}/history` | 필드 단위 변경 이력 |

## 코멘트

| 메서드 | 경로 | 설명 |
|---|---|---|
| GET / POST | `/api/qa/{qaId}/comments` | 목록 / 작성 (`parentId` 답글, `mentionedMemberIds` 멘션) |
| PATCH / DELETE | `/api/comments/{id}` | 본인 코멘트만 수정/삭제 |
| POST | `/api/comments/{id}/reactions` | 이모지 반응 토글 |

## 팀원

| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/api/members` | 목록 (한국어 가나다순) |
| GET / POST / PATCH / DELETE | `/api/members`, `/{id}` | CRUD (삭제는 소프트 삭제) |
| POST | `/api/members/{id}/reset-password` | 관리자 비밀번호 초기화 |
| PUT | `/api/members/{id}/email` | 본인 이메일 등록/변경 (+ AAD 매핑 시도) |
| PUT | `/api/members/{id}/teams-notify` | Teams 수신 마스터 토글 |
| POST | `/api/members/{id}/teams-test` | Teams 발송 6단계 진단 + 테스트 메시지 |

## 알림

| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/api/notifications` | 내 알림 목록 |
| GET | `/api/notifications/unread-count` | 안읽음 개수 |
| PATCH | `/api/notifications/{id}/read` | 읽음 처리 |
| PATCH | `/api/notifications/read-all` | 전체 읽음 |
| GET | `/api/notifications/stream` | **SSE 구독** (`text/event-stream`, 타임아웃 30분) |

## 파일

| 메서드 | 경로 | 설명 |
|---|---|---|
| POST | `/api/files/presigned` | S3 presigned PUT URL 발급 — `{key, uploadUrl, publicUrl, expiresInSeconds}` |

purpose: `qa_image` · `comment_image` · `avatar`. 이미지 5종 + PDF(아바타 제외), 기본 100MB 제한.

## GitHub 연동

| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/api/github/app` | 연동 상태 (`configured`, `appSlug`, `installUrl`) |
| POST | `/api/github/app/manifest` | Manifest + GitHub 앱 생성 targetUrl 반환 |
| POST | `/api/github/app/conversion` | GitHub `code` → 앱 자격증명 교환·저장 |
| DELETE | `/api/github/app` | 연동 해제 |
| GET | `/api/github/repos` | 설치된 repo 목록 |
| GET | `/api/github/qa/{qaId}/commits` | 연결 이슈를 참조한 커밋 목록 |

## 기타

| 메서드 | 경로 | 설명 |
|---|---|---|
| POST | `/api/teams/messages` | Teams Bot Framework 웹훅 (봇 JWT 서명 검증) |
| GET | `/api/ping` | 헬스체크 |
