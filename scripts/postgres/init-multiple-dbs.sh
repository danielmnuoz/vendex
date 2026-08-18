#!/bin/bash
# Idempotently creates the per-service databases in POSTGRES_MULTIPLE_DATABASES.
# The official image runs this once during first boot, and Compose also runs it
# after Postgres becomes healthy so newly-added services work with old volumes.
set -e
set -u

if [ -n "${POSTGRES_MULTIPLE_DATABASES:-}" ]; then
    connection_args=(--username "$POSTGRES_USER")
    if [ -n "${POSTGRES_HOST:-}" ]; then
        connection_args+=(--host "$POSTGRES_HOST")
    fi

    for db in $(echo "$POSTGRES_MULTIPLE_DATABASES" | tr ',' ' '); do
        if [[ ! "$db" =~ ^[a-zA-Z_][a-zA-Z0-9_]*$ ]]; then
            echo "Invalid database name: $db" >&2
            exit 1
        fi

        if psql "${connection_args[@]}" --dbname postgres --tuples-only --no-align \
            --command "SELECT 1 FROM pg_database WHERE datname = '$db'" | grep -qx 1; then
            echo "Database already exists: $db"
        else
            echo "Creating database: $db"
            createdb "${connection_args[@]}" "$db"
        fi

        psql -v ON_ERROR_STOP=1 "${connection_args[@]}" --dbname postgres \
            --command "GRANT ALL PRIVILEGES ON DATABASE $db TO $POSTGRES_USER"
    done
fi
