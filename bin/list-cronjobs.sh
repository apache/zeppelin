#!/bin/bash

USAGE="Usage: bin/list-cronjobs.sh [ZEPPELIN_URL]"

PWD=$(cd `dirname $0`; pwd);
if [[ "$ZEPPELIN_HOME" == "" ]]; then
  export ZEPPELIN_HOME=$(dirname $PWD)
fi

ZEPPELIN_URL=$1

TEMP_FILE=$PWD/$(date +%s)
touch $TEMP_FILE
for note_json_file in `grep '"cron":' ${ZEPPELIN_HOME}/notebook -r | egrep -v '""' | awk -F: '{print $1}'`
do
  CRON_EXPR=$(grep '"cron":' $note_json_file)
  NOTE_NAME=$(grep '^  "name":' $note_json_file)
  NOTE_URL=${ZEPPELIN_URL}notebook/$(echo $note_json_file | awk -F/ '{HASH=NF-1; print $HASH}')
echo "$CRON_EXPR  $NOTE_NAME  $NOTE_URL" >> $TEMP_FILE
done

sort $TEMP_FILE
rm -vf $TEMP_FILE
