#!/bin/bash
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

set -euo pipefail

: "${HADOOP_PREFIX:=/usr/local/hadoop}"

. "$HADOOP_PREFIX/etc/hadoop/hadoop-env.sh"

rm -f /tmp/*.pid

# installing libraries if any - (resource urls added comma separated to the ACP system variable)
cd "$HADOOP_PREFIX/share/hadoop/common"
ACP_URLS="${ACP:-}"
for cp in ${ACP_URLS//,/ }; do
  echo "== $cp"
  curl -fLO -- "$cp"
done
cd - > /dev/null

cp "$SPARK_HOME/conf/metrics.properties.template" "$SPARK_HOME/conf/metrics.properties" || true

# start hadoop
service ssh start
"$HADOOP_PREFIX/sbin/start-dfs.sh"
"$HADOOP_PREFIX/sbin/start-yarn.sh"

"$HADOOP_PREFIX/bin/hdfs" dfsadmin -safemode leave \
  && "$HADOOP_PREFIX/bin/hdfs" dfs -mkdir -p /spark
if ! "$HADOOP_PREFIX/bin/hdfs" dfs -test -e /spark/.jars-upload-complete; then
  "$HADOOP_PREFIX/bin/hdfs" dfs -rm -r -f /spark/jars
  "$HADOOP_PREFIX/bin/hdfs" dfs -put "$SPARK_HOME/jars" /spark
  "$HADOOP_PREFIX/bin/hdfs" dfs -touchz /spark/.jars-upload-complete
fi

# start spark
export SPARK_MASTER_OPTS="-Dspark.driver.port=7001 -Dspark.fileserver.port=7002
  -Dspark.broadcast.port=7003 -Dspark.replClassServer.port=7004
  -Dspark.blockManager.port=7005 -Dspark.executor.port=7006
  -Dspark.ui.port=4040 -Dspark.broadcast.factory=org.apache.spark.broadcast.HttpBroadcastFactory"
export SPARK_WORKER_OPTS="-Dspark.driver.port=7001 -Dspark.fileserver.port=7002
  -Dspark.broadcast.port=7003 -Dspark.replClassServer.port=7004
  -Dspark.blockManager.port=7005 -Dspark.executor.port=7006
  -Dspark.ui.port=4040 -Dspark.broadcast.factory=org.apache.spark.broadcast.HttpBroadcastFactory"

export SPARK_MASTER_PORT=7077

cd "$SPARK_HOME/sbin"
./start-master.sh
./start-worker.sh "spark://$(hostname):$SPARK_MASTER_PORT"

CMD=${1:-"exit 0"}
if [[ "$CMD" == "-d" ]];
then
	service ssh stop
	/usr/sbin/sshd -D -d
else
	/bin/bash -c "$*"
fi
