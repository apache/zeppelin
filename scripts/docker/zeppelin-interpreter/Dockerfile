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

ARG ZEPPELIN_DISTRIBUTION_IMAGE=zeppelin-distribution:latest
FROM $ZEPPELIN_DISTRIBUTION_IMAGE AS zeppelin-distribution

FROM ubuntu:22.04

LABEL maintainer="Apache Software Foundation <dev@zeppelin.apache.org>"

ARG version="0.10.0"

ENV VERSION="${version}" \
    ZEPPELIN_HOME="/opt/zeppelin"

# Install Java for zeppelin interpreter
# Install micromamba to install a python environment via conda
RUN set -ex && \
    /usr/bin/apt-get update && \
    DEBIAN_FRONTEND=noninteractive /usr/bin/apt-get install -y openjdk-11-jre-headless wget tini bzip2 && \
    /usr/bin/wget -qO- https://micromamba.snakepit.net/api/micromamba/linux-64/latest | tar -xvj bin/micromamba && \
    # Cleanup
    /usr/bin/apt-get clean && \
    /bin/rm -rf /var/lib/apt/lists/*


COPY --from=zeppelin-distribution /opt/zeppelin/bin ${ZEPPELIN_HOME}/bin
COPY log4j.properties ${ZEPPELIN_HOME}/conf/
COPY log4j_yarn_cluster.properties ${ZEPPELIN_HOME}/conf/
# Decide:
## 1) Build a huge image with all interpreters (default)
COPY --from=zeppelin-distribution /opt/zeppelin/interpreter ${ZEPPELIN_HOME}/interpreter
## 2) Build an image with only some interpreters
#### Copy interpreter-shaded JAR, needed for all interpreters
### COPY --from=zeppelin-distribution /opt/zeppelin/interpreter/zeppelin-interpreter-shaded-${VERSION}.jar ${ZEPPELIN_HOME}/interpreter/zeppelin-interpreter-shaded-${VERSION}.jar
#### Copy specific interpreters,  replace "${interpreter_name}" with your interpreter. Of course you can repeat the line with defferent interpreter
### COPY --from=zeppelin-distribution /opt/zeppelin/interpreter/${interpreter_name}  ${ZEPPELIN_HOME}/interpreter/${interpreter_name}


# Decide: Install conda to manage python and R packages. Maybe adjust the packages env_python_3_with_R
# Install python and R packages via conda
COPY env_python_3_with_R.yml /env_python_3_with_R.yml
# To improve the build time, the Zeppelin team recommends a conda proxy
# COPY condarc /etc/conda/condarc
RUN set -ex && \
    micromamba create -y -p /opt/conda -f env_python_3_with_R.yml && \
    micromamba clean -ay

ENV PATH=/opt/conda/bin:$PATH \
    SPARK_HOME=/opt/conda/lib/python3.9/site-packages/pyspark

# Allow to modify conda packages. This allows malicious code to be injected into other interpreter sessions, therefore it is disabled by default
# chmod -R ug+rwX /opt/conda

RUN mkdir -p "${ZEPPELIN_HOME}/logs" "${ZEPPELIN_HOME}/run" "${ZEPPELIN_HOME}/local-repo" && \
     # Allow process to edit /etc/passwd, to create a user entry for zeppelin
    chgrp root /etc/passwd && chmod ug+rw /etc/passwd && \
    # Give access to some specific folders
    chmod -R 775 "${ZEPPELIN_HOME}/logs" "${ZEPPELIN_HOME}/run" "${ZEPPELIN_HOME}/local-repo"

USER 1000
ENTRYPOINT [ "/usr/bin/tini", "--" ]
WORKDIR ${ZEPPELIN_HOME}
