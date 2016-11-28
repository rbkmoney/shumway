#!/bin/bash
cat <<EOF
drop database if exists ${SNAPSHOT};
-- drop connections
BEGIN;
SELECT pg_terminate_backend(pid)
FROM pg_stat_activity
WHERE pid <> pg_backend_pid() AND datname = '${DB_NAME}';
COMMIT;

create database ${SNAPSHOT} with template ${DB_NAME};
EOF