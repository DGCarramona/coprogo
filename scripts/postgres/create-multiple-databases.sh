#!/usr/bin/env bash
set -euo pipefail

if [[ -z "${POSTGRES_MULTIPLE_DATABASES:-}" ]]; then
  exit 0
fi

for database in ${POSTGRES_MULTIPLE_DATABASES//,/ }; do
  if [[ ! "$database" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]]; then
    echo "Invalid database name: $database" >&2
    exit 1
  fi

  psql --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" --command "CREATE DATABASE $database;"
done
