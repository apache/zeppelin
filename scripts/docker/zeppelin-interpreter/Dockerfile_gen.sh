#!/bin/bash

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

# This script outputs a Dockerfile for building the zeppelin interpreter image that contains some specific interpreters.

function output_license {
    cat <<EOF > Dockerfile
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
# limitations under the License."


EOF
}

function output_begin {
    cat <<EOF >> Dockerfile
FROM apache/zeppelin:$1 AS zeppelin-distribution

FROM ubuntu:20.04

LABEL maintainer="Apache Software Foundation <dev@zeppelin.apache.org>"

ARG version="$1"

ENV VERSION="\${version}" \\
    ZEPPELIN_HOME="/opt/zeppelin"

RUN set -ex && \\
    apt-get -y update && \\
    DEBIAN_FRONTEND=noninteractive apt-get install -y openjdk-8-jre-headless wget tini && \\
    # Cleanup
    rm -rf /var/lib/apt/lists/* && \\
    apt-get autoclean && \\
    apt-get clean

COPY --from=zeppelin-distribution /opt/zeppelin/bin \${ZEPPELIN_HOME}/bin
COPY log4j.properties \${ZEPPELIN_HOME}/conf/
COPY log4j_yarn_cluster.properties \${ZEPPELIN_HOME}/conf/

# Copy interpreter-shaded JAR, needed for all interpreters
COPY --from=zeppelin-distribution /opt/zeppelin/interpreter/zeppelin-interpreter-shaded-\${VERSION}.jar \${ZEPPELIN_HOME}/interpreter/zeppelin-interpreter-shaded-\${VERSION}.jar

EOF
}

function output_interpreter_file () {
    cat << EOF >> Dockerfile
# copy interpreter $1
COPY --from=zeppelin-distribution /opt/zeppelin/interpreter/$1  \${ZEPPELIN_HOME}/interpreter/$1

EOF
}

function output_conda_py_r () {
    cat << EOF >> Dockerfile
# Install conda to manage python and R packages
ARG miniconda_version="py37_4.9.2"
# Hashes via https://docs.conda.io/en/latest/miniconda_hashes.html
ARG miniconda_sha256="79510c6e7bd9e012856e25dcb21b3e093aa4ac8113d9aa7e82a86987eabe1c31"
# Install python and R packages via conda
COPY $1 /$1
RUN set -ex && \\
    wget -nv https://repo.anaconda.com/miniconda/Miniconda3-\${miniconda_version}-Linux-x86_64.sh -O miniconda.sh && \\
    echo "\${miniconda_sha256} miniconda.sh" > anaconda.sha256 && \\
    sha256sum --strict -c anaconda.sha256 && \\
    bash miniconda.sh -b -p /opt/conda && \\
    export PATH=/opt/conda/bin:\$PATH && \\
    conda config --set always_yes yes --set changeps1 no && \\
    conda info -a && \\
    conda install mamba -c conda-forge && \\
    mamba env update -f /$1 --prune && \\
    # Cleanup
    rm -v miniconda.sh anaconda.sha256  && \\
    # Cleanup based on https://github.com/ContinuumIO/docker-images/commit/cac3352bf21a26fa0b97925b578fb24a0fe8c383
    find /opt/conda/ -follow -type f -name '*.a' -delete && \\
    find /opt/conda/ -follow -type f -name '*.js.map' -delete && \\
    mamba clean -ay
    # Allow to modify conda packages. This allows malicious code to be injected into other interpreter sessions, therefore it is disabled by default
    # chmod -R ug+rwX /opt/conda
ENV PATH /opt/conda/bin:\$PATH

EOF
}

function output_end {
    cat << EOF >> Dockerfile
RUN mkdir -p "\${ZEPPELIN_HOME}/logs" "\${ZEPPELIN_HOME}/run" "\${ZEPPELIN_HOME}/local-repo" && \\
    # Allow process to edit /etc/passwd, to create a user entry for zeppelin
    chgrp root /etc/passwd && chmod ug+rw /etc/passwd && \\
    # Give access to some specific folders
    chmod -R 775 "\${ZEPPELIN_HOME}/logs" "\${ZEPPELIN_HOME}/run" "\${ZEPPELIN_HOME}/local-repo"


USER 1000
ENTRYPOINT [ "/usr/bin/tini", "--" ]
WORKDIR \${ZEPPELIN_HOME}
EOF
}


function usage {
  cat <<EOF
Usage: $0 [options] [command]
This script outputs a Dockerfile for building the zeppelin image that contains some specific interpreters.

Options:
  -i interpreter        (Optional) A comma-separated list of interpreter directory names (under /path/to/zeppelin/interpreter/)
                        that need to be add into the docker image.
                        By default, it will add the spark interpreter.
  -c conda yaml file    (Optional) Specify the conda yaml file that manages python and R packages. By default, it will not install
                        python and R packages through conda.
  -v zeppelin version   (Optional) Specify the version of zeppelin. By default, the version is "0.9.0".

Examples:
  - Output the Dockerfile for building the zeppelin image that contains spark and python interpreter.
    $0 -i spark,python

  - Output the Dockerfile for building the zeppelin image that contains spark and python interpreter and specify the 
    conda yaml file "python3.yaml"
    $0 -i spark,python -c python3.yaml
EOF
}

if [[ "$@" = *--help ]] || [[ "$@" = *-h ]]; then
  usage
  exit 0
fi

conda_file=""
interpreter_files="spark"
zeppelin_version="0.9.0"

while getopts c:i:v: opt
do
    case "${opt}" in
        c)  conda_file="$OPTARG";;
        i)  set -f
            IFS=,
            interpreter_files=($OPTARG);;
        v)  zeppelin_version="$OPTARG";;
    esac
done

output_license
output_begin $zeppelin_version
# echo ${#interpreter_files[*]}
for i in ${interpreter_files[*]}; do
    output_interpreter_file $i
done
if test ${#conda_file} -ne 0
then
    output_conda_py_r $conda_file
fi
output_end
