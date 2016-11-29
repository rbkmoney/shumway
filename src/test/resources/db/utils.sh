#!/usr/bin/env bash

# Usage.
# this script needs at least TEMPLATE env variable
# TEMPLATE=create-db ./utils.sh
# TEMPLATE=create-dump DB_NAME=bustermaze DUMP_PATH=/tmp/buster.sql ./utils.sh
# TEMPLATE=restore-dump DB_NAME=bustermaze DUMP_PATH=/tmp/buster.sql ./utils.sh

# bash exit when one of commands fails
set -e
set -u

export DB_HOST="${DB_HOST:-localhost}"
export DB_PORT="${DB_PORT:-5432}"
export DB_USER="${DB_USER:-postgres}"
export DB_NAME="${DB_NAME:-shumway}"
export DB_SUPERUSER="${DB_SUPERUSER:-postgres}"
export PGPASSWORD="${PGPASSWORD:-postgres}"
export SNAPSHOT_SUFFIX="${SNAPSHOT_SUFFIX:-1}"

export SCRIPT_DIR="$(dirname $0)"
export SNAPSHOT=snapshot_of_${DB_NAME}_${SNAPSHOT_SUFFIX}

if [ $TEMPLATE = "create-dump" ]
then
    DEFAULT_DUMP_PATH=${SCRIPT_DIR}/dumps/$DB_NAME-$(date +%s).bak
    DUMP_PATH="${DUMP_PATH:-$DEFAULT_DUMP_PATH}"

    # relative path to absolute path
    if ! [[ "${DUMP_PATH}" == \/* ]]
    then
        DUMP_PATH=${SCRIPT_DIR}/dumps/${DUMP_PATH}
    fi

    mkdir -p ${SCRIPT_DIR}/dumps

    PGPASSWORD=$PGPASSWORD pg_dump \
        -Fd \
        -v \
        -j 4 \
        -U $DB_SUPERUSER \
        -h $DB_HOST \
        -p $DB_PORT \
        -f $DUMP_PATH \
        $DB_NAME

    echo "DUMP_PATH=$DUMP_PATH"
elif [ $TEMPLATE = "restore-dump" ]
then
    TEMPLATE=drop-db ${SCRIPT_DIR}/utils.sh
    TEMPLATE=create-db ${SCRIPT_DIR}/utils.sh

    # relative path to absolute path
    if ! [[ "${DUMP_PATH}" == \/* ]]
    then
        DUMP_PATH=${SCRIPT_DIR}/dumps/${DUMP_PATH}
    fi

    pg_restore \
        -d $DB_NAME \
        -e \
        -v \
        -j 4 \
        -U $DB_SUPERUSER \
        -h $DB_HOST \
        -p $DB_PORT \
        $DUMP_PATH
elif [ $TEMPLATE = "psql-command" ]
then
    # execute psql
    psql \
        -X \
        -U $DB_SUPERUSER \
        -h $DB_HOST \
        -p $DB_PORT \
        -c "${PSQL_COMMAND}" \
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
else
    mkdir -p ${SCRIPT_DIR}/sql
    # generate sql from template
    ${SCRIPT_DIR}/template/"$TEMPLATE".tpl > ${SCRIPT_DIR}/sql/"$TEMPLATE".sql

    # execute psql
    psql \
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


