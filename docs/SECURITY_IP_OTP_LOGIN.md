# 설계 문서 — IP 기반 조건부 이메일 OTP 로그인

> 상태: **설계 (구현 전)**
> 작성일: 2026-06-01
> 결정 사항: OTP 채널 = **이메일(SMTP 신규)**, 신뢰 IP = **yml/환경변수 설정**

## 1. 목표

신뢰하지 않는 네트워크에서의 로그인에 2단계 인증(이메일 OTP)을 강제한다.

- **신뢰 IP 대역**(예: 사무실 고정 IP)에서 로그인 → 현행 그대로 아이디/비밀번호만으로 토큰 발급.
- **그 외 IP**에서 로그인 → 아이디/비밀번호 검증 후 토큰을 보류하고, 이메일로 일회용 코드(OTP)를 발송. 코드 검증에 성공해야 토큰 발급.

## 2. 범위

| 포함 | 제외(별도 논의) |
|------|------|
| IP allowlist(CIDR) 설정 기반 분기 | IP allowlist 관리 UI(이번엔 yml로 고정) |
| 이메일 OTP 발송/검증 | SMS·TOTP(authenticator) 등 타 채널 |
| 로그인 흐름 2단계화 + 신규 엔드포인트 | 관리자 강제 로그아웃·세션 관리 개편 |
| 보안 가드(만료·시도제한·쿨다운) | "이 기기 N일 신뢰" 쿠키(미채택) |
| | 비밀번호 정책 강화 / 감사 대시보드 |

## 3. 현재 인프라 (재사용/신규 구분)

| 구성요소 | 현황 | 이 기능에서 |
|----------|------|-------------|
| 인증 방식 | JWT access/refresh 쿠키 (`AuthService`, `JwtAuthenticationFilter`, `SecurityConfig`) | **재사용** — 토큰 발급 지점만 분기 |
| 클라이언트 IP 추출 | `audit/RequestLoggingFilter.clientIp()` (X-Forwarded-For 우선 → `getRemoteAddr`) | **로직 추출/공유** (아래 4.3 신뢰경계 주의) |
| 사용자 이메일 | `team_member.email` (nullable), `MemberDto.EmailRequest`로 등록/변경 | **재사용** — 단 미등록자 정책 필요(4.7) |
| 이메일 발송(SMTP) | ❌ 없음 | **신규**: `spring-boot-starter-mail` + SMTP 설정 + `MailService` |
| OTP 저장/검증 | ❌ 없음 | **신규** `LoginOtpService` — **Redis**에 TTL 저장(4.5) |
| Redis | ✅ 사용 중 (`TokenBlacklistService`) | **재사용** — OTP 저장 + rate limit |
| 사용자 이메일 미등록자 | `email` nullable | **차단(A)** 정책(4.7) |

## 4. 상세 설계

### 4.1 동작 흐름

```
[POST /api/auth/login]  (username, password)
  │
  ├─ 1) 자격 검증 (기존 AuthService.login 의 비번 확인 로직 재사용)
  │
  ├─ 2) 신뢰 IP 판정: clientIp ∈ trusted-cidrs ?
  │       ├─ 예  → 기존대로 토큰 발급 + 쿠키 → 200 {authenticated:true, user}
  │       └─ 아니오 ↓
  │
  ├─ 3) 이메일 등록 여부 확인 (4.7 정책: 미등록 = 차단)
  │       └─ 미등록 → 403 {reason:"email_required"} (토큰 미발급)
  │
  └─ 4) OTP 생성·해시 저장·이메일 발송
          → 200 {authenticated:false, otpRequired:true, challengeId, maskedEmail:"ho***@i***.com"}

[POST /api/auth/login/otp/verify]  (challengeId, code)
  ├─ challenge 조회(만료/소비/시도초과 검사) → 코드 해시 비교
  │     ├─ 성공 → consumed 처리, 토큰 발급 + 쿠키
  │     │         → 200 {authenticated:true, user}
  │     └─ 실패 → attempts++ → 401 {remainingAttempts}
  │
[POST /api/auth/login/otp/resend]  (challengeId)  ── 쿨다운 적용, 신규 코드 발송
```

### 4.2 신뢰 IP 설정 (yml/환경변수)

`application.yml` (운영값은 `application-prod.yml` 또는 환경변수로 주입):

```yaml
app:
  security:
    ip-otp:
      enabled: ${SECURITY_IP_OTP_ENABLED:true}
      # 이 대역에서의 로그인은 OTP 면제. CIDR 표기. 비어있으면 "모든 IP가 OTP 대상".
      trusted-cidrs: ${SECURITY_TRUSTED_CIDRS:}   # 예) 203.0.113.0/24,198.51.100.7/32
      otp:
        length: 6
        ttl: 10m
        max-attempts: 5
        resend-cooldown: 60s

spring:
  mail:                         # SMTP 신규
    host: ${SMTP_HOST:}
    port: ${SMTP_PORT:587}
    username: ${SMTP_USERNAME:}
    password: ${SMTP_PASSWORD:}
    properties:
      mail.smtp.auth: true
      mail.smtp.starttls.enable: true
    from: ${SMTP_FROM:QA Manager <no-reply@intocns.com>}
```

- CIDR 파싱/매칭: Spring의 `IpAddressMatcher`(spring-security-web) 사용 — 별도 라이브러리 불필요.
- `enabled:false`면 전 구간 현행 동작(롤백 스위치).
- `trusted-cidrs`가 비면 **전원 OTP 대상**이 되므로, 운영 배포 시 사무실 IP를 반드시 채울 것(체크리스트 7).

### 4.3 ⚠️ X-Forwarded-For 신뢰 경계 (중요)

IP를 **보안 판단**에 쓰므로 스푸핑 방지가 필수다. 기존 `RequestLoggingFilter`는 XFF 첫 값을 그대로 신뢰하는데, 이는 감사 로그용으로는 충분하나 인증 우회 판단에는 위험하다(클라이언트가 헤더를 위조하면 신뢰 IP로 가장 가능).

대응:
- nginx 등 신뢰 프록시가 **클라이언트가 보낸 XFF를 덮어쓰도록** 구성(`proxy_set_header X-Forwarded-For $remote_addr` 또는 RemoteIP 모듈로 신뢰 hop만 인정).
- 또는 Spring `ForwardedHeaderFilter` + `server.forward-headers-strategy: framework`로 통일하고, 신뢰 프록시 IP를 별도 화이트리스트로 검증.
- 설계 결정: **신뢰 프록시 1단(nginx)만 가정**하고, XFF의 *마지막에서 프록시 수만큼 앞선 값*을 클라이언트 IP로 채택. (docs/nginx.example.conf 갱신 필요)

### 4.4 신규 엔드포인트

| 메서드 | 경로 | 요청 | 응답(주요) |
|--------|------|------|-----------|
| POST | `/api/auth/login` | `{username, password}` | `{authenticated, otpRequired?, challengeId?, maskedEmail?, user?}` |
| POST | `/api/auth/login/otp/verify` | `{challengeId, code}` | `{authenticated, user}` / 401 |
| POST | `/api/auth/login/otp/resend` | `{challengeId}` | `202 {resentAt}` / 429 |

- `challengeId`: 추측 불가능한 불투명 토큰(UUID 또는 랜덤 32바이트 base64url). DB row를 가리킴.
- SecurityConfig의 permitAll 경로에 위 3개 추가(인증 전 호출되므로).

### 4.5 OTP 저장 — Redis 권장 (DB는 선택)

이 프로젝트는 **이미 Redis를 사용**한다(`auth/TokenBlacklistService`가 `StringRedisTemplate`로 토큰 블랙리스트를 TTL 보관). OTP도 Redis에 TTL로 저장하면 **만료 자동 처리·정리 스케줄러 불필요**해 가장 깔끔하다.

```
Key:   auth:otp:<challengeId>           (TTL = otp.ttl, 예: 600s)
Value: JSON { memberId, codeHash, channel, destMasked, attempts, maxAttempts, requestIp, lastSentAt }
보조:  auth:otp:member:<memberId> = <challengeId>   // 사용자당 활성 1개 보장(신규 발급 시 이전 키 삭제)
재발송 쿨다운: lastSentAt 비교 또는 auth:otp:cooldown:<challengeId> (TTL=resend-cooldown)
```

- 검증 성공/시도초과 시 키 삭제(= 소비). 시도 카운트는 동일 키 갱신.
- **영속 감사 기록**이 필요하면 발급/성공/실패 이벤트만 기존 `audit` 로그로 남기고, 코드값은 절대 기록하지 않는다.

아래 4.5-b는 영속 테이블이 꼭 필요할 때의 대안.

### 4.5-b (대안) DB 스키마 (`V12__login_otp.sql`)

```sql
CREATE TABLE login_otp (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    challenge_id CHAR(36)     NOT NULL,                 -- 불투명 식별자(UUID)
    member_id    BIGINT       NOT NULL,
    code_hash    VARCHAR(255) NOT NULL,                 -- BCrypt 해시(평문 저장 금지)
    channel      VARCHAR(16)  NOT NULL DEFAULT 'email',
    dest_masked  VARCHAR(255) NULL,                     -- 표시용 마스킹 주소
    request_ip   VARCHAR(45)  NULL,
    attempts     INT          NOT NULL DEFAULT 0,
    max_attempts INT          NOT NULL DEFAULT 5,
    expires_at   DATETIME     NOT NULL,
    consumed_at  DATETIME     NULL,                     -- 검증 성공/무효화 시각
    last_sent_at DATETIME     NOT NULL,                 -- resend 쿨다운 기준
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_login_otp_challenge (challenge_id),
    KEY idx_login_otp_member (member_id),
    CONSTRAINT fk_login_otp_member FOREIGN KEY (member_id) REFERENCES team_member (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

- 코드 자체는 6자리 숫자, **BCrypt 해시만 저장**(`BCryptPasswordEncoder`, 기존 비밀번호 인코더 재사용). 검증은 `matches`.
- 동일 사용자에 활성 challenge가 있으면 신규 발급 시 이전 것 `consumed_at` 처리(1개만 유효).
- 만료/소비된 row 정리용 스케줄러(@Scheduled) 또는 TTL 잡 — 선택(테이블 작음).

### 4.6 이메일 발송 (`MailService`)

- 의존성: `org.springframework.boot:spring-boot-starter-mail`.
- `@Async`(기존 `config/AsyncConfig` 재사용)로 비동기 발송 — 로그인 응답을 막지 않음.
- 발송 실패 시 로깅 + 사용자에겐 일반 메시지(열거/인프라 노출 방지). 단 재시도/resend 경로 제공.
- 본문: 6자리 코드 + 유효시간 + "요청한 적 없으면 무시" 문구. 한국어.

### 4.7 이메일 미등록 사용자 정책 — **차단 확정**

`team_member.email`은 nullable. 신뢰 IP 밖 + 이메일 미등록이면 OTP를 보낼 수 없다.

→ **결정: 차단(A).** 403 `email_required` 반환 + "관리자에게 이메일 등록을 요청하세요" 안내. 토큰 미발급.
→ **운영 전 전 멤버 이메일 등록을 반드시 점검**(미등록자는 신뢰 IP 밖에서 로그인 불가).

> 신뢰 기기 쿠키("이 기기 N일 신뢰")는 **이번 범위에서 채택하지 않음.** 매번 신뢰 IP 밖이면 OTP를 요구한다.

## 5. 프론트엔드 변경

- 로그인 페이지(`auth/login`)에 **OTP 입력 단계** 추가:
  1. 아이디/비번 제출 → 응답 `otpRequired:true`면 코드 입력 화면 전환(`challengeId` 보관, `maskedEmail` 표시).
  2. 코드 입력 → `/otp/verify`. 실패 시 남은 시도 표시, 만료 시 `resend`.
- `useAuthStore.login`을 2단계 응답을 처리하도록 확장(현재는 즉시 토큰 가정).

## 6. API 응답 예시

```jsonc
// 1단계: OTP 필요
{ "authenticated": false, "otpRequired": true,
  "challengeId": "b2c1...uuid", "maskedEmail": "ho***@i***.com",
  "expiresInSeconds": 600 }

// 2단계 성공 (기존 LoginResponse 형태 유지)
{ "authenticated": true, "expiresInSeconds": 900, "user": { /* MeResponse */ } }

// 2단계 실패
{ "error": "invalid_code", "remainingAttempts": 3 }
```

## 7. 보안 고려사항 / 운영 체크리스트

- [ ] **XFF 신뢰 경계**(4.3) — nginx에서 클라이언트 XFF 덮어쓰기 확인. 미설정 시 인증 우회 가능.
- [ ] `trusted-cidrs` 운영값 주입(사무실 IP). 비우면 전원 OTP.
- [ ] SMTP 자격증명은 환경변수/시크릿로만 주입(코드/yml 평문 금지).
- [ ] OTP: 6자리, 10분 만료, 5회 시도 후 challenge 폐기, resend 60초 쿨다운.
- [ ] 코드 **해시 저장** + 상수시간 비교. 평문/로그 노출 금지.
- [ ] 사용자 열거 방지: 잘못된 username이어도 응답·타이밍을 OTP 경로와 구분 불가하게(또는 일반 실패 통일).
- [ ] login·verify·resend에 IP/계정 단위 rate limit(브루트포스 방지).
- [ ] 감사 로그: OTP 발급/성공/실패/재발송 기록(기존 `ApiRequestLog`/`audit` 활용).
- [ ] 전 멤버 이메일 등록 점검(4.7-A).
- [ ] 롤백 스위치 `app.security.ip-otp.enabled=false` 동작 확인.

## 8. 구현 순서(제안)

1. 설정 바인딩(`IpOtpProperties`) + IP 매칭 유틸 + 신뢰경계(4.3) 정리.
2. `spring-boot-starter-mail` + `MailService`(@Async) + 로컬 SMTP(MailHog 등)로 발송 검증.
3. `LoginOtpService` — Redis 저장(4.5)으로 생성/검증/시도제한/쿨다운. (영속 감사 필요 시에만 `V12__login_otp.sql` 병행)
4. `AuthService.login` 분기 + `verify`/`resend` 엔드포인트 + SecurityConfig permitAll.
5. 프론트 2단계 UI + store 확장.
6. rate limit·감사·열거방지 가드.

## 9. 확정/미해결 사항

**확정:**
- 이메일 미등록자 = **차단(A)**. 운영 전 전 멤버 이메일 등록 점검.
- 신뢰 기기 쿠키 = **미채택**(신뢰 IP 밖이면 매번 OTP).
- OTP 저장 = **Redis**(4.5, 기존 인프라). 영속 감사 필요 시에만 DB 테이블 병행.
- rate limit 저장소 = **Redis**(이미 사용 중). login·verify·resend에 IP/계정 키 카운터.
- OTP 코드 해시 = **BCrypt** (검증은 `BCryptPasswordEncoder.matches`).
- OTP 파라미터 = **6자리 숫자 / 10분 만료 / 5회 시도 / 60초 재발송 쿨다운**.

**미해결:** 없음 — 구현 착수 가능.
