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

FWDIR="$(dirname "${BASH_SOURCE-$0}")"
ZEPPELIN_HOME="$(cd "${FWDIR}/.."; pwd)"
ZEPPELIN_ENV="conf/zeppelin-env.sh"
ZEPPELIN_ENV_TEMP="conf/zeppelin-env.sh.template"
ZEPPELIN_VERSION="$(getZeppelinVersion)"

SPARK_VERSION="2.0.0"
HADOOP_VERSION="2.7"

SPARK_CACHE="interpreter/spark"
SPARK_ARCHIVE="spark-${SPARK_VERSION}-bin-hadoop${HADOOP_VERSION}"
ANSWER_FILE="README.txt"

# Download Spark binary package from the given URL.
# Ties 3 times with 1s delay
# Arguments: url - source URL
function download_with_retry() {
  local url="$1"
  curl -O --retry 3 --retry-delay 1 "${url}"
  if [[ "$?" -ne 0 ]]; then
      echo "3 download attempts for ${url} failed"
  fi
}

function unzip_spark_bin() {
  if ! tar zxf "${SPARK_ARCHIVE}.tgz" ; then
    echo "Unable to extract ${SPARK_ARCHIVE}.tgz" >&2
    rm -rf "${SPARK_ARCHIVE}"
  else
    set_spark_home
  fi

  rm -f "${SPARK_ARCHIVE}.tgz"
}

function check_zeppelin_env() {
  if [[ ! -f "${ZEPPELIN_ENV}" ]]; then
    echo "${ZEPPELIN_ENV} doesn't exist."
    echo "Creating ${ZEPPELIN_ENV} from ${ZEPPELIN_ENV_TEMP}..."
    cp "${ZEPPELIN_HOME}/${ZEPPELIN_ENV_TEMP}" "${ZEPPELIN_HOME}/${ZEPPELIN_ENV}"
  fi
}

function set_spark_home() {
  local line_num
  check_zeppelin_env
  export SPARK_HOME="${ZEPPELIN_HOME}/${SPARK_CACHE}/${SPARK_ARCHIVE}"
  echo -e "SPARK_HOME is ${SPARK_HOME}\n"

  # get SPARK_HOME line number in conf/zeppelin-env.sh and substitute to real SPARK_HOME
  line_num=$(grep -n "export SPARK_HOME" "${ZEPPELIN_HOME}/conf/zeppelin-env.sh" | cut -d: -f 1)
  # save to zeppelin-env.sh.bak temporarily, then remove .bak file
  sed -i .bak "${line_num}s|.*|export SPARK_HOME=\"${SPARK_HOME}\"|g" "${ZEPPELIN_HOME}/conf/zeppelin-env.sh"
  rm "${ZEPPELIN_HOME}/conf/zeppelin-env.sh.bak"
}

function save_local_spark() {
  cd "${SPARK_CACHE}"
  echo "There is no local Spark binary in ${ZEPPELIN_HOME}/${SPARK_CACHE}"

  while true; do
    read -p "Do you want to download a latest version of Spark binary? (Y/N): " answer
    case $answer in
      [Yy]* )
        printf "\nZeppelin server will be started after successful downloading ${SPARK_ARCHIVE}\n"
        printf "Download ${SPARK_ARCHIVE}.tgz from mirror before starting Zeppelin server...\n\n"
        MIRROR_INFO=$(curl -s "http://www.apache.org/dyn/closer.cgi/spark/spark-${SPARK_VERSION}/${SPARK_ARCHIVE}.tgz?asjson=1")

        PREFFERED=$(echo "${MIRROR_INFO}" | grep preferred | sed 's/[^"]*.preferred.: .\([^"]*\).*/\1/g')
        PATHINFO=$(echo "${MIRROR_INFO}" | grep path_info | sed 's/[^"]*.path_info.: .\([^"]*\).*/\1/g')

        download_with_retry "${PREFFERED}${PATHINFO}"
        unzip_spark_bin
        break
        ;;
      [Nn]* )
        echo -e "Your answer is saved under ${SPARK_CACHE}/${SPARK_ARCHIVE}/${ANSWER_FILE} \n"
        mkdir -p "${SPARK_ARCHIVE}"
        cd "${SPARK_ARCHIVE}"
        echo -e "Please note that you answered 'No' when we asked whether you want to download local Spark binary under ZEPPELIN_HOME/${SPARK_CACHE}/ or not.
        \nIf you want to use Spark interpreter in Apache Zeppelin, you need to set your own SPARK_HOME.
        \nSee http://zeppelin.apache.org/docs/${ZEPPELIN_VERSION}/interpreter/spark.html#configuration for the further details about Spark configuration in Zeppelin.
        " > "${ANSWER_FILE}"
        break
        ;;
      * )
        echo "Invalid response. Please re-enter (Y/N):"
        ;;
    esac
  done
}

save_local_spark

set +xe
