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

RUN ./mvnw clean package -DskipTests -Pinclude-hadoop


FROM openjdk:11

RUN apt-get update && \
    apt-get install -y r-base && \
    apt-get install -y python3 python3-pip && \
        pip3 install jupyter-client grpcio protobuf~=3.20 ipython ipykernel && \
    ln -s /usr/bin/python3 /usr/bin/python && \
    wget https://archive.apache.org/dist/spark/spark-3.5.2/spark-3.5.2-bin-hadoop3.tgz && \
        tar -xvf spark-3.5.2-bin-hadoop3.tgz && \
        mv spark-3.5.2-bin-hadoop3/ /opt/spark && \
        rm -rf spark-3.5.2-bin-hadoop3.tgz && \
    rm -rf /var/lib/apt/lists/*

RUN R -e "install.packages('IRkernel', repos='http://cran.r-project.org')" && \
    R -e "IRkernel::installspec(user = FALSE)" && \
    R -e "install.packages('devtools', repos='http://cran.us.r-project.org')" && \
    R -e "install.packages('knitr', repos='http://cran.us.r-project.org')" && \
    R -e "install.packages('ggplot2', repos='http://cran.us.r-project.org')"

COPY --from=builder /zeppelin/bin /zeppelin/bin/
COPY --from=builder /zeppelin/conf /zeppelin/conf

COPY --from=builder /zeppelin/interpreter/r /zeppelin/interpreter/r
COPY --from=builder /zeppelin/zeppelin-interpreter-shaded/target /zeppelin/zeppelin-interpreter-shaded/target

WORKDIR /zeppelin

ENV R_INTERPRETER_PORT=8086

RUN chmod +x ./bin/interpreter.sh

CMD ./bin/interpreter.sh \
 -d ./interpreter/r \
 -c host.docker.internal \
 -p "${INTERPRETER_EVENT_SERVER_PORT}" \
 -r "${R_INTERPRETER_PORT}:${R_INTERPRETER_PORT}" \
 -i r-shared_process \
 -l ./local-repo \
 -g r
