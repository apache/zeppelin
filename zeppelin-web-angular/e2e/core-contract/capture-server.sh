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
port_given="no"

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
      port_given="yes"
      # Stop reuses this port from the marker, so reject invalid values now.
      if [[ ! "${zeppelin_port}" =~ ^[0-9]+$ ]]; then
        echo "--port must be a number, got '${zeppelin_port}'" >&2
        exit 2
      fi
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

reject_whitespace_path() {
  if [[ "$1" =~ [[:space:]] ]]; then
    echo "$2 path must not contain whitespace: the Zeppelin launcher splits JVM arguments" >&2
    exit 2
  fi
}

# Validate before creating directories or locking.
# Resolve an existing ancestor so symlinks cannot hide unsupported paths.
reject_whitespace_path "${capture_root}" "capture root"
existing_parent="${capture_root}"
while [[ ! -d "${existing_parent}" ]]; do
  existing_parent="$(dirname "${existing_parent}")"
done
# The suffix preserves trailing newlines in directory names during substitution.
physical_parent="$(cd -P "${existing_parent}" && printf '%s/.' "$PWD")"
reject_whitespace_path "${physical_parent}" "canonical capture root"
repo_root="$(cd -P "$(dirname "$0")/../../.." && printf '%s/.' "$PWD")"
reject_whitespace_path "${repo_root}" "repository"
repo_root="${repo_root%/.}"
# Use the physical path so symlink and direct access produce the same marker.
capture_root="$(mkdir -p "${capture_root}" && cd "${capture_root}" && pwd -P)"
capture_marker="-Dzeppelin.capture.root=${capture_root}"
marker_file="${capture_root}/.zeppelin-capture-root"
zeppelin_pid_file="${capture_root}/zeppelin.pid"
operation_lock="${capture_root}/.capture-operation-lock"

acquire_operation_lock() {
  # Lock atomically before inspecting ownership; hold through readiness and cleanup.
  if ! mkdir "${operation_lock}" 2>/dev/null; then
    echo "another capture server operation is in progress for ${capture_root}" >&2
    echo "if its owner crashed, verify no operation is running before removing ${operation_lock}" >&2
    exit 1
  fi
  trap 'rmdir "${operation_lock}"' EXIT
  trap 'exit 130' INT
  trap 'exit 143' TERM
}

port_in_use() {
  local port="$1"
  if command -v lsof >/dev/null 2>&1; then
    lsof -nP -iTCP:"${port}" -sTCP:LISTEN >/dev/null 2>&1
    return
  fi
  if command -v ss >/dev/null 2>&1; then
    ss -ltn "sport = :${port}" 2>/dev/null | grep -q LISTEN
    return
  fi
  (exec 3<>"/dev/tcp/127.0.0.1/${port}") >/dev/null 2>&1
}

write_marker() {
  {
    echo "root=${capture_root}"
    echo "repo=${repo_root}"
    echo "port=${zeppelin_port}"
  } > "${marker_file}"
}

verify_root_marker() {
  # Match paths literally, including regex metacharacters.
  [[ -f "${marker_file}" ]] && grep -qxF "root=${capture_root}" "${marker_file}"
}

verify_pid_identity() {
  local pid="$1"
  local expected="$2"
  [[ "${pid}" =~ ^[0-9]+$ ]] || return 1
  local command
  command="$(ps -p "${pid}" -o command= 2>/dev/null)" || return 1
  # Match a whole argument so /tmp/capture cannot claim /tmp/capture-other.
  [[ " ${command} " == *" ${expected} "* ]]
}

group_members() {
  local pid="$1"
  if command -v pgrep >/dev/null 2>&1; then
    pgrep -g "${pid}" 2>/dev/null
  else
    ps -A -o pid=,pgid= 2>/dev/null | awk -v group="${pid}" '$2 == group { print $1 }'
  fi
}

# Check the group so surviving children still trigger KILL escalation or failure.
process_group_alive() {
  local pid="$1"
  [[ "${pid}" =~ ^[0-9]+$ ]] || return 1
  [[ -n "$(group_members "${pid}")" ]] && return 0
  ps -p "${pid}" >/dev/null 2>&1
}

# If the leader has exited, require a surviving group member with our marker.
verify_group_identity() {
  local pid="$1"
  local expected="$2"
  local member
  for member in $(group_members "${pid}"); do
    if verify_pid_identity "${member}" "${expected}"; then
      return 0
    fi
  done
  verify_pid_identity "${pid}" "${expected}"
}

signal_process_group() {
  local pid="$1"
  local signal="$2"
  # Signal the group to reach servers launched through shell wrappers.
  kill "-${signal}" "-${pid}" 2>/dev/null || kill "-${signal}" "${pid}" 2>/dev/null || true
}

stop_pid() {
  local pid_file="$1"
  local expected="$2"
  [[ -f "${pid_file}" ]] || return 0
  local pid
  pid="$(cat "${pid_file}")"
  if process_group_alive "${pid}"; then
    if ! verify_group_identity "${pid}" "${expected}"; then
      echo "refusing to stop ${pid}: command does not match ${expected}" >&2
      echo "the recorded pid belongs to another process; start on this root again to clear it, or remove ${pid_file}" >&2
      exit 1
    fi
    signal_process_group "${pid}" TERM
    for _ in {1..20}; do
      process_group_alive "${pid}" || break
      sleep 1
    done
    if process_group_alive "${pid}"; then
      signal_process_group "${pid}" KILL
      for _ in {1..10}; do
        process_group_alive "${pid}" || break
        sleep 1
      done
    fi
    if process_group_alive "${pid}"; then
      echo "failed to stop ${pid}; keeping ${pid_file} so it can be retried" >&2
      exit 1
    fi
  fi
  rm -f "${pid_file}"
}

start_zeppelin() {
  # Environment and JVM properties override the temporary site XML.
  # Clear inherited settings that could redirect storage, classpaths or remote connections.
  # Keep JAVA_HOME and PATH to select the installed toolchain.
  local inherited_name
  for inherited_name in "${!ZEPPELIN_@}"; do
    unset "${inherited_name}"
  done
  unset JAVA_OPTS JAVA_TOOL_OPTIONS _JAVA_OPTIONS JDK_JAVA_OPTIONS CLASSPATH

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
  export ZEPPELIN_ADDR="127.0.0.1"
  export ZEPPELIN_NOTEBOOK_STORAGE="org.apache.zeppelin.notebook.repo.VFSNotebookRepo"
  export ZEPPELIN_NOTEBOOK_DIR="${capture_root}/notebook"
  export ZEPPELIN_LOG_DIR="${capture_root}/logs"
  export ZEPPELIN_PID_DIR="${capture_root}/run"
  export ZEPPELIN_WAR_TEMPDIR="${capture_root}/webapps"
  # Zeppelin ignores this marker; verify_pid_identity matches it as a whole JVM argument.
  export ZEPPELIN_JAVA_OPTS="-Dzeppelin.server.port=${zeppelin_port} -Dzeppelin.notebook.dir=${capture_root}/notebook -Dzeppelin.search.index.path=${capture_root}/index -Dzeppelin.recovery.dir=${capture_root}/recovery ${capture_marker}"
  export ZEPPELIN_CAPTURE_ROOT="${capture_root}"
  export ZEPPELIN_PORT="${zeppelin_port}"
  # Do not inherit Hadoop settings for this fixture server.
  export USE_HADOOP=false

  # Give the server its own process group so stop can signal its children too.
  set -m
  # Tests substitute a stub command and append the same ownership marker as a real server.
  # The stub ignores the extra argument.
  if [[ -n "${CAPTURE_ZEPPELIN_COMMAND:-}" ]]; then
    # Preserve the marker through bash -c parsing, including quotes and dollar signs.
    bash -c "${CAPTURE_ZEPPELIN_COMMAND} $(printf '%q' "${capture_marker}")" </dev/null >"${capture_root}/logs/zeppelin-stdout.log" 2>"${capture_root}/logs/zeppelin-stderr.log" &
    echo "$!" > "${zeppelin_pid_file}"
  else
    "${repo_root}/bin/zeppelin.sh" </dev/null >"${capture_root}/logs/zeppelin-stdout.log" 2>"${capture_root}/logs/zeppelin-stderr.log" &
    echo "$!" > "${zeppelin_pid_file}"
  fi
  set +m
}

wait_for_http() {
  local url="$1"
  local pid="${2:-}"
  for _ in {1..120}; do
    # Bound each request so a listener that never responds cannot hang startup.
    if curl -fsS --max-time 5 "${url}" >/dev/null 2>&1; then
      return 0
    fi
    # Report an exited server immediately instead of masking its error with a timeout.
    if [[ -n "${pid}" ]] && ! process_group_alive "${pid}"; then
      return 2
    fi
    sleep 1
  done
  return 1
}

# Verify listener ownership after binding; another server may take a previously free port.
verify_port_owner() {
  local port="$1"
  local expected="$2"
  local owner
  command -v lsof >/dev/null 2>&1 || return 1
  for owner in $(lsof -tiTCP:"${port}" -sTCP:LISTEN 2>/dev/null); do
    if verify_group_identity "${owner}" "${expected}"; then
      return 0
    fi
  done
  return 1
}

start_server() {
  if ! command -v lsof >/dev/null 2>&1; then
    echo "lsof is required to verify capture server listener ownership" >&2
    exit 1
  fi
  if [[ "${capture_mode}" != "anonymous" && "${capture_mode}" != "auth" ]]; then
    echo "mode must be anonymous or auth" >&2
    exit 2
  fi
  if [[ -f "${zeppelin_pid_file}" ]]; then
    local recorded_pid
    recorded_pid="$(cat "${zeppelin_pid_file}")"
    if [[ "${recorded_pid}" == "starting" ]]; then
      echo "capture startup is incomplete; inspect ${capture_root} before removing ${zeppelin_pid_file}" >&2
      exit 1
    fi
    # Preserve a live capture's PID file so a failed stop can be retried.
    # Clear recycled PIDs belonging to unrelated processes so the root remains reusable.
    if process_group_alive "${recorded_pid}" && verify_group_identity "${recorded_pid}" "${capture_marker}"; then
      echo "capture server ${recorded_pid} is still running for ${capture_root}; stop it first" >&2
      exit 1
    fi
    rm -f "${zeppelin_pid_file}"
  fi
  if port_in_use "${zeppelin_port}"; then
    echo "port ${zeppelin_port} is already in use" >&2
    exit 1
  fi

  # Keep a visible claim until start_zeppelin records the process group leader.
  if ! (set -o noclobber; echo "starting" > "${zeppelin_pid_file}") 2>/dev/null; then
    echo "another capture server start is already in progress for ${capture_root}" >&2
    exit 1
  fi

  write_marker
  start_zeppelin
  if [[ "${capture_mode}" == "auth" ]]; then
    # Direct the login helper to this capture's credentials instead of the repository config.
    echo "shiro config: ${capture_root}/conf/shiro.ini"
    echo "export ZEPPELIN_E2E_SHIRO_INI=${capture_root}/conf/shiro.ini to log the e2e helper into this server"
  fi
  local recorded_pid
  recorded_pid="$(cat "${zeppelin_pid_file}" 2>/dev/null)"
  # Capture failure status without triggering set -e before cleanup.
  local ready=0
  wait_for_http "http://127.0.0.1:${zeppelin_port}/api/version" "${recorded_pid}" || ready=$?
  if [[ ${ready} -ne 0 ]]; then
    # The claim must not outlive a start that never came up.
    if [[ ${ready} -eq 2 ]]; then
      echo "zeppelin exited before it answered on port ${zeppelin_port}" >&2
    else
      echo "zeppelin did not become ready on port ${zeppelin_port}" >&2
    fi
    echo "--- ${capture_root}/logs/zeppelin-stderr.log (tail) ---" >&2
    tail -n 20 "${capture_root}/logs/zeppelin-stderr.log" >&2 2>/dev/null || true
    stop_server
    exit 1
  fi
  if ! verify_port_owner "${zeppelin_port}" "${capture_marker}"; then
    echo "port ${zeppelin_port} is answered by a server this root did not start" >&2
    stop_server
    exit 1
  fi
}

stop_server() {
  if ! verify_root_marker; then
    echo "refusing to stop without matching capture root marker: ${marker_file}" >&2
    exit 1
  fi
  # Use the recorded port so an unrelated listener on the default port cannot fail stop.
  if [[ "${port_given}" == "no" ]]; then
    local recorded_port
    recorded_port="$(sed -n 's/^port=//p' "${marker_file}")"
    if [[ "${recorded_port}" =~ ^[0-9]+$ ]]; then
      zeppelin_port="${recorded_port}"
    fi
  fi
  stop_pid "${zeppelin_pid_file}" "${capture_marker}"
  # Allow wrapped servers time to release the port after their wrapper exits.
  for _ in {1..10}; do
    port_in_use "${zeppelin_port}" || return 0
    sleep 1
  done
  echo "port ${zeppelin_port} is still in use after stop; another process may hold it" >&2
  exit 1
}

case "${command}" in
  start)
    acquire_operation_lock
    start_server
    ;;
  stop)
    acquire_operation_lock
    stop_server
    ;;
  *)
    usage
    exit 2
    ;;
esac
