# GitHub 연동 가이드

QA 항목을 GitHub 이슈와 연결하고, 이슈를 참조한 커밋을 QA 상세에서 추적하는 기능입니다.

![GitHub 커밋 추적](images/github-commits.png)

## 무엇이 되나

| 기능 | 동작 |
|---|---|
| 이슈 자동 생성 | QA 등록 시 체크박스 하나로 연결 repo 에 GitHub 이슈 생성 (QA 딥링크 포함) |
| 상태 동기화 | QA 상태 변경 → 이슈 open/close 자동 반영 (단방향: QA → GitHub) |
| 커밋 추적 | 커밋 메시지에 `#이슈번호` 를 남기면 QA 상세 "연결된 커밋" 에 자동 표시 |
| 다중 repo | 프로젝트당 여러 저장소 연결, QA 생성 시 대상 repo 선택 |

상태 매핑:

| QA 상태 | GitHub 이슈 |
|---|---|
| 수정완료 · 확인완료 | → `closed` |
| 수정필요 · 진행중 · 추가확인필요 | → `open` (닫혀 있으면 reopen) |
| 보류 | 변경 안 함 |

## 설정 방법 (관리자)

환경변수가 **필요 없습니다.** 관리자 화면에서 GitHub App 을 원클릭으로 생성합니다 (Manifest flow).

1. **관리자 → 설정 → GitHub** 탭 이동
2. GitHub 조직명 입력 (비우면 개인 계정) → `GitHub App 생성하기`
3. GitHub 페이지에서 앱 생성 승인 → 자동으로 돌아와 자격증명 교환·저장 완료
4. `GitHub에서 앱 설치` 링크로 이동해 **이슈를 만들 저장소에 앱 설치** (repo 권한 선택)
5. 프로젝트 생성/수정 모달에서 저장소 연결

앱 권한은 최소로 요청합니다: `issues:write`, `metadata:read`, `contents:read`. 웹훅은 사용하지 않습니다(방화벽 안 배포에서도 동작).

### GitHub Enterprise Server

`.env` 에서 base URL 만 바꾸면 됩니다:

```bash
GITHUB_WEB_BASE_URL=https://github.example.com
GITHUB_API_BASE_URL=https://github.example.com/api/v3
```

## 사용 흐름

1. QA 생성 모달에서 `GitHub 이슈도 함께 생성` 체크 (연결 repo 가 있는 프로젝트에서만 노출, 기본 ON)
2. 생성된 이슈 본문에는 QA Manager 딥링크와 "커밋 메시지에 `#번호` 를 넣으면 추적됩니다" 안내가 자동 삽입
3. 개발자는 평소처럼 커밋: `git commit -m "fix: 로그인 버그 수정 #42"`
4. QA 상세에서 이슈 배지(open=초록/closed=보라)와 커밋 목록(sha · 메시지 · 작성자 · 날짜) 확인

## 동작 방식 (내부)

- **인증 2단계**: App JWT(RS256, 9분) → installation token(1시간, 만료 60초 전 자동 갱신 캐시)
- **이슈 생성**: QA 저장 트랜잭션 **커밋 후** 비동기 실행 — GitHub 장애가 QA 등록을 막지 않음. `qa_github_issue` 유니크 키로 중복 생성 방지
- **커밋 조회**: 이슈 타임라인 API 에서 `referenced`/`closed` 이벤트의 커밋 SHA 수집 → 상세 조회 (최대 5페이지 · 30커밋 상한)
- **자격증명 보관**: app id · PEM private key · client secret 은 `github_app` 테이블 단일 행에 저장. GitHub 이 주는 PKCS#1 PEM 은 외부 라이브러리 없이 PKCS#8 로 변환해 사용

## 주의 · 트러블슈팅

| 증상 | 확인 |
|---|---|
| 이슈가 생성되지 않음 | 프로젝트에 repo 가 연결돼 있는지, 해당 repo 에 앱이 **설치**돼 있는지 (권한만으로는 부족) |
| 커밋이 안 보임 | 커밋 메시지에 `#이슈번호` 가 있는지, 이슈와 같은 repo 의 커밋인지 (타 repo 참조는 조회 불가 시 건너뜀) |
| 연동 해제 후 | QA Manager 의 자격증명만 삭제됨 — **GitHub 쪽 앱은 남아있으므로** GitHub 설정에서 별도 삭제 |
