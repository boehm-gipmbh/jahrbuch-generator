#!/bin/bash
set -e

/deployments/migrate.sh

exec /deployments/application \
    -Dquarkus.http.host=0.0.0.0 \
    -Dquarkus.datasource.reactive.url=$FREE_DATABASE_URL \
    -Dquarkus.datasource.jdbc=false \
    -Dquarkus.flyway.migrate-at-start=false \
    -Dquarkus.hibernate-orm.database.generation=none
