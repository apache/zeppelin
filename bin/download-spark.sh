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

SPARK_VERSION="2.0.0"
HADOOP_VERSION="2.7"

FWDIR="$(dirname "${BASH_SOURCE-$0}")"
ZEPPELIN_HOME="$(cd "${FWDIR}/.."; pwd)"
ZEPPELIN_ENV="${ZEPPELIN_HOME}/conf/zeppelin-env.sh"
ZEPPELIN_ENV_TEMP="${ZEPPELIN_HOME}/conf/zeppelin-env.sh.template"

# Downloads file from the given URL.
# Ties 3 times with 1s delay, 20s read and 15s connection timeouts.
# Arguments: url - source URL
function download_with_retry() {
  local url="$1"
  wget --retry-connrefused --waitretry=1 --read-timeout=20 --timeout=15 -t 3 "${url}"

  if [[ "$?" -ne 0 ]]; then
      echo "3 download attempts for ${url} failed"
  fi
}

SPARK_CACHE=".spark-dist"
SPARK_ARCHIVE="spark-${SPARK_VERSION}-bin-hadoop${HADOOP_VERSION}"

mkdir -p "${SPARK_CACHE}"
cd "${SPARK_CACHE}"
if [[ ! -f "${SPARK_ARCHIVE}.tgz" ]]; then
  echo "There is no SPARK_HOME in your system."
  echo "Download ${SPARK_ARCHIVE} from mirror before starting Zeppelin server..."
  MIRROR_INFO=$(curl -s "http://www.apache.org/dyn/closer.cgi/spark/spark-${SPARK_VERSION}/${SPARK_ARCHIVE}.tgz?asjson=1")

  PREFFERED=$(echo "${MIRROR_INFO}" | grep preferred | sed 's/[^"]*.preferred.: .\([^"]*\).*/\1/g')
  PATHINFO=$(echo "${MIRROR_INFO}" | grep path_info | sed 's/[^"]*.path_info.: .\([^"]*\).*/\1/g')

  download_with_retry "${PREFFERED}${PATHINFO}"
fi

if ! tar zxf "${SPARK_ARCHIVE}.tgz" ; then
  echo "Unable to extract ${SPARK_ARCHIVE}.tgz" >&2
  rm -rf "${SPARK_ARCHIVE}"
else
  if [[ ! -f "${ZEPPELIN_ENV}" ]]; then
    echo "${ZEPPELIN_ENV} doesn't exist."
    echo "Creating ${ZEPPELIN_ENV} from ${ZEPPELIN_ENV_TEMP}..."
    cp "${ZEPPELIN_ENV_TEMP}" "${ZEPPELIN_ENV}"
  fi
  export SPARK_HOME="${ZEPPELIN_HOME}/.spark-dist/${SPARK_ARCHIVE}"

  echo "SPARK_HOME is ${SPARK_HOME}"

  # get SPARK_HOME line number in conf/zeppelin-env.sh and substitute to real SPARK_HOME
  SPARK_HOME_LINE_NUM=$(grep -n "export SPARK_HOME" "${ZEPPELIN_HOME}/conf/zeppelin-env.sh" | cut -d: -f 1)
  # save to zeppelin-env.sh.bak temporarily, then remove .bak file
  sed -i .bak "${SPARK_HOME_LINE_NUM}s|.*|export SPARK_HOME=\"${SPARK_HOME}\"|g" "${ZEPPELIN_HOME}/conf/zeppelin-env.sh"
  rm "${ZEPPELIN_HOME}/conf/zeppelin-env.sh.bak"
fi

rm -f "${SPARK_HOME}.tgz"

set +xe
