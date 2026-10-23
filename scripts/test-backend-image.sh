#!/usr/bin/env bash
set -euo pipefail

# Run from the repository root. This exercises the actual Dockerfile and Flyway
# migrations, without a Neon account or production credentials.
image="${BACKEND_TEST_IMAGE:-coprogo-backend:smoke}"
docker build --file coprogo/Dockerfile --tag "$image" .

network_id=''
database_id=''
application_id=''
cleanup() {
  status=$?
  if (( status != 0 )) && [[ -n "$application_id" ]]; then
    docker logs "$application_id" >&2 || true
  fi
  [[ -z "$application_id" ]] || docker rm --force "$application_id" >/dev/null || true
  [[ -z "$database_id" ]] || docker rm --force "$database_id" >/dev/null || true
  [[ -z "$network_id" ]] || docker network rm "$network_id" >/dev/null || true
  exit "$status"
}
trap cleanup EXIT

network_id=$(docker network create "coprogo-image-test-$$-$RANDOM")
database_id=$(docker run --detach --network "$network_id" --network-alias database \
  --tmpfs /var/lib/postgresql/data \
  --env POSTGRES_DB=coprogo --env POSTGRES_USER=coprogo \
  --env POSTGRES_PASSWORD=image-test-only postgres:17)

database_ready=false
for ((attempt = 0; attempt < 60; attempt++)); do
  if docker exec "$database_id" pg_isready --host 127.0.0.1 --username coprogo --dbname coprogo >/dev/null 2>&1; then
    database_ready=true
    break
  fi
  sleep 1
done
[[ "$database_ready" == true ]] || { docker logs "$database_id"; exit 1; }

application_id=$(docker run --detach --network "$network_id" \
  --memory 512m --cpus 0.5 --read-only --tmpfs /tmp:rw,noexec,nosuid,size=32m \
  --cap-drop ALL --security-opt no-new-privileges \
  --publish 127.0.0.1::8080 \
  --env DB_HOST=database --env DB_NAME=coprogo --env DB_USERNAME=coprogo \
  --env DB_PASSWORD=image-test-only --env GOOGLE_ID_TOKEN_AUDIENCES=image-test \
  --env COPROGO_DOCUMENTS_S3_BUCKET=image-test \
  --env COPROGO_DOCUMENTS_S3_REGION=eu-west-1 \
  --env COPROGO_DOCUMENTS_S3_ENDPOINT=http://localhost:9090 \
  --env AWS_ACCESS_KEY_ID=image-test --env AWS_SECRET_ACCESS_KEY=image-test \
  --env MICRONAUT_SERVER_PORT=8080 "$image")
address=$(docker port "$application_id" 8080/tcp)

application_ready=false
for ((attempt = 0; attempt < 120; attempt++)); do
  status=$(curl --silent --output /dev/null --write-out '%{http_code}' \
    --max-time 2 "http://$address/api/group-invitations/pending" || true)
  if [[ "$status" == 401 ]]; then
    application_ready=true
    break
  fi
  [[ "$(docker inspect --format '{{.State.Running}}' "$application_id")" == true ]] || break
  sleep 1
done
[[ "$application_ready" == true ]] || { echo 'Expected an authenticated route to return HTTP 401.' >&2; exit 1; }

expected_migrations=$(find coprogo/src/main/resources/db/migration -name 'V*.sql' -type f | wc -l)
actual_migrations=$(docker exec "$database_id" psql --username coprogo --dbname coprogo \
  --tuples-only --no-align --command 'SELECT count(*) FROM flyway_schema_history WHERE success')
[[ "$actual_migrations" -eq "$expected_migrations" ]] || { echo 'Not all migrations were applied.' >&2; exit 1; }
[[ "$(docker inspect --format '{{.Config.User}}' "$application_id")" == '65532:65532' ]]
[[ "$(docker inspect --format '{{.State.OOMKilled}}' "$application_id")" == false ]]
docker stats --no-stream --format 'Memory: {{.MemUsage}}' "$application_id"

docker stop --time 15 "$application_id" >/dev/null
case "$(docker inspect --format '{{.State.ExitCode}}' "$application_id")" in
  0|143) ;;
  *) echo 'The backend did not stop cleanly.' >&2; exit 1 ;;
esac
echo "Image smoke test passed: HTTP authentication, $actual_migrations migrations, non-root, read-only, 512 MiB, shutdown."
