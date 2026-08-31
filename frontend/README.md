# QA Manager — Frontend

Nuxt 4 (Vue 3 · Pinia · Tailwind CSS) 프론트엔드입니다.
프로젝트 전체 소개와 문서는 [루트 README](../README.md) / [docs 위키](../docs/README.md) 를 보세요.

## 개발

```bash
npm install
npm run dev        # http://localhost:3000 (백엔드 http://localhost:8080 필요)
```

주요 스크립트:

| 명령 | 설명 |
|---|---|
| `npm run dev` | 개발 서버 |
| `npm run build` | SSR 운영 빌드 (`node .output/server/index.mjs`) |
| `DEMO_BUILD=true npm run generate` | 백엔드 없는 정적 데모 빌드 → [docs/DEMO.md](../docs/DEMO.md) |
| `npm run typecheck` | TypeScript strict 타입 검사 |

## 구조

```
app/
├── components/    # base(공용 UI 프리미티브) · feature(도메인 컴포넌트)
├── composables/   # 상태 없는 API 래퍼 (useQa, useGithub, useUpload, …)
├── demo/          # 데모 모드 mock 백엔드 (localStorage)
├── layouts/       # default(네비 포함) · blank(로그인)
├── middleware/    # 전역 인증 가드
├── pages/         # /, /project/:id, /qa/:id, /admin, /auth/login
├── plugins/       # $api (쿠키 인증 + 401 자동 refresh), 인증 복구
├── stores/        # Pinia — auth, notifications(SSE)
├── types/         # 백엔드 DTO 1:1 타입
└── utils/         # 필터 공유, 날짜 포맷, 첨부 헬퍼
```

환경변수는 `NUXT_PUBLIC_API_BASE`(백엔드 URL), `NUXT_PUBLIC_UPLOAD_MAX_FILE_SIZE_MB` 를 사용합니다 — 루트 [.env.example](../.env.example) 참고.
