# QA Manager

팀 내부 QA(품질보증) 관리 도구. 프로젝트 → 업데이트(버전) → QA 항목을 체계적으로 관리하고,
담당자 배정 · 우선순위 · 코멘트 · 변경 이력 · 실시간 알림으로 협업하는 웹 서비스.

---

## 기술 스택

| 영역 | 스택 |
|---|---|
| Frontend | Nuxt 4 (SSR), Vue 3, Pinia, Tailwind CSS |
| Backend | Spring Boot 4, Java 25, Hibernate 7, Spring Security |
| DB | MariaDB 11 + Flyway 마이그레이션 |
| Auth | JWT (access + refresh) |
| Storage | AWS S3 (브라우저 직접 업로드, presigned URL) |
| 실시간 | SSE (Server-Sent Events) |
| 배포 | Docker Compose (풀스택) / systemd / pm2 |

---

## 주요 기능

### 프로젝트 & QA
- 프로젝트 CRUD, 상태(`active` / `paused` / `completed`), 고정
- 프로젝트별 업데이트(릴리즈 버전) 관리
- QA 아이템 CRUD, 상태/우선순위/담당자/이미지 첨부
- 필드 변경 히스토리 자동 기록

### 코멘트
- 루트 코멘트 + 답글 (1-depth 스레드)
- 본인 코멘트 인라인 수정/삭제
- 이미지 첨부 (S3 업로드) + Lightbox(휠 줌, 드래그 이동)
- `@` 멘션 자동완성 (방향키/Enter)
- 이모지 반응 (8종, toggle)

### 알림 (SSE 실시간)
| 조건 | 수신자 |
|---|---|
| 새 QA 가 본인에게 배정될 때 | 새 담당자 |
| 기존 QA 의 담당자가 본인으로 변경될 때 | 새 담당자 |
| 담당 QA 에 코멘트가 달릴 때 | QA 담당자 |
| 본인 코멘트에 답글이 달릴 때 | 부모 코멘트 작성자 |
| QA 상태가 변경될 때 | QA 담당자 |

자기 자신이 발생시킨 이벤트는 알림 발송 X.

### 인증 & 프로필
- JWT 로그인 / refresh / logout
- 헤더에서 아바타 클릭 → 프로필 모달 (이름 · 비밀번호 변경 · 아바타 업로드)
- 비밀번호 변경 시 현재 비밀번호 검증

### Admin 페이지
4 탭 구조: **프로젝트 관리** / **QA 관리** / **팀원 관리** / **설정**
- Stats 카드 4개 (전체 프로젝트, 전체 QA, 진행중 프로젝트, 긴급 QA)
- 셀렉트박스로 상태 즉시 변경
- 팀원 추가/수정 통합 모달, 삭제 확인 모달
- 설정 탭은 알림/MS Teams UI mock (백엔드 연동 예정)

---

## 디렉토리 구조

```
qa-manager/
├── frontend/             # Nuxt 4 (port 3000)
│   ├── app/
│   │   ├── components/   # base, feature
│   │   ├── composables/  # useApi, useQa, useProjects, …
│   │   ├── pages/        # 라우팅 (auth, admin, project, qa, index)
│   │   ├── stores/       # auth, notifications (Pinia)
│   │   └── types/        # 백엔드 DTO 1:1 매핑 타입
│   └── Dockerfile
├── backend/              # Spring Boot 4 (port 8080)
│   ├── src/main/java/com/qamanager/
│   │   ├── auth/         # JWT, /api/me
│   │   ├── member/       # 팀원
│   │   ├── project/
│   │   ├── projectupdate/
│   │   ├── qa/
│   │   │   ├── item/     # QaItem, History, DTO
│   │   │   ├── comment/  # QaComment, Reaction, DTO
│   │   │   └── shared/   # QaStatus, QaPriority
│   │   ├── notification/ # SSE + Event 리스너
│   │   ├── file/         # S3 presigned
│   │   ├── config/       # Security, JPA
│   │   └── common/       # BaseEntity, ApiException 등
│   ├── src/main/resources/
│   │   ├── application.yml
│   │   └── db/migration/ # Flyway
│   └── Dockerfile
├── docs/
│   └── DEPLOYMENT.md     # 배포 가이드
├── docker-compose.yml    # 풀스택 (DB + BE + FE)
├── .env.example
└── README.md
```

---

## 빠른 시작

### 옵션 A: Docker Compose (권장)

```bash
cp .env.example .env       # JWT_SECRET, DB_PASSWORD, AWS 키 등 채우기
docker compose up -d --build
```

- 프론트엔드: `http://localhost:3000`
- 백엔드: `http://localhost:8080`
- API 문서: `http://localhost:8080/swagger-ui.html`

상세는 [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md) 참고.

### 옵션 B: 호스트에서 직접 실행

사전 준비:
- JDK 25 (Corretto / OpenJDK)
- Node ≥ 22.12
- 접근 가능한 MariaDB 11
- AWS S3 버킷 + IAM 키

```bash
# 1. 환경변수
cp .env.example .env       # DB_HOST=localhost 등 조정

# 2. 백엔드
cd backend
./gradlew bootRun

# 3. 프론트엔드 (별도 터미널)
cd frontend
npm install
npm run dev
```

---

## 환경변수

`.env.example` 참고. 주요 항목:

| 키 | 비고 |
|---|---|
| `DB_HOST` `DB_PORT` `DB_NAME` `DB_USER` `DB_PASSWORD` | MariaDB 접속 |
| `DB_ROOT_PASSWORD` | Docker Compose 전용 (mariadb 컨테이너 root 비밀번호) |
| `JWT_SECRET` | **64자 이상** 랜덤. `openssl rand -hex 48` 추천 |
| `AWS_REGION` `AWS_S3_BUCKET` `AWS_ACCESS_KEY_ID` `AWS_SECRET_ACCESS_KEY` | S3 업로드 |
| `CORS_ALLOWED_ORIGINS` | 프론트 도메인 (콤마 구분) |
| `NUXT_PUBLIC_API_BASE` | **브라우저에서 접근 가능한** 백엔드 URL |

> ⚠️ `.env` 는 절대 깃에 커밋하지 말 것. 운영/스테이징/로컬 모두 다른 `JWT_SECRET` 사용 권장.

---

## API 문서

백엔드 실행 후 접근 가능:
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`
- Health: `http://localhost:8080/actuator/health`

---

## 개발 메모

- **DB 마이그레이션**: Flyway 가 백엔드 부팅 시 자동 실행. 마이그레이션 파일은 `backend/src/main/resources/db/migration/`.
- **JPA `ddl-auto: validate`**: 엔티티와 스키마 불일치 시 부팅 실패. 스키마 변경은 반드시 Flyway 마이그레이션으로.
- **알림 SSE**: 브라우저 fetch + ReadableStream 으로 구독. `EventSource` 가 헤더를 못 보내는 한계 회피.
- **이미지 업로드**: 프론트 → 백엔드(`POST /api/files/presigned`) → S3 PUT 직접. 백엔드는 메타만 다룸.

배포 / 트러블슈팅 / 백업 등 운영 정보는 [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md) 참고.
