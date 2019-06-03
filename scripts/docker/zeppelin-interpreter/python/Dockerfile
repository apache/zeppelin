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

FROM zeppelin-interpreter-base:latest

ARG miniconda_version="py37_4.8.2"
ARG miniconda_sha256="957d2f0f0701c3d1335e3b39f235d197837ad69a944fa6f5d8ad2c686b69df3b"

ENV MINICONDA_VERSION=${miniconda_version}

# Install additional_conda_packages
COPY python_conda_packages.txt /python_conda_packages.txt
# Some packages are not available via conda
COPY pip_packages.txt /pip_packages.txt
# Install Miniconda3
RUN set -ex && \
    wget -nv https://repo.anaconda.com/miniconda/Miniconda3-${MINICONDA_VERSION}-Linux-x86_64.sh -O miniconda.sh && \
    echo "${miniconda_sha256} miniconda.sh" > anaconda.sha256 && \
    sha256sum --strict -c anaconda.sha256 && \
    bash miniconda.sh -b -p /opt/conda && \
    export PATH=/opt/conda/bin:$PATH && \
    conda config --set always_yes yes --set changeps1 no && \
    conda info -a && \
    conda config --add channels conda-forge && \
    conda install -y --quiet --file /python_conda_packages.txt && \
    pip install -q -r /pip_packages.txt  && \
    # Cleanup
    rm -v miniconda.sh anaconda.sha256  && \
    # Cleanup based on https://github.com/ContinuumIO/docker-images/commit/cac3352bf21a26fa0b97925b578fb24a0fe8c383
    find /opt/conda/ -follow -type f -name '*.a' -delete && \
    find /opt/conda/ -follow -type f -name '*.js.map' -delete && \
    conda clean -ay

ENV PATH /opt/conda/bin:$PATH

COPY --from=zeppelin-distribution /opt/zeppelin/interpreter/python ${Z_HOME}/interpreter/python
