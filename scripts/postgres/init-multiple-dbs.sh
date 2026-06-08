#!/bin/bash
# Creates the per-service databases listed in POSTGRES_MULTIPLE_DATABASES.
# Postgres' official image runs every *.sh under /docker-entrypoint-initdb.d/
# on first boot — that's where this lands via docker-compose.
set -e
set -u

if [ -n "${POSTGRES_MULTIPLE_DATABASES:-}" ]; then
    for db in $(echo "$POSTGRES_MULTIPLE_DATABASES" | tr ',' ' '); do
        echo "Creating database: $db"
        psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" <<-EOSQL
            CREATE DATABASE $db;
            GRANT ALL PRIVILEGES ON DATABASE $db TO $POSTGRES_USER;
EOSQL
    done
fi
