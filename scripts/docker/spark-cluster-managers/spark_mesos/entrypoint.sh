#!/bin/bash
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements. See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License. You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

export SPARK_HOME=/usr/local/spark/
export SPARK_MASTER_PORT=7077
export SPARK_MASTER_WEBUI_PORT=8080
export SPARK_WORKER_PORT=8888
export SPARK_WORKER_WEBUI_PORT=8081
export LD_LIBRARY_PATH=$LD_LIBRARY_PATH:$JAVA_HOME/jre/lib/amd64/server/

# spark configuration
cp $SPARK_HOME/conf/spark-env.sh.template $SPARK_HOME/conf/spark-env.sh
echo "export MESOS_NATIVE_JAVA_LIBRARY=/usr/lib/libmesos.so" >> $SPARK_HOME/conf/spark-env.sh

cp $SPARK_HOME/conf/spark-defaults.conf.template $SPARK_HOME/conf/spark-defaults.conf
echo "spark.master mesos://`hostname`:5050" >> $SPARK_HOME/conf/spark-defaults.conf
echo "spark.mesos.executor.home /usr/local/spark" >> $SPARK_HOME/conf/spark-defaults.conf

# run spark
cd $SPARK_HOME/sbin
./start-master.sh
./start-slave.sh spark://`hostname`:$SPARK_MASTER_PORT

# start mesos
mesos-master --ip=0.0.0.0 --work_dir=/var/lib/mesos &> /var/log/mesos_master.log &
mesos-slave --master=0.0.0.0:5050 --work_dir=/var/lib/mesos --launcher=posix &> /var/log/mesos_slave.log &

CMD=${1:-"exit 0"}
if [[ "$CMD" == "-d" ]];
then
	service sshd stop
	/usr/sbin/sshd -D -d
else
	/bin/bash -c "$*"
fi
