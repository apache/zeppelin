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
FROM maven:3.5-jdk-8 as builder
ADD . /workspace/zeppelin
WORKDIR /workspace/zeppelin
# Allow npm and bower to run with root privileges
RUN echo "unsafe-perm=true" > ~/.npmrc && \
    echo '{ "allow_root": true }' > ~/.bowerrc && \
    mvn -B package -DskipTests -Pbuild-distr -Pspark-3.0 -Pinclude-hadoop -Phadoop3 -Pspark-scala-2.12 -Pweb-angular && \
    # Example with doesn't compile all interpreters
    # mvn -B package -DskipTests -Pbuild-distr -Pspark-3.0 -Pinclude-hadoop -Phadoop3 -Pspark-scala-2.12 -Pweb-angular -pl '!groovy,!submarine,!livy,!hbase,!pig,!file,!flink,!ignite,!kylin,!lens' && \
    mv /workspace/zeppelin/zeppelin-distribution/target/zeppelin-*/zeppelin-* /opt/zeppelin/ && \
    # Removing stuff saves time, because docker creates a temporary layer
    rm -rf ~/.m2 && \
    rm -rf /workspace/zeppelin/*

FROM ubuntu:20.04
COPY --from=builder /opt/zeppelin /opt/zeppelin
