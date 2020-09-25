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

FROM zeppelin-distribution:latest AS zeppelin-distribution

FROM ubuntu:20.04

LABEL maintainer="Apache Software Foundation <dev@zeppelin.apache.org>"

ARG version="0.9.0-SNAPSHOT"

ENV VERSION="${version}" \
    Z_HOME="/opt/zeppelin"

RUN set -ex && \
    apt-get -y update && \
    DEBIAN_FRONTEND=noninteractive apt-get install -y openjdk-8-jre-headless wget tini && \
    # Cleanup
    rm -rf /var/lib/apt/lists/* && \
    apt-get autoclean && \
    apt-get clean

COPY --from=zeppelin-distribution /opt/zeppelin/bin ${Z_HOME}/bin
COPY log4j.properties ${Z_HOME}/conf/
COPY log4j_yarn_cluster.properties ${Z_HOME}/conf/
# Decide:
## 1) Build a huge image with all interpreters (default)
COPY --from=zeppelin-distribution /opt/zeppelin/interpreter ${Z_HOME}/interpreter
## 2) Build an image with only some interpreters
#### Copy interpreter-shaded JAR, needed for all interpreters
### COPY --from=zeppelin-distribution /opt/zeppelin/interpreter/zeppelin-interpreter-shaded-${VERSION}.jar ${Z_HOME}/interpreter/zeppelin-interpreter-shaded-${VERSION}.jar
#### Copy specific interpreters,  replace "${interpreter_name}" with your interpreter. Of course you can repeat the line with defferent interpreter
### COPY --from=zeppelin-distribution /opt/zeppelin/interpreter/${interpreter_name}  ${Z_HOME}/interpreter/${interpreter_name}


# Decide: Install conda to manage python and R packages. Maybe adjust the packages in pip_packages.txt or conda_packages.txt
ARG miniconda_version="py38_4.8.3"
ARG miniconda_sha256="879457af6a0bf5b34b48c12de31d4df0ee2f06a8e68768e5758c3293b2daf688"
# Install python and R packages via conda
COPY conda_packages.txt /conda_packages.txt
# Some python packages are not available via conda, so we are using pip
COPY pip_packages.txt /pip_packages.txt
RUN set -ex && \
    wget -nv https://repo.anaconda.com/miniconda/Miniconda3-${miniconda_version}-Linux-x86_64.sh -O miniconda.sh && \
    echo "${miniconda_sha256} miniconda.sh" > anaconda.sha256 && \
    sha256sum --strict -c anaconda.sha256 && \
    bash miniconda.sh -b -p /opt/conda && \
    export PATH=/opt/conda/bin:$PATH && \
    conda config --set always_yes yes --set changeps1 no && \
    conda info -a && \
    conda config --add channels conda-forge && \
    conda install -y --quiet --file /conda_packages.txt && \
    pip install -q -r /pip_packages.txt  && \
    # Cleanup
    rm -v miniconda.sh anaconda.sha256  && \
    # Cleanup based on https://github.com/ContinuumIO/docker-images/commit/cac3352bf21a26fa0b97925b578fb24a0fe8c383
    find /opt/conda/ -follow -type f -name '*.a' -delete && \
    find /opt/conda/ -follow -type f -name '*.js.map' -delete && \
    conda clean -ay
    # Allow to modify conda packages. This allows malicious code to be injected into other interpreter sessions, therefore it is disabled by default
    # chmod -R ug+rwX /opt/conda
ENV PATH /opt/conda/bin:$PATH

RUN mkdir -p "${Z_HOME}/logs" "${Z_HOME}/run" "${Z_HOME}/local-repo" && \
     # Allow process to edit /etc/passwd, to create a user entry for zeppelin
    chgrp root /etc/passwd && chmod ug+rw /etc/passwd && \
    # Give access to some specific folders
    chmod -R 775 "${Z_HOME}/logs" "${Z_HOME}/run" "${Z_HOME}/local-repo"

USER 1000
ENTRYPOINT [ "/usr/bin/tini", "--" ]
WORKDIR ${Z_HOME}
