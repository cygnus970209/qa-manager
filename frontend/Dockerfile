# syntax=docker/dockerfile:1.7
# ────────── Stage 1: build ──────────
FROM node:22-alpine AS build
WORKDIR /app

# 의존성 캐싱
COPY package.json package-lock.json ./
RUN npm ci

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
