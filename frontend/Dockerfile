# syntax=docker/dockerfile:1.7
# ────────── Stage 1: build ──────────
FROM node:22-alpine AS build
WORKDIR /app

# 의존성 캐싱
# node:22-alpine 의 npm@10 은 로컬 npm@11 이 쓴 lockfile 을 읽지 못하는 케이스가 있어
# (hoisting/dedupe 차이로 npm ci 가 "Missing: ..." EUSAGE 실패) npm 메이저를 로컬과 맞춘다.
COPY package.json package-lock.json ./
RUN npm install -g npm@^11 --no-fund --no-audit && npm ci

COPY . .
RUN npm run build

# ────────── Stage 2: runtime ──────────
FROM node:22-alpine
WORKDIR /app

# 실행용 비루트 유저
RUN addgroup -S app && adduser -S app -G app

COPY --from=build --chown=app:app /app/.output /app/.output

USER app
EXPOSE 3000

ENV HOST=0.0.0.0 \
    PORT=3000 \
    NODE_ENV=production

CMD ["node", "/app/.output/server/index.mjs"]
