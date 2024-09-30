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

RUN ./mvnw clean package -am -pl zeppelin-interpreter-shaded,zeppelin-interpreter,mongodb -DskipTests


FROM openjdk:11

RUN apt-get update && \
    apt-get install -y wget gnupg && \
    wget -qO- https://www.mongodb.org/static/pgp/server-7.0.asc | tee /etc/apt/trusted.gpg.d/mongodb-server-7.0.asc

RUN echo "deb [ arch=amd64,arm64 ] https://repo.mongodb.org/apt/ubuntu focal/mongodb-org/7.0 multiverse" | tee /etc/apt/sources.list.d/mongodb-org-7.0.list

RUN apt-get update && \
    apt-get install -y mongodb-mongosh && \
    apt-get clean && rm -rf /var/lib/apt/lists/*

COPY --from=builder /zeppelin/bin /zeppelin/bin/
COPY --from=builder /zeppelin/conf /zeppelin/conf
COPY --from=builder /zeppelin/interpreter/mongodb /zeppelin/interpreter/mongodb
COPY --from=builder /zeppelin/zeppelin-interpreter-shaded/target /zeppelin/zeppelin-interpreter-shaded/target

WORKDIR /zeppelin

ENV MONGO_DB_INTERPRETER_PORT=8083

RUN chmod +x ./bin/interpreter.sh

CMD ./bin/interpreter.sh \
     -d ./interpreter/mongodb \
     -c host.docker.internal \
     -p "${INTERPRETER_EVENT_SERVER_PORT}" \
     -r "${MONGO_DB_INTERPRETER_PORT}:${MONGO_DB_INTERPRETER_PORT}" \
     -i mongodb-shared_process \
     -l ./local-repo \
     -g mongodb
