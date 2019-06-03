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

FROM zeppelin-interpreter-python:latest

# Install additional_conda_packages
COPY r_conda_packages.txt /r_conda_packages.txt
# Install necessary r packages
RUN set -ex && \
    conda install -y --quiet --file /r_conda_packages.txt && \
    # Cleanup based on https://github.com/ContinuumIO/docker-images/commit/cac3352bf21a26fa0b97925b578fb24a0fe8c383
    find /opt/conda/ -follow -type f -name '*.a' -delete && \
    find /opt/conda/ -follow -type f -name '*.js.map' -delete && \
    conda clean -ay

COPY --from=zeppelin-distribution /opt/zeppelin/interpreter/r ${Z_HOME}/interpreter/r
