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


if [ $# -ne 2 ]; then
    echo "usage) $0 [spark version] [hadoop version]"
    echo "   eg) $0 1.3.1 2.6"
    exit 1
fi

SPARK_VERSION="${1}"
HADOOP_VERSION="${2}"

echo ${SPARK_VERSION} | grep "^1.[123].[0-9]" > /dev/null
if [ $? -eq 0 ]; then
  echo "${SPARK_VERSION}" | grep "^1.[12].[0-9]" > /dev/null
  if [ $? -eq 0 ]; then
    SPARK_VER_RANGE="<=1.2"
  else
    SPARK_VER_RANGE="<=1.3"
  fi
else
  SPARK_VER_RANGE=">1.3"
fi

set -xe

FWDIR=$(dirname "${BASH_SOURCE-$0}")
ZEPPELIN_HOME="$(cd "${FWDIR}/.."; pwd)"
export SPARK_HOME=${ZEPPELIN_HOME}/spark-${SPARK_VERSION}-bin-hadoop${HADOOP_VERSION}
echo "SPARK_HOME is ${SPARK_HOME}"
if [ ! -d "${SPARK_HOME}" ]; then
    if [ "${SPARK_VER_RANGE}" == "<=1.2" ]; then
        # spark 1.1.x and spark 1.2.x can be downloaded from archive
        STARTTIME=`date +%s`
        timeout -s KILL 300 wget -q http://archive.apache.org/dist/spark/spark-${SPARK_VERSION}/spark-${SPARK_VERSION}-bin-hadoop${HADOOP_VERSION}.tgz
        ENDTIME=`date +%s`
        DOWNLOADTIME=$((ENDTIME-STARTTIME))
    else
        # spark 1.3.x and later can be downloaded from mirror
        # get download address from mirror
        MIRROR_INFO=$(curl -s "http://www.apache.org/dyn/closer.cgi/spark/spark-${SPARK_VERSION}/spark-${SPARK_VERSION}-bin-hadoop${HADOOP_VERSION}.tgz?asjson=1")

        PREFFERED=$(echo "${MIRROR_INFO}" | grep preferred | sed 's/[^"]*.preferred.: .\([^"]*\).*/\1/g')
        PATHINFO=$(echo "${MIRROR_INFO}" | grep path_info | sed 's/[^"]*.path_info.: .\([^"]*\).*/\1/g')

        STARTTIME=`date +%s`
        timeout -s KILL 590 wget -q "${PREFFERED}${PATHINFO}"
        ENDTIME=`date +%s`
        DOWNLOADTIME=$((ENDTIME-STARTTIME))
    fi
    tar zxf spark-${SPARK_VERSION}-bin-hadoop${HADOOP_VERSION}.tgz
fi

set +xe
