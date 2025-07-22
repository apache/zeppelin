#!/bin/bash
#
# Copyright 2007 The Apache Software Foundation
#
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
# List Zeppelin's cron jobs
#


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
