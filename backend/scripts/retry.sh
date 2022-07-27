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
  if test -z "$FILE"
  then
    run-sql "insert into jobs (event_revision, event_timestamp, quick_settings, profile_id, payload) select event_revision, event_timestamp, quick_settings, profile_id, jsonb_build_object('type', 'process_command', 'subscription_id', payload->>'subscription_id', 'transaction_batch_id', payload->>'transaction_batch_id') from commands where event_revision = ${REVISION};"
  else
    run-sql "insert into jobs (event_revision, event_timestamp, quick_settings, profile_id, payload) select event_revision, event_timestamp, quick_settings, profile_id, jsonb_build_object('type', 'deliver_file', 'subscription_id', payload->>'subscription_id', 'transaction_batch_id', payload->>'transaction_batch_id', 'file', '${FILE}') from commands where event_revision = ${REVISION};"
  fi

  run-sql "select * from jobs order by created_at desc limit 10;"
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
