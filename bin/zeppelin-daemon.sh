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
# description: Start and stop daemon script for.
#

BIN=$(dirname "${BASH_SOURCE-$0}")
BIN=$(cd "${BIN}">/dev/null; pwd)

. "${BIN}/common.sh"
. "${BIN}/functions.sh"

HOSTNAME=$(hostname)
ZEPPELIN_NAME="Zeppelin"
ZEPPELIN_LOGFILE="${ZEPPELIN_LOG_DIR}/zeppelin-${ZEPPELIN_IDENT_STRING}-${HOSTNAME}.log"
ZEPPELIN_OUTFILE="${ZEPPELIN_LOG_DIR}/zeppelin-${ZEPPELIN_IDENT_STRING}-${HOSTNAME}.out"
ZEPPELIN_PID="${ZEPPELIN_PID_DIR}/zeppelin-${ZEPPELIN_IDENT_STRING}-${HOSTNAME}.pid"
ZEPPELIN_MAIN=com.nflabs.zeppelin.server.ZeppelinServer
JAVA_OPTS+=" -Dzeppelin.log.file=${ZEPPELIN_LOGFILE}"

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

  if [[ ! -d "${ZEPPELIN_NOTEBOOK_DIR}" ]]; then
    echo "Notbook dir doesn't exist, create ${ZEPPELIN_NOTEBOOK_DIR}"
    $(mkdir -p "${ZEPPELIN_NOTEBOOK_DIR}")
  fi
}

function wait_for_process_to_die() {
  local pid
  local count
  pid=$1
  count=0
  while [[ "${count}" -lt 10 ]]; do
    $(kill ${pid} > /dev/null 2> /dev/null)
    if kill -0 ${pid} > /dev/null 2>&1; then
      sleep 3
      let "count+=1"
    else
      break
    fi
  if [[ "${count}" == "5" ]]; then
    $(kill -9 ${pid} > /dev/null 2> /dev/null)
  fi
  done
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
  local driver_pid app_pid

  driver_pid=$(sed -n '1p' ${ZEPPELIN_PID})
  app_pid=$(sed -n '2p' ${ZEPPELIN_PID})

  if ! kill -0 ${driver_pid} >/dev/null 2>&1; then
    action_msg "${ZEPPELIN_RUNNER} process died" "${SET_ERROR}"
    print_log_for_ci
  fi

  if ! kill -0 ${app_pid} >/dev/null 2>&1; then
    action_msg "${ZEPPELIN_NAME} process died" "${SET_ERROR}"
    print_log_for_ci
  fi
}

function start() {
  local driver_pid app_pid

  if [[ -f "${ZEPPELIN_PID}" ]]; then
    action_msg "${ZEPPELIN_NAME} is already running" "${SET_ERROR}"
    exit 1;
  fi
  
  initialize_default_directories

  # Spark is deprecating manual setting of the CLASSPATH. The recommended
  # route is to invoke spark-submit to deal with the expansion
  nohup nice -n "${ZEPPELIN_NICENESS}" "${ZEPPELIN_RUNNER}" --class "${ZEPPELIN_MAIN}" --driver-java-options "-Dzeppelin.log.file=${ZEPPELIN_LOGFILE}" spark-shell >> "${ZEPPELIN_OUTFILE}" 2>&1 < /dev/null &

  # Output two PIDs to the pid file. The first line
  # contains the driver pid and the second contains the
  # zeppelin app pid
  driver_pid=$!
  if [[ -z "${driver_pid}" ]]; then
    action_msg "${ZEPPELIN_RUNNER}" "${SET_ERROR}"
  else
    action_msg "${ZEPPELIN_RUNNER}" "${SET_OK}"
    echo ${driver_pid} > ${ZEPPELIN_PID}
  fi

  # Wait for Zeppelin app to be spawned by driver
  # Before trying to get the app PID
  wait_zeppelin_is_up_for_ci
  sleep 2

  # Avoiding pgrep for compatibility
  app_pid=$(ps --no-headers --ppid ${driver_pid} | awk '{print $1}')
  if [[ -z "${app_pid}" ]]; then
    action_msg "${ZEPPELIN_NAME}" "${SET_ERROR}"
  else
    action_msg "${ZEPPELIN_NAME}" "${SET_OK}"
    echo ${app_pid} >> ${ZEPPELIN_PID}
  fi

  check_if_process_is_alive
}

function stop() {
  local driver_pid app_pid

  if [[ ! -f "${ZEPPELIN_PID}" ]]; then
    action_msg "${ZEPPELIN_NAME} is not running" "${SET_ERROR}"
     exit 1;
  fi
  
  driver_pid=$(sed -n '1p' "${ZEPPELIN_PID}")
  app_pid=$(sed -n '2p' "${ZEPPELIN_PID}")
  
  if [[ -z "${app_pid}" ]] && [[ -z "${driver_pid}" ]]; then
    action_msg "${ZEPPELIN_NAME} is not running" "${SET_ERROR}"
  else
    wait_for_process_to_die "${app_pid}"
    wait_for_process_to_die "${driver_pid}"
    $(rm -f "${ZEPPELIN_PID}")
    action_msg "${ZEPPELIN_RUNNER}" "${SET_OK}"
    action_msg "${ZEPPELIN_NAME}" "${SET_OK}"
  fi
}

function find_zeppelin_process() {
  local pid

  if [[ -f "${ZEPPELIN_PID}" ]]; then
    pid=$(sed -n '1p' ${ZEPPELIN_PID})
    if ! kill -0 ${pid} > /dev/null 2>&1; then
      action_msg "${ZEPPELIN_NAME} running but process is dead" "${SET_ERROR}"
    else
      action_msg "${ZEPPELIN_NAME} is running" "${SET_OK}"
    fi
  else
    action_msg "${ZEPPELIN_NAME} is not running" "${SET_ERROR}"
  fi
}

case "${1}" in
  start)
    start
    ;;
  stop)
    stop
    ;;
  reload)
    stop
    start
    ;;
  restart)
    stop
    start
    ;;
  status)
    find_zeppelin_process
    ;;
  *)
    echo "Usage: $0 {start|stop|restart|reload|status}"
esac

exit 0
