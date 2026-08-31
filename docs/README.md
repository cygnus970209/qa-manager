# QA Manager 문서 위키

QA Manager 의 기능 · 아키텍처 · 운영 문서 모음입니다. 프로젝트 개요는 [루트 README](../README.md) 를 먼저 보세요.

## 문서 지도

### 처음 사용하는 분
| 문서 | 내용 |
|---|---|
| [FEATURES.md](FEATURES.md) | 화면별 기능 상세 가이드 — 대시보드부터 관리자 페이지까지 |
| [DEMO.md](DEMO.md) | 설치 없이 체험하는 데모 모드 |

### 설치 · 운영하는 분
| 문서 | 내용 |
|---|---|
| [DEPLOYMENT.md](DEPLOYMENT.md) | Docker Compose / 호스트 배포, 백업, 트러블슈팅 |
| [TEAMS_INTEGRATION.md](TEAMS_INTEGRATION.md) | MS Teams 봇 알림 — Azure Bot 준비부터 사용자 설치까지 |
| [GITHUB_INTEGRATION.md](GITHUB_INTEGRATION.md) | GitHub App 연동 — 원클릭 앱 생성, 이슈/커밋 추적 |
| [nginx.example.conf](nginx.example.conf) | 운영용 Nginx 리버스 프록시 예시 (TLS · SSE · 캐시) |
| [nginx.demo.example.conf](nginx.demo.example.conf) | 데모 사이트용 Nginx 예시 |

### 개발자
| 문서 | 내용 |
|---|---|
| [ARCHITECTURE.md](ARCHITECTURE.md) | 시스템 구성도, 백엔드/프론트 구조, 기술 결정 기록, Flyway 스키마 히스토리 |
| [API.md](API.md) | REST API 전체 레퍼런스 (실행 중에는 Swagger UI 제공) |
| [SECURITY_IP_OTP_LOGIN.md](SECURITY_IP_OTP_LOGIN.md) | IP 조건부 이메일 OTP 로그인 설계 문서 |

## 스크린샷 관리

문서에 쓰이는 이미지는 [`images/`](images/README.md) 폴더에 모아둡니다. 파일명 규칙과 권장 캡처 목록은 해당 폴더의 README를 참고하세요.
