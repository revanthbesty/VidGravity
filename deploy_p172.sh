#!/usr/bin/env sh
# Operator-run deployment. This script does not deploy remotely on its own.
set -eu
cd "$(dirname "$0")"
ENV_FILE="${1:-.env.production}"
if [ ! -f "$ENV_FILE" ]; then
  echo "Missing $ENV_FILE. Copy .env.production.example and set a real domain and Caddy password hash." >&2
  exit 2
fi
for tool in docker curl; do
  command -v "$tool" >/dev/null 2>&1 || { echo "Missing required command: $tool" >&2; exit 2; }
done
docker info >/dev/null 2>&1 || { echo "Docker daemon is unavailable. Start Docker first." >&2; exit 2; }
docker compose version >/dev/null 2>&1 || { echo "Docker Compose v2 is required." >&2; exit 2; }
# Parse only non-secret domain value for post-deployment URL; Compose reads the same file for interpolation.
DOMAIN=$(sed -n 's/^VIDGRAVITY_DOMAIN=//p' "$ENV_FILE" | tail -n 1 | tr -d '\r')
case "$DOMAIN" in
  ''|*' '*|*'/'*|*'?'*|*'#'*) echo "VIDGRAVITY_DOMAIN is missing or malformed." >&2; exit 2 ;;
esac
case "$DOMAIN" in
  *.*) ;;
  *) echo "VIDGRAVITY_DOMAIN must be a fully qualified domain name." >&2; exit 2 ;;
esac
docker compose --env-file "$ENV_FILE" -f compose.production.yml config --quiet
docker compose --env-file "$ENV_FILE" -f compose.production.yml up -d --build
printf '\nDeployment command completed. Allow DNS/TLS provisioning time, then test in a browser:\n  https://%s/api/health\n' "$DOMAIN"
printf 'Expected: HTTP Basic Auth challenge, then authenticated JSON health response.\n'
printf 'This script does not verify external downloads, payment webhooks, network egress isolation, or Android.\n'
