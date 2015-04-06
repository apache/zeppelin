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

wget http://apache.mesi.com.ar/spark/spark-1.1.1/spark-1.1.1-bin-hadoop2.3.tgz
tar zxvf spark-1.1.1-bin-hadoop2.3.tgz
cd spark-1.1.1-bin-hadoop2.3
export SPARK_MASTER_PORT=7071
export SPARK_MASTER_WEBUI_PORT=7072
./sbin/start-master.sh
./bin/spark-class org.apache.spark.deploy.worker.Worker spark://localhost:7071 &> worker.log &
./bin/spark-class org.apache.spark.deploy.worker.Worker spark://localhost:7071 &> worker2.log &
