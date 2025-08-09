#!/bin/bash
#
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

set -Eeuo pipefail
IFS=$'\n\t'

usage() {
  echo "Usage: $0 <ZEPPELIN_SRC_ROOT_DIR> [additional args...]" >&2
  exit 2
}

[[ $# -ge 1 ]] || usage

ZEPPELIN_SRC_ROOT_DIR=$1
shift || true

TRAVIS_SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"

if [[ -d "$ZEPPELIN_SRC_ROOT_DIR" ]]; then
  ZEPPELIN_SRC_ROOT_DIR="$(cd -- "$ZEPPELIN_SRC_ROOT_DIR" && pwd -P)"
else
  echo "ERROR: ZEPPELIN_SRC_ROOT_DIR not found: $ZEPPELIN_SRC_ROOT_DIR" >&2
  exit 2
fi

PYTHON_BIN="${PYTHON:-}"
if [[ -z "$PYTHON_BIN" ]]; then
  if command -v python3 >/dev/null 2>&1; then
    PYTHON_BIN="python3"
  elif command -v python >/dev/null 2>&1; then
    PYTHON_BIN="python"
  else
    echo "ERROR: python3/python not found in PATH" >&2
    exit 2
  fi
fi

SAVE_LOGS_PY="${TRAVIS_SCRIPT_DIR}/save-logs.py"
LOG_FILE="install.txt"

if [[ ! -f "$SAVE_LOGS_PY" ]]; then
  echo "ERROR: save-logs.py not found: $SAVE_LOGS_PY" >&2
  exit 2
fi

cd -- "$ZEPPELIN_SRC_ROOT_DIR"

set +e
"$PYTHON_BIN" "$SAVE_LOGS_PY" "$LOG_FILE" "$@"
BUILD_RET_VAL=$?
set -e

if [[ "$BUILD_RET_VAL" != "0" ]]; then
  if [[ -f "$LOG_FILE" ]]; then
    cat -- "$LOG_FILE"
  else
    echo "WARN: Log file not found: $LOG_FILE" >&2
  fi
fi

exit "$BUILD_RET_VAL"
