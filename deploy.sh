#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────
# QA Manager 무중단 배포 (blue / green)
#
# backend·frontend 를 파랑(기본 서비스)과 초록(profile: green) 두 벌로 두고,
# 지금 켜져 있는 색의 반대 색을 새 이미지로 띄운 뒤 정상(healthy)이 되면 이전 색을 내린다.
# 두 색이 겹치는 동안 nginx 가 둘 다에게 요청을 보내므로(upstream 에 두 포트 등록) 끊김이 없다.
#
# 사용:   git pull && ./deploy.sh
# 전제:   docs/nginx.example.conf 처럼 upstream 에 파랑/초록 포트가 모두 있을 것
#         (백엔드 8357/8358, 프론트 3247/3248). 없으면 초록이 켜진 동안 요청이 가지 않는다.
# 환경:   ENV_FILE (기본 .env) / HEALTH_TIMEOUT (기본 180초)
# 주의:   새 백엔드가 뜨면서 Flyway 마이그레이션이 먼저 적용되고 잠시 옛 백엔드도 같은 DB 를 쓴다.
#         컬럼 삭제·이름 변경처럼 옛 코드가 깨지는 스키마 변경은 두 단계로 나눠 배포할 것.
# ─────────────────────────────────────────────────────────────
set -euo pipefail
cd "$(dirname "$0")"

ENV_FILE=${ENV_FILE:-.env}
HEALTH_TIMEOUT=${HEALTH_TIMEOUT:-180}
STATE_FILE=.deploy-active

compose() { docker compose --env-file "$ENV_FILE" --profile green "$@"; }
services_of() { if [ "$1" = blue ]; then echo "backend frontend"; else echo "backend-green frontend-green"; fi; }
containers_of() { if [ "$1" = blue ]; then echo "qa-manager-backend qa-manager-frontend"; else echo "qa-manager-backend-green qa-manager-frontend-green"; fi; }
running() { docker ps --format '{{.Names}}' | grep -qx "$1"; }

# 현재 색: 상태 파일 → 없으면 떠 있는 컨테이너로 판단 → 둘 다 없으면 첫 배포
active=$(cat "$STATE_FILE" 2>/dev/null || true)
if [ -z "$active" ]; then
  if running qa-manager-backend-green; then active=green
  elif running qa-manager-backend; then active=blue
  else active=none; fi
fi
case "$active" in
  green) next=blue ;;
  blue)  next=green ;;
  *)     next=blue ;;
esac
echo "▶ 현재 활성: $active → 새로 띄울 색: $next"

echo "▶ 이미지 빌드 (backend, frontend) — 두 색이 같은 이미지 태그를 쓴다"
compose build backend frontend

echo "▶ $next 기동"
# shellcheck disable=SC2046
compose up -d --no-deps --no-build $(services_of "$next")

echo "▶ 정상(healthy) 대기 — 최대 ${HEALTH_TIMEOUT}초"
deadline=$((SECONDS + HEALTH_TIMEOUT))
for c in $(containers_of "$next"); do
  until [ "$(docker inspect --format '{{.State.Health.Status}}' "$c" 2>/dev/null || echo missing)" = healthy ]; do
    if [ "$SECONDS" -ge "$deadline" ]; then
      echo "✖ $c 가 시간 안에 healthy 가 되지 않았습니다. 최근 로그:" >&2
      docker logs --tail 60 "$c" >&2 || true
      if [ "$active" != none ]; then
        echo "▶ 롤백: $next 를 내리고 $active 를 그대로 둡니다" >&2
        # shellcheck disable=SC2046
        compose stop $(services_of "$next") >/dev/null 2>&1 || true
      fi
      exit 1
    fi
    sleep 3
  done
  echo "  ✔ $c healthy"
done

if [ "$active" != none ] && [ "$active" != "$next" ]; then
  echo "▶ 이전 색($active) 정리 — 처리 중인 요청은 마치고 내려간다 (graceful)"
  # shellcheck disable=SC2046
  compose stop $(services_of "$active")
fi
echo "$next" > "$STATE_FILE"
echo "✅ 배포 완료 — $next 활성"
