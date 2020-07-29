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

FROM alpine:3.11 AS downloader

ENV TINI_VERSION=v0.19.0

RUN set -ex && \
    /sbin/apk add --no-cache wget gnupg
    # Install tini
RUN set -ex && \
    /usr/bin/wget -q https://github.com/krallin/tini/releases/download/${TINI_VERSION}/tini-static-amd64 -O /sbin/tini && \
    /usr/bin/wget -q https://github.com/krallin/tini/releases/download/${TINI_VERSION}/tini-static-amd64.asc -O /tmp/tini.asc && \
    /usr/bin/gpg --batch --keyserver keyserver.ubuntu.com --recv-keys 595E85A6B1B4779EA4DAAEC70B588DFF0527A9B7 && \
    /usr/bin/gpg --batch --verify /tmp/tini.asc /sbin/tini && \
    /bin/chmod +x /sbin/tini && \
    /bin/rm -v /tmp/tini.asc

# Prepare all interpreter settings for Zeppelin server
# This steps are not needed, if you you add only specific interpreters settings to your image
FROM alpine:3.11 AS interpreter-settings
COPY --from=zeppelin-distribution /opt/zeppelin/interpreter /tmp/interpreter
RUN mkdir -p /opt/zeppelin/interpreter && \
    cd /tmp/interpreter && \
    find . -name 'interpreter-setting.json' -exec cp --parents \{\} /opt/zeppelin/interpreter \;

FROM ubuntu:18.04
LABEL maintainer="Apache Software Foundation <dev@zeppelin.apache.org>"

RUN set -ex && \
    apt-get -y update && \
    # Install language and other base packages
    DEBIAN_FRONTEND=noninteractive apt-get install -y language-pack-en openjdk-8-jre-headless wget && \
    # Cleanup
    rm -rf /var/lib/apt/lists/* && \
    apt-get autoclean && \
    apt-get clean

ARG version="0.9.0-SNAPSHOT"

ENV LANG=en_US.UTF-8 \
    LC_ALL=en_US.UTF-8 \
    JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64 \
    VERSION="${version}" \
    Z_HOME="/opt/zeppelin" \
    ZEPPELIN_ADDR="0.0.0.0" \
    ZEPPELIN_WAR_TEMPDIR="/tmp/webapps" \

# Copy Zeppelin related files
COPY --from=zeppelin-distribution /opt/zeppelin/zeppelin-web-${VERSION}.war ${Z_HOME}/
COPY --from=zeppelin-distribution /opt/zeppelin/zeppelin-web-angular-${VERSION}.war ${Z_HOME}/
COPY --from=zeppelin-distribution /opt/zeppelin/conf ${Z_HOME}/conf
COPY --from=zeppelin-distribution /opt/zeppelin/bin ${Z_HOME}/bin
COPY --from=zeppelin-distribution /opt/zeppelin/lib ${Z_HOME}/lib
COPY --from=zeppelin-distribution /opt/zeppelin/plugins ${Z_HOME}/plugins
COPY --from=zeppelin-distribution /opt/zeppelin/interpreter/zeppelin-interpreter-shaded-${VERSION}.jar ${Z_HOME}/interpreter/zeppelin-interpreter-shaded-${VERSION}.jar
# copy example notebooks
COPY --from=zeppelin-distribution /opt/zeppelin/notebook ${Z_HOME}/notebook
# copy k8s files
COPY --from=zeppelin-distribution /opt/zeppelin/k8s ${Z_HOME}/k8s

# Decide
## 1) Copy and activate all interpreters (default)
COPY --from=interpreter-settings /opt/zeppelin/interpreter ${Z_HOME}/interpreter
## 2) Copy and activate only a specific set of interpreter
# COPY --from=zeppelin-distribution /opt/zeppelin/interpreter/spark/interpreter-setting.json ${Z_HOME}/interpreter/spark/interpreter-setting.json
# COPY --from=zeppelin-distribution /opt/zeppelin/interpreter/jdbc/interpreter-setting.json ${Z_HOME}/interpreter/jdbc/interpreter-setting.json
# COPY --from=zeppelin-distribution /opt/zeppelin/interpreter/md/interpreter-setting.json ${Z_HOME}/interpreter/md/interpreter-setting.json

COPY log4j.properties ${Z_HOME}/conf/
COPY --from=downloader /sbin/tini /sbin/tini

RUN mkdir -p "${Z_HOME}/logs" "${Z_HOME}/run" "${Z_HOME}/notebook" "${Z_HOME}/local-repo" && \
     # Allow process to edit /etc/passwd, to create a user entry for zeppelin
    chgrp root /etc/passwd && chmod ug+rw /etc/passwd && \
    # Give access to some specific folders
    chmod -R 775 "${Z_HOME}/logs" "${Z_HOME}/run" "${Z_HOME}/conf" "${Z_HOME}/notebook" "${Z_HOME}/local-repo"

USER 1000

EXPOSE 8080

ENTRYPOINT [ "/sbin/tini", "--" ]
WORKDIR ${Z_HOME}
CMD ["bin/zeppelin.sh"]
