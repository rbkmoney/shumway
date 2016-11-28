#!/usr/bin/env bash

# Usage.
# this script needs at least TEMPLATE env variable
# TEMPLATE=create-db ./utils.sh
# TEMPLATE=create-dump DB_NAME=bustermaze DUMP_PATH=/tmp/buster.sql ./utils.sh
# TEMPLATE=restore-dump DB_NAME=bustermaze DUMP_PATH=/tmp/buster.sql ./utils.sh

# bash exit when one of commands fails
set -e
set -u

SCRIPT_DIR="$(dirname $0)"

DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
DB_USER="${DB_USER:-postgres}"
DB_NAME="${DB_NAME:-shumway}"
DB_PASSWORD="${DB_PASSWORD:-postgres}"
DB_SUPERUSER="${DB_SUPERUSER:-postgres}"
DB_SUPERUSER_PASSWORD="${DB_SUPERUSER_PASSWORD:-postgres}"

PGPASSWORD="${PGPASSWORD:-postgres}"


if [ $TEMPLATE = "create-dump" ]
then
    DEFAULT_DUMP_PATH=${SCRIPT_DIR}/dumps/$DB_NAME-$(date +%s).bak
    DUMP_PATH="${DUMP_PATH:-$DEFAULT_DUMP_PATH}"

    mkdir -p ${SCRIPT_DIR}/dumps

    PGPASSWORD=$PGPASSWORD pg_dump \
        -Fd \
        -v \
        -j 4 \
        -h $DB_HOST \
        -U $DB_SUPERUSER \
        -f $DUMP_PATH \
        $DB_NAME

    echo "DUMP_PATH=$DUMP_PATH"
elif [ $TEMPLATE = "restore-dump" ]
then
    TEMPLATE=drop-db DB_NAME=$DB_NAME ${SCRIPT_DIR}/utils.sh
    TEMPLATE=create-db DB_NAME=$DB_NAME ${SCRIPT_DIR}/utils.sh

    PGPASSWORD=$PGPASSWORD pg_restore \
        -d $DB_NAME \
        -e \
        -v \
        -j 4 \
        -h $DB_HOST \
        -U $DB_SUPERUSER \
        $DUMP_PATH
else
    mkdir -p ${SCRIPT_DIR}/sql
    # generate sql from template
    DB_NAME=$DB_NAME ${SCRIPT_DIR}/template/"$TEMPLATE".tpl > ${SCRIPT_DIR}/sql/"$TEMPLATE".sql

    # execute psql
    PGPASSWORD=$PGPASSWORD psql \
        -X \
        -U $DB_SUPERUSER \
        -h $DB_HOST \
        -p $DB_PORT \
        -f ${SCRIPT_DIR}/sql/"$TEMPLATE".sql \
        --echo-all \
        --set AUTOCOMMIT=off \
        -v ON_ERROR_STOP=1

    PSQL_EXIT_STATUS=$?

    if [ $PSQL_EXIT_STATUS != 0 ]; then
        echo "psql failed while trying to run this sql script." 1>&2
        exit $PSQL_EXIT_STATUS
    fi

    echo "sql script successful."
    exit 0
fi


