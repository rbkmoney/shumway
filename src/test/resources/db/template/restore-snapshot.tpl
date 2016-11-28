#!/bin/bash
cat <<EOF
drop database if exists ${DB_NAME};
create database ${DB_NAME} with template ${SNAPSHOT};
EOF