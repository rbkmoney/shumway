#!/bin/bash
cat <<EOF
create user ${DB_USER} with password '${DB_PASSWORD}';
grant all privileges on database ${DB_NAME} to ${DB_USER};
EOF


