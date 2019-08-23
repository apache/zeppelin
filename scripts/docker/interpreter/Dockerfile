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

FROM apache/zeppelin:0.8.0
MAINTAINER Apache Software Foundation <dev@zeppelin.apache.org>

ENV SPARK_VERSION=2.3.3
ENV HADOOP_VERSION=2.7

# support Kerberos certification
RUN export DEBIAN_FRONTEND=noninteractive && apt-get update && apt-get install -yq krb5-user libpam-krb5 && apt-get clean

RUN apt-get update && apt-get install -y curl unzip wget grep sed vim tzdata && apt-get clean

# auto upload zeppelin interpreter lib
RUN rm -rf /zeppelin

RUN rm -rf /spark
RUN wget https://www-us.apache.org/dist/spark/spark-${SPARK_VERSION}/spark-${SPARK_VERSION}-bin-hadoop${HADOOP_VERSION}.tgz
RUN tar zxvf spark-${SPARK_VERSION}-bin-hadoop${HADOOP_VERSION}.tgz
RUN mv spark-${SPARK_VERSION}-bin-hadoop${HADOOP_VERSION} spark
RUN rm spark-${SPARK_VERSION}-bin-hadoop${HADOOP_VERSION}.tgz
