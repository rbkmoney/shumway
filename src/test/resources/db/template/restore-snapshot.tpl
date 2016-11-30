#!/bin/bash
cat <<EOF
-- drop connections
BEGIN;
SELECT pg_terminate_backend(pid)
FROM pg_stat_activity
WHERE pid <> pg_backend_pid() AND datname = '${DB_NAME}';
COMMIT;

drop database if exists ${DB_NAME};
create database ${DB_NAME} with template ${SNAPSHOT};
EOF