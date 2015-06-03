#!/bin/sh
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


if [ $# -ne 1 ]; then
    echo "usage) $0 [spark version]"
    echo "   eg) $0 1.3.1"
    exit 1
fi

SPARK_VERSION="${1}"

if [ ! -d "spark-${SPARK_VERSION}-bin-hadoop2.3" ]; then
    wget -q http://www.us.apache.org/dist/spark/spark-${SPARK_VERSION}/spark-${SPARK_VERSION}-bin-hadoop2.3.tgz
    tar zxf spark-${SPARK_VERSION}-bin-hadoop2.3.tgz
fi

# start
export SPARK_MASTER_PORT=7071
export SPARK_MASTER_WEBUI_PORT=7072
./spark-${SPARK_VERSION}-bin-hadoop2.3/sbin/start-master.sh
./spark-${SPARK_VERSION}-bin-hadoop2.3/sbin/start-slave.sh 1 `hostname`:${SPARK_MASTER_PORT}
