#!/usr/bin/env bash
#
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

set -euo pipefail

usage() {
  echo "usage: $0 start|stop --root <dir> [--mode anonymous|auth] [--port <port>]" >&2
}

command="${1:-}"
shift || true
capture_root=""
capture_mode="anonymous"
zeppelin_port="8080"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --root)
      capture_root="${2:-}"
      shift 2
      ;;
    --mode)
      capture_mode="${2:-}"
      shift 2
      ;;
    --port)
      zeppelin_port="${2:-}"
      shift 2
      ;;
    *)
      usage
      exit 2
      ;;
  esac
done

if [[ -z "${command}" || -z "${capture_root}" ]]; then
  usage
  exit 2
fi

repo_root="$(cd "$(dirname "$0")/../../.." && pwd)"
capture_root="$(mkdir -p "${capture_root}" && cd "${capture_root}" && pwd)"
marker_file="${capture_root}/.zeppelin-capture-root"
zeppelin_pid_file="${capture_root}/zeppelin.pid"

port_in_use() {
  lsof -nP -iTCP:"$1" -sTCP:LISTEN >/dev/null 2>&1
}

write_marker() {
  {
    echo "root=${capture_root}"
    echo "repo=${repo_root}"
  } > "${marker_file}"
}

verify_root_marker() {
  [[ -f "${marker_file}" ]] && grep -qx "root=${capture_root}" "${marker_file}"
}

verify_pid_identity() {
  local pid="$1"
  local expected="$2"
  [[ "${pid}" =~ ^[0-9]+$ ]] || return 1
  ps -p "${pid}" -o command= | grep -F -- "${expected}" >/dev/null 2>&1
}

stop_pid() {
  local pid_file="$1"
  local expected="$2"
  [[ -f "${pid_file}" ]] || return 0
  local pid
  pid="$(cat "${pid_file}")"
  if ps -p "${pid}" >/dev/null 2>&1; then
    if ! verify_pid_identity "${pid}" "${expected}"; then
      echo "refusing to stop ${pid}: command does not match ${expected}" >&2
      exit 1
    fi
    kill "${pid}"
    for _ in {1..20}; do
      ps -p "${pid}" >/dev/null 2>&1 || break
      sleep 1
    done
  fi
  rm -f "${pid_file}"
}

start_zeppelin() {
  mkdir -p "${capture_root}/conf" "${capture_root}/notebook" "${capture_root}/index" \
    "${capture_root}/logs" "${capture_root}/run" "${capture_root}/recovery" "${capture_root}/webapps"
  cp "${repo_root}/conf/log4j2.properties" "${capture_root}/conf/log4j2.properties"
  cp "${repo_root}/conf/zeppelin-site.xml.template" "${capture_root}/conf/zeppelin-site.xml"
  if [[ "${capture_mode}" == "auth" ]]; then
    cp "${repo_root}/conf/shiro.ini.template" "${capture_root}/conf/shiro.ini"
  else
    rm -f "${capture_root}/conf/shiro.ini"
  fi

  export ZEPPELIN_CONF_DIR="${capture_root}/conf"
  export ZEPPELIN_NOTEBOOK_DIR="${capture_root}/notebook"
  export ZEPPELIN_LOG_DIR="${capture_root}/logs"
  export ZEPPELIN_PID_DIR="${capture_root}/run"
  export ZEPPELIN_WAR_TEMPDIR="${capture_root}/webapps"
  export ZEPPELIN_JAVA_OPTS="${ZEPPELIN_JAVA_OPTS:-} -Dzeppelin.server.port=${zeppelin_port} -Dzeppelin.notebook.dir=${capture_root}/notebook -Dzeppelin.search.index.path=${capture_root}/index -Dzeppelin.recovery.dir=${capture_root}/recovery -Dzeppelin.capture.root=${capture_root}"
  export ZEPPELIN_CAPTURE_ROOT="${capture_root}"
  export ZEPPELIN_PORT="${zeppelin_port}"
  # The fixture server has no Hadoop configuration; do not inherit a developer shell setting.
  export USE_HADOOP=false

  if [[ -n "${CAPTURE_ZEPPELIN_COMMAND:-}" ]]; then
    bash -c "${CAPTURE_ZEPPELIN_COMMAND}" >"${capture_root}/logs/zeppelin-stdout.log" 2>"${capture_root}/logs/zeppelin-stderr.log" &
    echo "$!" > "${zeppelin_pid_file}"
  else
    "${repo_root}/bin/zeppelin.sh" >"${capture_root}/logs/zeppelin-stdout.log" 2>"${capture_root}/logs/zeppelin-stderr.log" &
    echo "$!" > "${zeppelin_pid_file}"
  fi
}

wait_for_http() {
  local url="$1"
  for _ in {1..120}; do
    if curl -fsS "${url}" >/dev/null 2>&1; then
      return 0
    fi
    sleep 1
  done
  return 1
}

start_server() {
  if [[ "${capture_mode}" != "anonymous" && "${capture_mode}" != "auth" ]]; then
    echo "mode must be anonymous or auth" >&2
    exit 2
  fi
  if port_in_use "${zeppelin_port}"; then
    echo "port ${zeppelin_port} is already in use" >&2
    exit 1
  fi

  write_marker
  start_zeppelin
  if ! wait_for_http "http://127.0.0.1:${zeppelin_port}/api/version"; then
    echo "zeppelin did not become ready on port ${zeppelin_port}" >&2
    stop_server
    exit 1
  fi
}

stop_server() {
  if ! verify_root_marker; then
    echo "refusing to stop without matching capture root marker: ${marker_file}" >&2
    exit 1
  fi
  stop_pid "${zeppelin_pid_file}" "${capture_root}"
}

case "${command}" in
  start)
    start_server
    ;;
  stop)
    stop_server
    ;;
  *)
    usage
    exit 2
    ;;
esac
