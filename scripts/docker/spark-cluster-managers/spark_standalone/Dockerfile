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
ARG SPARK_VERSION=3.5.8
ARG SCALA_VERSION=2.12
ARG JAVA_VERSION=11
FROM apache/spark:${SPARK_VERSION}-scala${SCALA_VERSION}-java${JAVA_VERSION}-python3-ubuntu

USER root

RUN apt-get update \
    && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
      ca-certificates \
      openssh-server \
      procps \
    && rm -rf /var/lib/apt/lists/* \
    && mkdir -p /run/sshd

# update boot script
COPY entrypoint.sh /etc/entrypoint.sh
RUN chown root:root /etc/entrypoint.sh
RUN chmod 700 /etc/entrypoint.sh

#spark
EXPOSE 8080 7077 8888 8081

ENTRYPOINT ["/etc/entrypoint.sh"]
