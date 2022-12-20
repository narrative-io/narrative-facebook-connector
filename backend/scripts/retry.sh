#!/bin/bash

set -e

function ssm {
  aws ssm get-parameter --name $1 --with-decryption --query 'Parameter.Value' --output text
}
function confirm {
  read -p "Confirm (y/n)? " repl
  if [[ ! $repl =~ ^[Yy](es)?$ ]]
  then
    exit 1
  fi
}
function run-sql {
  psql "postgres://root:${master_password}@${db_host}:5432/facebookconnector" -c "$1"
}
function retry {
  SUBSELECT="select jsonb_object_keys(status->'files') as name from commands where event_revision=${REVISION}"
  SELECT="select jsonb_build_object('type', 'file', 'event_revision', '${REVISION}', 'file', files.name) from ($SUBSELECT) as files"

  if [ -n "$FILE" ]; then
    SELECT="${SELECT} where files.name='${FILE}'"
  fi

  run-sql "insert into queue (payload) $SELECT;"
  run-sql "select * from queue order by id limit 10;"
}

STAGE=$1
REVISION=$2
FILE=$3

case "$STAGE" in
  "")
    echo -e "Usage: $0 <stage: local | dev | demo | prod>" 1>&2
    exit 1
    ;;
  local)
    db_host="localhost"
    master_password="narrative"
    ;;
  *)
    db_host="facebookconnector-db-${STAGE}.c4sgf4vjfwdh.us-east-1.rds.amazonaws.com"
    master_password=$(ssm /${STAGE}/rds-master-password)
    ;;
esac

printf "Selected stage: \033[1m${STAGE}\033[0m\n"
printf "Event Revision: \033[1m${REVISION}\033[0m\n"
printf "File to retry: \033[1m${FILE:-ALL}\033[0m\n"

confirm
retry
