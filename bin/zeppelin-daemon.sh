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
# description: Start and stop daemon script for.
#

USAGE="-e Usage: zeppelin-daemon.sh\n\t
        [--config <conf-dir>] {start|stop|upstart|restart|reload|status}\n\t
        [--version | -v]"

if [[ "$1" == "--config" ]]; then
  shift
  conf_dir="$1"
  if [[ ! -d "${conf_dir}" ]]; then
    echo "ERROR : ${conf_dir} is not a directory"
    echo ${USAGE}
    exit 1
  else
    export ZEPPELIN_CONF_DIR="${conf_dir}"
  fi
  shift
fi

if [ -L ${BASH_SOURCE-$0} ]; then
  BIN=$(dirname $(readlink "${BASH_SOURCE-$0}"))
else
  BIN=$(dirname ${BASH_SOURCE-$0})
fi
BIN=$(cd "${BIN}">/dev/null; pwd)

. "${BIN}/common.sh"
. "${BIN}/functions.sh"

HOSTNAME=$(hostname)
ZEPPELIN_NAME="Zeppelin"
ZEPPELIN_LOGFILE="${ZEPPELIN_LOG_DIR}/zeppelin-${ZEPPELIN_IDENT_STRING}-${HOSTNAME}.log"
ZEPPELIN_OUTFILE="${ZEPPELIN_LOG_DIR}/zeppelin-${ZEPPELIN_IDENT_STRING}-${HOSTNAME}.out"
ZEPPELIN_PID="${ZEPPELIN_PID_DIR}/zeppelin-${ZEPPELIN_IDENT_STRING}-${HOSTNAME}.pid"
ZEPPELIN_MAIN=org.apache.zeppelin.server.ZeppelinServer
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

if [[ -n "${HADOOP_CONF_DIR}" ]] && [[ -d "${HADOOP_CONF_DIR}" ]]; then
  ZEPPELIN_CLASSPATH+=":${HADOOP_CONF_DIR}"
fi

# Add jdbc connector jar
# ZEPPELIN_CLASSPATH+=":${ZEPPELIN_HOME}/jdbc/jars/jdbc-connector-jar"

addJarInDir "${ZEPPELIN_HOME}"
addJarInDir "${ZEPPELIN_HOME}/lib"
addJarInDir "${ZEPPELIN_HOME}/lib/interpreter"
addJarInDir "${ZEPPELIN_HOME}/zeppelin-interpreter/target/lib"
addJarInDir "${ZEPPELIN_HOME}/zeppelin-zengine/target/lib"
addJarInDir "${ZEPPELIN_HOME}/zeppelin-server/target/lib"
addJarInDir "${ZEPPELIN_HOME}/zeppelin-web/target/lib"

CLASSPATH+=":${ZEPPELIN_CLASSPATH}"

if [[ "${ZEPPELIN_NICENESS}" = "" ]]; then
    export ZEPPELIN_NICENESS=0
fi

function initialize_default_directories() {
  if [[ ! -d "${ZEPPELIN_LOG_DIR}" ]]; then
    echo "Log dir doesn't exist, create ${ZEPPELIN_LOG_DIR}"
    $(mkdir -p "${ZEPPELIN_LOG_DIR}")
  fi

  if [[ ! -d "${ZEPPELIN_PID_DIR}" ]]; then
    echo "Pid dir doesn't exist, create ${ZEPPELIN_PID_DIR}"
    $(mkdir -p "${ZEPPELIN_PID_DIR}")
  fi
}

function wait_for_zeppelin_to_die() {
  local pid
  local count
  pid=$1
  timeout=$2
  count=0
  timeoutTime=$(date "+%s")
  let "timeoutTime+=$timeout"
  currentTime=$(date "+%s")
  forceKill=1

  while [[ $currentTime -lt $timeoutTime ]]; do
    $(kill ${pid} > /dev/null 2> /dev/null)
    if kill -0 ${pid} > /dev/null 2>&1; then
      sleep 3
    else
      forceKill=0
      break
    fi
    currentTime=$(date "+%s")
  done

  if [[ forceKill -ne 0 ]]; then
    $(kill -9 ${pid} > /dev/null 2> /dev/null)
  fi
}

function wait_zeppelin_is_up_for_ci() {
  if [[ "${CI}" == "true" ]]; then
    local count=0;
    while [[ "${count}" -lt 30 ]]; do
      curl -v localhost:8080 2>&1 | grep '200 OK'
      if [[ $? -ne 0 ]]; then
        sleep 1
        continue
      else
        break
      fi
        let "count+=1"
    done
  fi
}

function print_log_for_ci() {
  if [[ "${CI}" == "true" ]]; then
    tail -1000 "${ZEPPELIN_LOGFILE}" | sed 's/^/  /'
  fi
}

function check_if_process_is_alive() {
  local pid
  pid=$(cat ${ZEPPELIN_PID})
  if ! kill -0 ${pid} >/dev/null 2>&1; then
    action_msg "${ZEPPELIN_NAME} process died" "${SET_ERROR}"
    print_log_for_ci
    return 1
  fi
}

function upstart() {

  # upstart() allows zeppelin to be run and managed as a service
  # for example, this could be called from an upstart script in /etc/init
  # where the service manager starts and stops the process
  initialize_default_directories

  echo "ZEPPELIN_CLASSPATH: ${ZEPPELIN_CLASSPATH_OVERRIDES}:${CLASSPATH}" >> "${ZEPPELIN_OUTFILE}"

  $ZEPPELIN_RUNNER $JAVA_OPTS -cp $ZEPPELIN_CLASSPATH_OVERRIDES:$CLASSPATH $ZEPPELIN_MAIN >> "${ZEPPELIN_OUTFILE}"
}

function start() {
  local pid

  if [[ -f "${ZEPPELIN_PID}" ]]; then
    pid=$(cat ${ZEPPELIN_PID})
    if kill -0 ${pid} >/dev/null 2>&1; then
      echo "${ZEPPELIN_NAME} is already running"
      return 0;
    fi
  fi

  initialize_default_directories

  echo "ZEPPELIN_CLASSPATH: ${ZEPPELIN_CLASSPATH_OVERRIDES}:${CLASSPATH}" >> "${ZEPPELIN_OUTFILE}"

  nohup nice -n $ZEPPELIN_NICENESS $ZEPPELIN_RUNNER $JAVA_OPTS -cp $ZEPPELIN_CLASSPATH_OVERRIDES:$CLASSPATH $ZEPPELIN_MAIN >> "${ZEPPELIN_OUTFILE}" 2>&1 < /dev/null &
  pid=$!
  if [[ -z "${pid}" ]]; then
    action_msg "${ZEPPELIN_NAME} start" "${SET_ERROR}"
    return 1;
  else
    action_msg "${ZEPPELIN_NAME} start" "${SET_OK}"
    echo ${pid} > ${ZEPPELIN_PID}
  fi

  wait_zeppelin_is_up_for_ci
  sleep 2
  check_if_process_is_alive
}

function stop() {
  local pid

  # zeppelin daemon kill
  if [[ ! -f "${ZEPPELIN_PID}" ]]; then
    echo "${ZEPPELIN_NAME} is not running"
  else
    pid=$(cat ${ZEPPELIN_PID})
    if [[ -z "${pid}" ]]; then
      echo "${ZEPPELIN_NAME} is not running"
    else
      wait_for_zeppelin_to_die $pid 40
      $(rm -f ${ZEPPELIN_PID})
      action_msg "${ZEPPELIN_NAME} stop" "${SET_OK}"
    fi
  fi
}

function find_zeppelin_process() {
  local pid

  if [[ -f "${ZEPPELIN_PID}" ]]; then
    pid=$(cat ${ZEPPELIN_PID})
    if ! kill -0 ${pid} > /dev/null 2>&1; then
      action_msg "${ZEPPELIN_NAME} running but process is dead" "${SET_ERROR}"
      return 1
    else
      action_msg "${ZEPPELIN_NAME} is running" "${SET_OK}"
    fi
  else
    action_msg "${ZEPPELIN_NAME} is not running" "${SET_ERROR}"
    return 1
  fi
}

case "${1}" in
  start)
    start
    ;;
  stop)
    stop
    ;;
  upstart)
    upstart
    ;;
  reload)
    stop
    start
    ;;
  restart)
    echo "${ZEPPELIN_NAME} is restarting" >> "${ZEPPELIN_OUTFILE}"
    stop
    start
    ;;
  status)
    find_zeppelin_process
    ;;
  -v | --version)
    getZeppelinVersion
    ;;
  *)
    echo ${USAGE}
esac
