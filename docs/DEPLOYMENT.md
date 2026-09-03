# 배포 가이드

QA Manager 를 호스트 환경 또는 Docker Compose 로 배포하는 방법.

---

## 1. 사전 준비

| 항목 | 비고 |
|---|---|
| MariaDB 11+ | docker compose 사용 시 자동 제공 |
| Redis 7+ | 토큰 블랙리스트 · OTP 저장. docker compose 사용 시 자동 제공 |
| JDK 25 | 호스트 직접 실행 시만 필요 |
| Node ≥ 22.12 | 호스트 직접 실행 시만 필요 |
| AWS S3 버킷 + IAM 키 | 첨부 이미지 업로드. `s3:PutObject` 권한 필요 |
| 도메인 / TLS 인증서 | 운영 시 Nginx / Caddy / Cloudflare 등으로 별도 처리 |

---

## 2. 환경변수

루트의 `.env.example` 을 복사:

```bash
cp .env.example .env
```

채워야 하는 값:

| 키 | 설명 |
|---|---|
| `DB_HOST` | docker compose: `db` / 호스트 직접: `localhost` 등 |
| `DB_NAME` `DB_USER` `DB_PASSWORD` | 서비스용 DB 계정 |
| `DB_ROOT_PASSWORD` | (compose 전용) mariadb 컨테이너 초기 root 비밀번호 |
| `JWT_SECRET` | **64자 이상** 랜덤 문자열 — `openssl rand -hex 48` 권장 |
| `AWS_REGION` `AWS_S3_BUCKET` `AWS_ACCESS_KEY_ID` `AWS_SECRET_ACCESS_KEY` | S3 업로드 |
| `CORS_ALLOWED_ORIGINS` | 프론트엔드 도메인. 콤마로 다중 지정 가능 |
| `NUXT_PUBLIC_API_BASE` | **브라우저에서 접근 가능한** 백엔드 URL. compose 내부 hostname 사용 금지 |

> `JWT_SECRET` 은 절대 깃에 커밋하지 말 것. 운영/스테이징/로컬 모두 다른 값 사용 권장.

---

## 3. Docker Compose 로 배포 (권장)

원클릭 풀스택 실행. MariaDB + Redis + 백엔드 + 프론트엔드 컨테이너 4개.

### 시작

```bash
cp .env.example .env       # 값 채우기
docker compose up -d --build
```

최초 빌드는 5~10분 정도. 두 번째부터는 Gradle/npm 의존성 캐시로 빨라짐.

기본 노출 포트 (`docker-compose.yml` 기준):
- 프론트엔드: `http://<HOST>:3247` (초록: 3248 — 아래 무중단 배포 참고)
- 백엔드: `http://<HOST>:8357` (초록: 8358)
- DB: `<HOST>:13307` (운영에선 방화벽/보안그룹으로 허용 IP 를 제한하거나 db `ports` 섹션 제거 권장)
- Redis: 외부 미노출 (compose 네트워크 내부 전용)

### 운영 명령

```bash
docker compose ps                    # 상태 확인
docker compose logs -f backend       # 로그 추적
docker compose restart backend       # 재시작
./deploy.sh                          # 업데이트 (무중단 — 아래 참고)
docker compose up -d --build         # 업데이트 (단순 — 백엔드가 뜨는 30초쯤 끊김)
docker compose down                  # 종료 (DB 데이터 유지)
docker compose down -v               # 종료 + DB 볼륨 삭제 (주의)
```

### 무중단 배포 (`deploy.sh`)

`docker compose up -d --build` 는 컨테이너를 내렸다 새로 띄우므로 백엔드가 기동되는 동안(약 30초) API 가 끊깁니다.
`deploy.sh` 는 **파랑/초록 두 벌**을 번갈아 써서 끊김을 없앱니다.

```bash
git pull && ./deploy.sh              # ENV_FILE=.env.prod ./deploy.sh 처럼 env 파일 지정 가능
```

동작:
1. 이미지 빌드 (`backend`, `frontend` — 두 색이 같은 이미지 태그를 씀)
2. 지금 꺼져 있는 색을 새 이미지로 기동 (`backend-green`/`frontend-green` 은 profile `green`)
3. 새 색의 헬스체크가 `healthy` 가 될 때까지 대기 (기본 180초, 실패하면 새 색을 내리고 **롤백**)
   - 백엔드는 `wget --spider /actuator/health`(DB·Redis 포함, 메일 제외), 프론트는 `/auth/login` 응답으로 판단. 백엔드 이미지에 `wget` 을 설치해 두었으니 `docker inspect --format '{{.State.Health.Status}}' qa-manager-backend` 가 `healthy` 인지 먼저 확인하면 문제를 빨리 찾을 수 있습니다
4. 옛 색을 graceful 하게 정지 — 처리 중인 요청은 마치고, SSE 는 끊어 클라이언트가 새 색으로 재연결
5. 활성 색을 `.deploy-active` 에 기록

전제:
- **nginx upstream 에 두 색의 포트가 모두 있고, 초록(`8358`·`3248`)은 `backup` 이어야 합니다** — [`docs/nginx.example.conf`](./nginx.example.conf) 참고. 파랑이 살아 있으면 모든 요청이 파랑으로 가고, 파랑을 내리면 초록으로 넘어갑니다. `backup` 없이 두 색을 번갈아 쓰면 HTML 은 새 빌드에서, JS 청크(`/_nuxt/*.js`)는 옛 빌드에서 받아 `Failed to fetch dynamically imported module` 오류가 납니다.
- 배포 전에 열어 둔 화면은 옛 청크를 요청하다 실패할 수 있는데, 웹앱이 청크 로드 실패를 감지하면 스스로 새로고침합니다(1분에 한 번만).
- 새 백엔드가 뜨면서 Flyway 마이그레이션이 먼저 적용되고 잠시 옛 백엔드도 같은 DB 를 씁니다. 컬럼 삭제·이름 변경처럼 옛 코드를 깨뜨리는 스키마 변경은 두 번에 나눠 배포하세요.
- 웹앱은 새 빌드를 감지하면 화면에 "새 버전 · 새로고침" 배너를 띄우고 다음 화면 이동 때 새 버전으로 다시 불러옵니다 (데스크톱 앱 포함).

### 백업

```bash
# 덤프
docker exec qa-manager-db sh -c 'mariadb-dump -u root -p"$MARIADB_ROOT_PASSWORD" "$MARIADB_DATABASE"' > backup.sql

# 복원
docker exec -i qa-manager-db sh -c 'mariadb -u root -p"$MARIADB_ROOT_PASSWORD" "$MARIADB_DATABASE"' < backup.sql
```

### 리버스 프록시 (선택)

운영 시 80/443 단일 진입점이 필요하면 호스트에 Nginx / Caddy 를 두고 프록시:

```
/        → 프론트엔드 (호스트 직접 실행: 127.0.0.1:3000 / compose: 127.0.0.1:3247 + 3248)
/api, /swagger-ui.html, /v3/api-docs → 백엔드 (호스트 직접 실행: 127.0.0.1:8080 / compose: 127.0.0.1:8357 + 8358)
```

**바로 쓸 수 있는 예시:** [`docs/nginx.example.conf`](./nginx.example.conf)
- HTTP → HTTPS 리다이렉트
- SSE 라우팅 (`proxy_buffering off` — 실시간 알림 필수. 서버가 25초마다 keep-alive 를 보내므로 `proxy_read_timeout` 은 기본값 60초로 충분)
- `_nuxt/*` 정적 자산 long-cache
- HSTS / X-Frame-Options 등 보안 헤더

이때 `NUXT_PUBLIC_API_BASE` 를 같은 도메인(`https://example.com`) 으로 변경.

---

## 4. 호스트에서 직접 실행

Docker 없이 직접 띄우고 싶을 때.

### DB 준비

MariaDB 에 데이터베이스 + 사용자 생성:

```sql
CREATE DATABASE qa_manager CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'qa_manager'@'%' IDENTIFIED BY '...';
GRANT ALL PRIVILEGES ON qa_manager.* TO 'qa_manager'@'%';
FLUSH PRIVILEGES;
```

Flyway 가 백엔드 첫 실행 시 스키마 마이그레이션을 자동 수행.

### 백엔드

```bash
cd backend
./gradlew bootJar
java -jar build/libs/qa-manager-*.jar
# 또는 개발용
./gradlew bootRun
```

`systemd` 유닛 예시 (`/etc/systemd/system/qa-manager-backend.service`):

```ini
[Unit]
Description=QA Manager Backend
After=network.target

[Service]
WorkingDirectory=/opt/qa-manager
EnvironmentFile=/opt/qa-manager/.env
ExecStart=/usr/bin/java -jar /opt/qa-manager/backend.jar
Restart=on-failure
User=qa-manager

[Install]
WantedBy=multi-user.target
```

### 프론트엔드

```bash
cd frontend
npm ci
npm run build
HOST=0.0.0.0 PORT=3000 NODE_ENV=production node .output/server/index.mjs
```

`pm2` 또는 `systemd` 로 데몬화 권장.

---

## 5. 첫 배포 체크리스트

- [ ] `.env` 작성 (`JWT_SECRET` 길이 충분한지 확인)
- [ ] AWS S3 버킷 CORS 설정 — 프론트 도메인에서 `PUT` 허용
- [ ] DB 외부 노출 차단 (compose 의 `db.ports` 제거 권장)
- [ ] 계정 정리 — Flyway `V2__seed.sql` 시드 계정(비밀번호 `1234`)이 함께 생성되므로, 운영에서는 시드 비활성화 또는 전 계정 비밀번호 변경
- [ ] 백엔드 헬스체크: `curl http://<HOST>:8080/actuator/health`
- [ ] 프론트엔드 접근: 브라우저에서 `http://<HOST>:3000`
- [ ] 로그인 → 프로필 이미지 업로드 → S3 권한 확인
- [ ] (운영) HTTPS 적용 — Nginx + Let's Encrypt 등

---

## 6. 트러블슈팅

| 증상 | 원인 / 해결 |
|---|---|
| backend 가 `db` 호스트를 못 찾음 | `.env` 의 `DB_HOST=db` 확인. compose 네트워크 안에서만 유효 |
| Flyway "validation failed" | 마이그레이션 파일을 운영에서 수정한 경우. 운영 DB 의 `flyway_schema_history` 조정 필요 |
| 브라우저에서 API 호출 시 CORS 에러 | `CORS_ALLOWED_ORIGINS` 에 프론트 도메인 추가 후 백엔드 재시작 |
| S3 업로드 403 | IAM 사용자 권한 + 버킷 CORS 설정 확인 |
| 컨테이너 OOM | `JAVA_OPTS=-Xmx512m` 등 명시 또는 호스트 메모리 증설 |
