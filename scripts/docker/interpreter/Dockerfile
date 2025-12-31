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

FROM apache/zeppelin:0.11.2-SNAPSHOT
LABEL maintainer="Apache Zeppelin Community <dev@zeppelin.apache.org>"

ARG SPARK_VERSION=3.5.1
ARG SPARK_BIN_NAME=hadoop3

USER root

# support Kerberos certification
RUN export DEBIAN_FRONTEND=noninteractive && apt-get update && apt-get install -yq krb5-user libpam-krb5 && apt-get clean

RUN apt-get update && apt-get install -y curl unzip wget grep sed vim tzdata && apt-get clean

# auto upload zeppelin interpreter lib
RUN rm -rf /opt/zeppelin

RUN rm -rf /spark
RUN wget -O spark-${SPARK_VERSION}-bin-${SPARK_BIN_NAME}.tgz "https://www.apache.org/dyn/closer.lua/spark/spark-${SPARK_VERSION}/spark-${SPARK_VERSION}-bin-${SPARK_BIN_NAME}.tgz?action=download"
RUN tar zxvf spark-${SPARK_VERSION}-bin-${SPARK_BIN_NAME}.tgz
RUN mv spark-${SPARK_VERSION}-bin-${SPARK_BIN_NAME} /opt/spark
RUN rm spark-${SPARK_VERSION}-bin-${SPARK_BIN_NAME}.tgz
