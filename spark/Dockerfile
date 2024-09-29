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
FROM openjdk:11 as builder

COPY . /zeppelin/

WORKDIR /zeppelin

RUN chmod +x ./mvnw
RUN ./mvnw clean package -am -pl zeppelin-interpreter-shaded,zeppelin-interpreter,spark -DskipTests

FROM openjdk:11

COPY --from=builder /zeppelin/bin /zeppelin/bin/
COPY --from=builder /zeppelin/conf /zeppelin/conf

COPY --from=builder /zeppelin/interpreter/spark /zeppelin/interpreter/spark
COPY --from=builder /zeppelin/zeppelin-interpreter-shaded/target /zeppelin/zeppelin-interpreter-shaded/target

WORKDIR /zeppelin

ENV SPARK_INTERPRETER_PORT=8085
ENV SPARK_HOME=/opt/spark

RUN wget https://archive.apache.org/dist/spark/spark-3.5.2/spark-3.5.2-bin-hadoop3.tgz && \
    tar -xvf spark-3.5.2-bin-hadoop3.tgz && \
    mv spark-3.5.2-bin-hadoop3/ /opt/spark && \
    rm -rf spark-3.5.2-bin-hadoop3.tgz

RUN chmod +x ./bin/interpreter.sh

CMD ./bin/interpreter.sh \
    -d ./interpreter/spark \
    -c host.docker.internal \
    -p ${INTERPRETER_EVENT_SERVER_PORT} \
    -r ${SPARK_INTERPRETER_PORT}:${SPARK_INTERPRETER_PORT} \
    -i spark-shared_process \
    -l ./local-repo \
    -g spark
