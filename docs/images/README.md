# 스크린샷 폴더

README 와 docs 문서에 쓰이는 이미지를 이 폴더에 모읍니다.
아래 파일명으로 저장하면 문서에 자동으로 표시됩니다. (JPG, 밝은 테마, 1440×900 뷰포트 · 2x 기준)

## 캡처 목록

| 파일명 | 화면 | 캡처 포인트 |
|---|---|---|
| `dashboard.jpg` | `/` 대시보드 | 사이드바(프로젝트 트리) + QA 현황 요약 카드 + QA 목록 전체 페이지 |
| `project-detail.jpg` | `/project/:id` 개요 | 사이드바에서 프로젝트가 펼쳐진 상태, 헤더 + 요약 카드 + 업데이트 아코디언 |
| `qa-detail.jpg` | `/qa/:id` | 3분할 — 접힌 사이드바 + 좌 목록 + 본문 + 우 이력 (뷰포트) |
| `qa-comments.jpg` | QA 상세 코멘트 영역 | 답글 · `@멘션` · 이모지 반응이 보이는 코멘트 섹션만 |
| `github-commits.jpg` | QA 상세 정보 카드 | GitHub 이슈 배지 + 연결된 커밋 목록 |
| `notifications.jpg` | `/notifications` | 왼쪽 알림 목록(안읽음 표시) + 오른쪽 고른 알림의 QA 내용 |
| `settings.jpg` | `/settings/notifications` | 전체 화면 설정 — 왼쪽 세 묶음 메뉴 + 알림 토글 |
| `test-flow.jpg` | 테스트 플로우 에디터 | 워크플로우 그래프(분기 라벨 포함) + 팔레트/툴바 |
| `test-run.jpg` | `/run/:id` 실행 화면 | 플랫폼 필터·배지 + 스텝 테이블 + 결과 버튼 |
| `login-otp.jpg` | `/auth/login` | OTP 입력 단계 (마스킹된 이메일 안내 박스) |
| `demo-login.jpg` | 데모 로그인 화면 | 데모 계정 클릭 목록 + 데모 배너 |

## 사용 위치

- 루트 `README.md` / `README.en.md` — dashboard / qa-detail / project-detail / settings / qa-comments / github-commits / test-flow / test-run / notifications / login-otp
- `docs/FEATURES.md` — 위 전부
- `docs/GITHUB_INTEGRATION.md` — github-commits
- `docs/DEMO.md` — demo-login

## 다시 찍기

데모 모드 개발 서버(`cd frontend && DEMO_BUILD=true npm run dev`)를 띄우고 헤드리스 Chrome 으로 캡처합니다.
데모 배너와 Nuxt DevTools 는 캡처 전에 숨깁니다. 데모 사이트(https://qa-manager-demo.cygnus2.com)에서 직접 찍어도 됩니다.
