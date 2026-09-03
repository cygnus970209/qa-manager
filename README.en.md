# QA Manager

[한국어](README.md) | **English**

A team QA workflow manager: projects → updates (releases) → QA items.
Real-time notifications, an MS Teams bot, GitHub issue and commit tracking, test cases and test runs — the whole in-house QA loop in one place.

![Nuxt 4](https://img.shields.io/badge/Nuxt-4-00DC82?logo=nuxt&logoColor=white)
![Vue 3](https://img.shields.io/badge/Vue-3-4FC08D?logo=vuedotjs&logoColor=white)
![Spring Boot 4](https://img.shields.io/badge/Spring%20Boot-4-6DB33F?logo=springboot&logoColor=white)
![Java 25](https://img.shields.io/badge/Java-25-orange)
![MariaDB 11](https://img.shields.io/badge/MariaDB-11-003545?logo=mariadb&logoColor=white)
![Redis](https://img.shields.io/badge/Redis-7-DC382D?logo=redis&logoColor=white)
![Docker Compose](https://img.shields.io/badge/Docker-Compose-2496ED?logo=docker&logoColor=white)

**🔗 Live demo: https://qa-manager-demo.cygnus2.com** — a static demo that runs without a backend. Click a demo account on the login screen to jump right in (the UI and seed data follow your browser language).

## Screenshots

| Dashboard                                | QA detail                               |
|------------------------------------------|-----------------------------------------|
| ![Dashboard](docs/images/dashboard.jpg)  | ![QA detail](docs/images/qa-detail.jpg) |

| Notifications                                    | Settings                              |
|--------------------------------------------------|---------------------------------------|
| ![Notifications](docs/images/notifications.jpg)  | ![Settings](docs/images/settings.jpg) |

| Workflow graph → test cases                | Test run                               |
|--------------------------------------------|----------------------------------------|
| ![Flow editor](docs/images/test-flow.jpg)  | ![Test run](docs/images/test-run.jpg)  |

## Features

- **QA workflow** — projects → updates → QA items. Six statuses, four priorities, a tester plus two assignees, change history, `#number` cross-references
- **Test cases & test runs** — suites and steps, cases generated from a workflow graph, per-release runs (PC/Android/iOS) with failure → QA linking
- **Collaboration** — comments, replies, `@mentions`, emoji reactions, image/PDF attachments
- **Notifications** — in-app real-time (SSE) plus 1:1 MS Teams bot messages. The notifications page shows the list and the QA item side by side
- **GitHub integration** — issues created and synced from QA items, commits tracked via `#number` in commit messages
- **UI** — project tree sidebar (`⌘B` to collapse), Discord-style settings, dark mode, Korean/English
- **Desktop app** — [qa-manager-desktop](https://github.com/cygnus970209/qa-manager-desktop) (Tauri) with native notifications and auto-update
- **Security & operations** — admin/member roles, IP-conditional email OTP, API audit log, zero-downtime Docker Compose deploys, demo-mode build

See [docs/FEATURES.md](docs/FEATURES.md) (Korean) for a screen-by-screen guide.

## Tech stack

| Area | Stack |
|---|---|
| Frontend | Nuxt 4 (SSR) · Vue 3 · Pinia · Tailwind CSS · TypeScript · Vue Flow |
| Backend | Spring Boot 4 · Java 25 · Spring Security · JPA |
| Data | MariaDB 11 (Flyway) · Redis 7 · AWS S3 |
| Integrations & deploy | SSE · GitHub App · MS Teams Bot Framework · Docker Compose · Nginx |

## Quick start

```bash
cp .env.example .env          # fill in JWT_SECRET, DB and AWS values
docker compose up -d --build  # frontend http://localhost:3247 · backend http://localhost:8357
```

Log in with the seed account `kimminjun` / `1234`.
Running on the host, zero-downtime deploys (`./deploy.sh`), reverse proxy and backups are covered in [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md) (Korean).

## Documentation

All detailed docs are in Korean.

| Document | Contents |
|---|---|
| [docs/README.md](docs/README.md) | Wiki home — reading guide by audience |
| [docs/FEATURES.md](docs/FEATURES.md) | Feature guide, screen by screen |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | System layout · repository structure · technical decisions · DB schema history |
| [docs/API.md](docs/API.md) | REST API reference |
| [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md) | Deployment · environment variables · operations · troubleshooting |
| [docs/GITHUB_INTEGRATION.md](docs/GITHUB_INTEGRATION.md) | GitHub App integration |
| [docs/TEAMS_INTEGRATION.md](docs/TEAMS_INTEGRATION.md) | MS Teams bot notifications |
| [docs/DEMO.md](docs/DEMO.md) | Demo mode |
| [docs/SECURITY_IP_OTP_LOGIN.md](docs/SECURITY_IP_OTP_LOGIN.md) | IP-conditional email OTP design |
