#!/bin/bash
# Initialize multiple databases inside a single Postgres container.
# Reads POSTGRES_MULTIPLE_DATABASES (comma-separated) and creates each one.
# Adapted from https://github.com/mrts/docker-postgresql-multiple-databases.
set -e
set -u

if [ -n "${POSTGRES_MULTIPLE_DATABASES:-}" ]; then
    for db in $(echo "$POSTGRES_MULTIPLE_DATABASES" | tr ',' ' '); do
        echo "Creating database '$db'"
        psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" <<-EOSQL
            CREATE DATABASE "$db";
            GRANT ALL PRIVILEGES ON DATABASE "$db" TO "$POSTGRES_USER";
EOSQL
    done
fi
