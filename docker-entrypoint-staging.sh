#!/bin/bash
set -e

# FREE_DATABASE_URL: postgres://user:pass@host:port/db
# Quarkus reactive akzeptiert dieses Format direkt.
# JDBC braucht jdbc:postgresql://host:port/db + separate credentials.

DB_URL_NO_SCHEME="${FREE_DATABASE_URL#postgres://}"
DB_USERPASS="${DB_URL_NO_SCHEME%@*}"
DB_USER="${DB_USERPASS%:*}"
DB_PASS="${DB_USERPASS#*:}"
DB_HOSTPORTDB="${DB_URL_NO_SCHEME#*@}"
DB_HOSTPORT="${DB_HOSTPORTDB%/*}"
DB_NAME="${DB_HOSTPORTDB#*/}"
JDBC_URL="jdbc:postgresql://${DB_HOSTPORT}/${DB_NAME}"

exec java \
  -Dquarkus.http.host=0.0.0.0 \
  -Djava.util.logging.manager=org.jboss.logmanager.LogManager \
  -Dquarkus.datasource.reactive.url="${FREE_DATABASE_URL}" \
  -Dquarkus.datasource.jdbc.url="${JDBC_URL}" \
  -Dquarkus.datasource.username="${DB_USER}" \
  -Dquarkus.datasource.password="${DB_PASS}" \
  -Dquarkus.flyway.migrate-at-start=true \
  -Dquarkus.hibernate-orm.database.generation=none \
  -jar /deployments/quarkus-run.jar
