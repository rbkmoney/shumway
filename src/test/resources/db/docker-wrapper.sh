#!/usr/bin/env bash

read -d '' COMMAND_1 << EOF
    TEMPLATE="${TEMPLATE}" \
    DB_HOST="${DB_HOST}" \
    DB_PORT="${DB_PORT}" \
    DB_USER="${DB_USER}" \
    DB_NAME="${DB_NAME}" \
    DB_SUPERUSER="${DB_SUPERUSER}" \
    PGPASSWORD="${PGPASSWORD}" \
    SNAPSHOT_SUFFIX="${SNAPSHOT_SUFFIX}" \
    DUMP_PATH="${DUMP_PATH}" \
    PSQL_COMMAND="${PSQL_COMMAND}" \
    ${UTILS_SH_IN_CONTAINER}
EOF

echo ${COMMAND_1}

docker exec ${CONTAINER_ID} bash -c "
    ${COMMAND_1}
"

