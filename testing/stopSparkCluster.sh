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

FWDIR=$(dirname "${BASH_SOURCE-$0}")
ZEPPELIN_HOME="$(cd "${FWDIR}/.."; pwd)"
export SPARK_HOME=${ZEPPELIN_HOME}/spark-${SPARK_VERSION}-bin-hadoop${HADOOP_VERSION}

# set create PID dir
export SPARK_PID_DIR=${SPARK_HOME}/run


${SPARK_HOME}/sbin/spark-daemon.sh stop org.apache.spark.deploy.worker.Worker 1
${SPARK_HOME}/sbin/stop-master.sh
