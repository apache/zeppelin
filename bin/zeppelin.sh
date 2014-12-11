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
# Run Zeppelin 
#

function usage() {
  echo "Usage: bin/zeppelin.sh [spark options] [application options]"
  exit 0
}

bin=$(dirname "${BASH_SOURCE-$0}")
bin=$(cd "${bin}">/dev/null; pwd)

. "${bin}/common.sh"

HOSTNAME=$(hostname)
ZEPPELIN_LOGFILE="${ZEPPELIN_LOG_DIR}/zeppelin-${ZEPPELIN_IDENT_STRING}-${HOSTNAME}.log"
LOG="${ZEPPELIN_LOG_DIR}/zeppelin-cli-${ZEPPELIN_IDENT_STRING}-${HOSTNAME}.out"
  
ZEPPELIN_SERVER=com.nflabs.zeppelin.server.ZeppelinServer
JAVA_OPTS+=" -Dzeppelin.log.file=${ZEPPELIN_LOGFILE}"

if [[ ! -d "${ZEPPELIN_LOG_DIR}" ]]; then
  echo "Log dir doesn't exist, create ${ZEPPELIN_LOG_DIR}"
  $(mkdir -p "${ZEPPELIN_LOG_DIR}")
fi

if [[ ! -d "${ZEPPELIN_PID_DIR}" ]]; then
  echo "Pid dir doesn't exist, create ${ZEPPELIN_PID_DIR}"
  $(mkdir -p "${ZEPPELIN_PID_DIR}")
fi

if [[ ! -d "${ZEPPELIN_NOTEBOOK_DIR}" ]]; then
  echo "Pid dir doesn't exist, create ${ZEPPELIN_NOTEBOOK_DIR}"
  $(mkdir -p "${ZEPPELIN_NOTEBOOK_DIR}")
fi

if [[ ! -z "${SPARK_HOME}" ]]; then
  source "${SPARK_HOME}/bin/utils.sh"
  SUBMIT_USAGE_FUNCTION=usage
  gatherSparkSubmitOpts "$@"
  ZEPPELIN_RUNNER="${SPARK_HOME}/bin/spark-submit"
  $(exec $ZEPPELIN_NICENESS $ZEPPELIN_RUNNER --class $ZEPPELIN_SERVER "${SUBMISSION_OPTS[@]}" --driver-java-options -Dzeppelin.log.file=$ZEPPELIN_LOGFILE spark-shell "${APPLICATION_OPTS[@]}")
else
  $(exec $ZEPPELIN_RUNNER $JAVA_OPTS -cp $CLASSPATH $ZEPPELIN_SERVER "$@")
fi
