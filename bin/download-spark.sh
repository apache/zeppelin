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

bin=$(dirname "${BASH_SOURCE-$0}")
bin=$(cd "${bin}">/dev/null; pwd)

. "${bin}/common.sh"

ZEPPELIN_ENV="conf/zeppelin-env.sh"
ZEPPELIN_ENV_TEMP="${ZEPPELIN_ENV}.template"
ZEPPELIN_VERSION="$(getZeppelinVersion)"

ANSWER_FILE="README.txt"

# Download Spark binary package from the given URL.
# Ties 3 times with 1s delay
# Arguments: url - source URL
function download_with_retry() {
  local url="$1"
  curl -O --retry 3 --retry-delay 1 "${url}"

  if [[ "$?" -ne 0 ]]; then
    echo -e "\nStop downloading with unexpected error."
  fi
}

function unzip_spark_bin() {
  if ! tar zxf "${SPARK_ARCHIVE}.tgz" ; then
    echo "Unable to extract ${SPARK_ARCHIVE}.tgz" >&2
    rm -rf "${SPARK_ARCHIVE}"
  fi

  rm -f "${SPARK_ARCHIVE}.tgz"
  echo -e "\n${SPARK_ARCHIVE} is successfully downloaded and saved under ${ZEPPELIN_HOME}/${SPARK_CACHE}\n"
}

function create_local_spark_dir() {
  if [[ ! -d "${SPARK_CACHE}" ]]; then
    mkdir -p "${SPARK_CACHE}"
  fi
}

function check_local_spark_dir() {
  if [[ -d "${ZEPPELIN_HOME}/${SPARK_CACHE}" ]]; then
    rm -r "${ZEPPELIN_HOME}/${SPARK_CACHE}"
  fi
}

function save_local_spark() {
  # echo -e "For using Spark interpreter in local mode(without external Spark installation), Spark binary needs to be downloaded."
  trap "echo -e '\n\nForced termination by user.'; check_local_spark_dir; exit 1" SIGTERM SIGINT SIGQUIT
  create_local_spark_dir
  cd "${SPARK_CACHE}"

  printf "Download ${SPARK_ARCHIVE}.tgz from mirror ...\n\n"

  MIRROR_INFO=$(curl -s "http://www.apache.org/dyn/closer.cgi/spark/spark-${SPARK_VERSION}/${SPARK_ARCHIVE}.tgz?asjson=1")
  PREFFERED=$(echo "${MIRROR_INFO}" | grep preferred | sed 's/[^"]*.preferred.: .\([^"]*\).*/\1/g')
  PATHINFO=$(echo "${MIRROR_INFO}" | grep path_info | sed 's/[^"]*.path_info.: .\([^"]*\).*/\1/g')

  download_with_retry "${PREFFERED}${PATHINFO}"
  unzip_spark_bin
}

save_local_spark

set +xe
