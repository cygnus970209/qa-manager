# QA Manager — Teams 앱 패키지

QA Manager 알림 봇을 Teams에 personal scope로 설치하기 위한 앱 패키지입니다.

## 구성

| 파일 | git | 설명 |
|---|---|---|
| `manifest.example.json` | ✅ 커밋 | 매니페스트 **템플릿** (App ID 자리는 `your-bot-id` 플레이스홀더) |
| `manifest.json` | 🚫 gitignore | 실제 App ID를 채운 **작업본** (`.env`처럼 로컬에만 존재) |
| `color.png` | ✅ 커밋 | 컬러 아이콘 192×192 |
| `outline.png` | ✅ 커밋 | 아웃라인 아이콘 32×32 (투명 배경) |
| `generate_icons.py` | ✅ 커밋 | 아이콘 생성 스크립트 (의존성 없음). 로고 교체 시 참고 |
| `qa-manager-teams-app.zip` | 🚫 gitignore | 업로드용 빌드 산출물 (아래 명령으로 재생성) |

> `.env`/`.env.example` 과 동일한 방식입니다. 템플릿만 git에 올라가고, 실제 값이 든
> `manifest.json` 과 zip은 추적되지 않습니다.

## 처음 받았을 때 (clone 직후)

`manifest.json` 은 git에 없으므로 템플릿을 복사해 만들고 App ID를 채웁니다:

```bash
cd teams-app
cp manifest.example.json manifest.json
# manifest.json 의 "your-bot-id" 2곳(id, bots[].botId)을 Azure Bot 의 Microsoft App ID 로 교체
```

## 업로드 패키지(zip) 만들기

```bash
cd teams-app
python3 generate_icons.py            # 아이콘을 다시 만들 때만 (이미 있으면 생략)
rm -f qa-manager-teams-app.zip
zip -j qa-manager-teams-app.zip manifest.json color.png outline.png
```

## 업로드 전 확인할 것

1. **봇 App ID** — `manifest.json` 의 `id` 와 `bots[].botId` 가 Azure Bot 의 Microsoft App ID 와 일치하는지.
2. **developer URL** — `websiteUrl`/`privacyUrl`/`termsOfUseUrl` 이 `https://example.com` 플레이스홀더입니다.
   조직 앱 카탈로그 업로드 시엔 실제 https URL로 교체해야 합니다(개발자 사이드로드는 보통 그대로도 통과).
3. **Messaging endpoint** — Azure Bot에 messaging endpoint(`https://<도메인>/api/teams/messages`)가
   설정돼 있어야 설치 시 `conversationUpdate` 이벤트가 백엔드로 들어와 conversation이 캐싱됩니다.

## 설치 방법 (둘 중 하나)

### A. 사이드로드 (테스트 — 본인 계정에 빠르게)
Teams → 좌측 **앱(Apps)** → **앱 관리(Manage your apps)** → **앱 업로드(Upload an app)** →
**사용자 지정 앱 업로드(Upload a custom app)** → `qa-manager-teams-app.zip` 선택 → **추가(Add)**.
> 조직에서 "사용자 지정 앱 업로드"가 허용돼 있어야 메뉴가 보입니다.

### B. 조직 앱 카탈로그 (배포 — 여러 사용자에게)
Teams 관리 센터 → **Teams 앱 → 앱 관리 → 앱 업로드**로 zip 업로드 → 필요 시 **설정 정책(setup policy)** 으로 배포.

## 설치 후

봇을 추가하면 1:1 채팅이 열리고, 백엔드가 설치 이벤트를 받아 해당 사용자의 conversation을 캐싱합니다.
이후 QA Manager 알림이 이 채팅으로 전송됩니다. 미설치 사용자에게는 발송 시 403(미설치)이 되어
`teams_send_log`에 안내가 남습니다.

> 이 봇은 `isNotificationOnly: true`(알림 전용)라서 사용자가 봇에게 보내는 메세지에는 응답하지 않습니다.
> 