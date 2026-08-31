# 스크린샷 폴더

README 와 docs 문서에 쓰이는 이미지를 이 폴더에 모읍니다.
아래 파일명으로 저장하면 문서에 자동으로 표시됩니다. (PNG 권장, 밝은 테마 기준)

## 권장 캡처 목록

| 파일명 | 화면 | 캡처 포인트 |
|---|---|---|
| `dashboard.png` | `/` 대시보드 | 프로젝트 카드 + 통계 8종 + QA 목록이 한 화면에 |
| `project-detail.png` | `/project/:id` | 업데이트 아코디언 펼친 상태, 상태별 QA 배지 |
| `qa-detail.png` | `/qa/:id` | 3분할 전체 — 좌 목록 사이드바 + 본문 + 우 이력 |
| `qa-comments.png` | QA 상세 코멘트 영역 | `@멘션` 자동완성 드롭다운 또는 이모지 반응이 보이게 |
| `notification-center.png` | 상단 알림센터 드롭다운 | 안읽음 배지 + 알림 목록 |
| `github-commits.png` | QA 상세 | GitHub 이슈 배지 + 연결된 커밋 목록 |
| `admin-settings.png` | `/admin` 설정 탭 | MS Teams 또는 GitHub 서브탭 |
| `login-otp.png` | `/auth/login` | OTP 입력 단계 (마스킹된 이메일 안내 박스) |
| `demo-login.png` | 데모 로그인 화면 | 데모 계정 클릭 목록 + 데모 배너 |

## 사용 위치

- 루트 `README.md` — dashboard / qa-detail / qa-comments / github-commits / notification-center / login-otp
- `docs/FEATURES.md` — 위 전부
- `docs/GITHUB_INTEGRATION.md` — github-commits
- `docs/DEMO.md` — demo-login

> 팁: 데모 사이트(https://qa-manager-demo.cygnus2.com)에서 캡처하면 실데이터 노출 걱정 없이 스크린샷을 만들 수 있습니다.
