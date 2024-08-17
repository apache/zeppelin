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

RUN ./mvnw package -am -pl zeppelin-interpreter-shaded,zeppelin-interpreter,shell -DskipTests


FROM openjdk:11

COPY --from=builder /zeppelin/bin /zeppelin/bin/
COPY --from=builder /zeppelin/conf /zeppelin/conf

COPY --from=builder /zeppelin/interpreter/sh /zeppelin/interpreter/sh
COPY --from=builder /zeppelin/zeppelin-interpreter-shaded/target /zeppelin/zeppelin-interpreter-shaded/target

WORKDIR /zeppelin

ENV SHELL_INTERPRETER_PORT=8081

RUN chmod +x ./bin/interpreter.sh

CMD ["./bin/interpreter.sh", \
    "-d", "interpreter/sh", \
    "-c", "host.docker.internal", \
    "-p", "${INTERPRETER_EVENT_SERVER_PORT}", \
    "-r", "${SHELL_INTERPRETER_PORT}:${SHELL_INTERPRETER_PORT}", \
    "-i", "sh-shared_process", \
    "-l", "./local-repo", \
    "-g", "sh"]
