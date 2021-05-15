#!/bin/bash
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

# pre-requisites for checking that we're running in container
if [ -f /proc/self/cgroup ] && [ -n "$(command -v getent)" ]; then
    # checks if we're running in container...
    if awk -F: '/cpu/ && $3 ~ /^\/$/{ c=1 } END { exit c }' /proc/self/cgroup; then
        # Check whether there is a passwd entry for the container UID
        myuid="$(id -u)"
        mygid="$(id -g)"
        # turn off -e for getent because it will return error code in anonymous uid case
        set +e
        uidentry="$(getent passwd "$myuid")"
        set -e
        
        # If there is no passwd entry for the container UID, attempt to create one
        if [ -z "$uidentry" ] ; then
            if [ -w /etc/passwd ] ; then
                echo "zeppelin:x:$myuid:$mygid:anonymous uid:$ZEPPELIN_HOME:/bin/false" >> /etc/passwd
            else
                echo "Container ENTRYPOINT failed to add passwd entry for anonymous UID"
            fi
        fi
    fi
fi

function usage() {
    echo "Usage: bin/zeppelin.sh [--config <conf-dir>] [--run <noteId>]"
}

POSITIONAL=()
while [[ $# -gt 0 ]]
do
  key="$1"
  case $key in
    --config)
    export ZEPPELIN_CONF_DIR="$2"
    shift # past argument
    shift # past value
    ;;
    --run)
    export ZEPPELIN_NOTEBOOK_RUN_ID="$2"
    shift # past argument
    shift # past value
    ;;
    -h|--help)
        usage
        exit 0
        ;;
    *)
        echo "Unsupported argument."
        usage
        exit 1
        ;;
  esac
done
set -- "${POSITIONAL[@]}" # restore positional parameters

bin="$(dirname "${BASH_SOURCE-$0}")"
bin="$(cd "${bin}">/dev/null; pwd)"

. "${bin}/common.sh"

check_java_version

if [ "$1" == "--version" ] || [ "$1" == "-v" ]; then
    getZeppelinVersion
fi

HOSTNAME=$(hostname)
ZEPPELIN_LOGFILE="${ZEPPELIN_LOG_DIR}/zeppelin-${ZEPPELIN_IDENT_STRING}-${HOSTNAME}.log"

ZEPPELIN_SERVER=org.apache.zeppelin.server.ZeppelinServer
JAVA_OPTS+=" -Dzeppelin.log.file=${ZEPPELIN_LOGFILE}"

# construct classpath
if [[ -d "${ZEPPELIN_HOME}/zeppelin-interpreter/target/classes" ]]; then
  ZEPPELIN_CLASSPATH+=":${ZEPPELIN_HOME}/zeppelin-interpreter/target/classes"
fi

if [[ -d "${ZEPPELIN_HOME}/zeppelin-zengine/target/classes" ]]; then
  ZEPPELIN_CLASSPATH+=":${ZEPPELIN_HOME}/zeppelin-zengine/target/classes"
fi

if [[ -d "${ZEPPELIN_HOME}/zeppelin-server/target/classes" ]]; then
  ZEPPELIN_CLASSPATH+=":${ZEPPELIN_HOME}/zeppelin-server/target/classes"
fi

addJarInDir "${ZEPPELIN_HOME}"
addJarInDir "${ZEPPELIN_HOME}/lib"
addJarInDir "${ZEPPELIN_HOME}/lib/interpreter"
addJarInDir "${ZEPPELIN_HOME}/zeppelin-interpreter/target/lib"
addJarInDir "${ZEPPELIN_HOME}/zeppelin-zengine/target/lib"
addJarInDir "${ZEPPELIN_HOME}/zeppelin-server/target/lib"
addJarInDir "${ZEPPELIN_HOME}/zeppelin-web/target/lib"
addJarInDir "${ZEPPELIN_HOME}/zeppelin-web-angular/target/lib"

ZEPPELIN_CLASSPATH="$CLASSPATH:$ZEPPELIN_CLASSPATH"

if [[ -n "${HADOOP_CONF_DIR}" ]] && [[ -d "${HADOOP_CONF_DIR}" ]]; then
  ZEPPELIN_CLASSPATH+=":${HADOOP_CONF_DIR}"
fi

if [[ ! -d "${ZEPPELIN_LOG_DIR}" ]]; then
  echo "Log dir doesn't exist, create ${ZEPPELIN_LOG_DIR}"
  mkdir -p "${ZEPPELIN_LOG_DIR}"
fi

if [[ ! -d "${ZEPPELIN_PID_DIR}" ]]; then
  echo "Pid dir doesn't exist, create ${ZEPPELIN_PID_DIR}"
  mkdir -p "${ZEPPELIN_PID_DIR}"
fi

exec $ZEPPELIN_RUNNER $JAVA_OPTS -cp $ZEPPELIN_CLASSPATH_OVERRIDES:${ZEPPELIN_CLASSPATH} $ZEPPELIN_SERVER "$@"
