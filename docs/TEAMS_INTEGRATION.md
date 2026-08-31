# MS Teams 봇 알림 설정 가이드

QA 알림(등록·상태 변경·코멘트·답글·멘션)을 Teams 1:1 채팅으로 받는 기능입니다.
**Bot Framework 프로액티브 메시지** 방식이며, Adaptive Card 에 "QA 열기" 딥링크가 포함됩니다.

> 기본 비활성(`TEAMS_ENABLED=false`)이며, 미설정 상태에서도 인앱 알림은 정상 동작합니다.

## 준비물

| 항목 | 용도 |
|---|---|
| Azure Bot 리소스 (Microsoft App ID / secret) | 메시지 발송 (Bot Connector) |
| Azure AD 앱 등록 (tenant/client id + secret) | 이메일 → AAD Object ID 조회 (Graph, 읽기 전용) |
| Teams 앱 패키지 | 사용자가 봇을 personal 로 설치 — [`teams-app/`](../teams-app/README.md) 에 매니페스트 템플릿과 빌드 방법 |

## 설정 순서 (운영자)

1. **Azure Portal** 에서 Azure Bot 리소스 생성 → **Teams 채널 활성화**
2. Messaging endpoint 를 `https://<백엔드 공개도메인>/api/teams/messages` 로 설정
3. `.env` 에 자격증명 입력 후 재시작:

   ```bash
   TEAMS_ENABLED=true
   # Graph 조회용 (email → AAD Object ID)
   TEAMS_TENANT_ID=...
   TEAMS_CLIENT_ID=...
   TEAMS_CLIENT_SECRET=...
   # Bot Connector 발송용
   TEAMS_BOT_APP_ID=...
   TEAMS_BOT_APP_PASSWORD=...
   # 싱글테넌트 봇이면 회사 tenant id (멀티테넌트는 기본값 botframework.com)
   TEAMS_BOT_TENANT_ID=...
   # (선택) 회사 메일 도메인 — 이 도메인 계정만 발송 대상
   TEAMS_EMAIL_DOMAIN=example.com
   ```

4. [`teams-app/`](../teams-app/README.md) 절차대로 매니페스트에 App ID 를 채워 zip 패키지 생성 → Teams 관리센터 업로드 또는 사용자 개별 업로드

## 사용자별 설정 (각 팀원)

1. 프로필/관리자 화면에서 **이메일 등록** (AAD 계정 이메일)
2. Teams 에서 **봇 앱 설치** (앱 → 앱 관리 → 앱 업로드) — 설치해야 1:1 대화가 열립니다. 미설치 사용자는 발송되지 않습니다(403)
3. **관리자 → 설정 → MS Teams** 에서 `Teams 알림 받기` 토글 ON
4. `내게 테스트 발송` 으로 확인 — 결과 모달이 6단계 체크리스트로 어디서 막혔는지 보여줍니다:
   `Teams 설정 활성화 → Email 등록 → 알림 토글 → AAD 사용자 매핑 → 1:1 chat 준비 → 발송`

관리자는 팀원 관리 탭에서 다른 사용자를 대상으로도 같은 테스트를 실행할 수 있습니다.

## 알림 개인화

**관리자 → 설정 → 알림 설정** 탭 (개인별 저장):

- 종류별 토글: QA 알림 / 코멘트 알림 / 답글 알림
- **방해금지 시간대**: `HH:mm` 시작~종료, 자정 넘김(예: 22:00~08:00) 지원, Asia/Seoul 기준

> 이 설정은 **Teams 발송에만** 적용됩니다. 인앱 벨 알림은 항상 저장됩니다.

## 동작 방식 (내부)

- 발송은 알림 저장 트랜잭션 **커밋 후 비동기** — Teams 장애가 본 기능에 영향 없음
- email → AAD Object ID(Graph) → 1:1 conversation 생성(Bot Connector) 결과를 멤버 행에 캐시해 재사용
- 발송/스킵 내역은 전부 `teams_send_log` 에 기록 — 스킵 사유(설정 비활성 / 토글 off / 방해금지 / 이메일 없음 / 도메인 불일치 / AAD 미매핑 / 봇 미설치)까지 남아 진단 가능
- 인바운드 웹훅(`/api/teams/messages`)은 인증 없이 열려 있지만, Bot Framework OpenID JWKS 로 **RS256 서명 · issuer · audience · serviceUrl claim 을 자체 검증**해 위조 요청을 차단

## 트러블슈팅

| 증상 | 확인 |
|---|---|
| 테스트 발송 "AAD 사용자 매핑" 실패 | 등록한 이메일이 실제 AAD 계정 이메일인지, Graph 앱 권한(User.Read.All 등) 승인 여부 |
| "1:1 chat 준비" 실패 (403) | 사용자가 봇 앱을 **개인 스코프로 설치**했는지 |
| 발송 자체가 안 됨 | `TEAMS_ENABLED=true` 인지, `teams_send_log` 의 스킵 사유 확인 |
| 싱글테넌트 봇 토큰 오류 | `TEAMS_BOT_TENANT_ID` 를 회사 tenant id 로 지정했는지 |
