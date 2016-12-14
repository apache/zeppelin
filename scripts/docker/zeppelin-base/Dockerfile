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

FROM alpine:3.4
MAINTAINER Apache Software Foundation <dev@zeppelin.apache.org>

ENV LOG_TAG="[ZEPPELIN_BASE]:" \
    LANG=C.UTF-8 \
    DUMBINIT_VERSION="1.2.0"

RUN echo "$LOG_TAG Install basic packages" && \
    apk update && \
    echo "http://dl-1.alpinelinux.org/alpine/edge/community" >> /etc/apk/repositories; \
    echo "http://dl-2.alpinelinux.org/alpine/edge/community" >> /etc/apk/repositories; \
    echo "http://dl-3.alpinelinux.org/alpine/edge/community" >> /etc/apk/repositories; \
    echo "http://dl-4.alpinelinux.org/alpine/edge/community" >> /etc/apk/repositories; \
    echo "http://dl-5.alpinelinux.org/alpine/edge/community" >> /etc/apk/repositories; \
    echo "http://dl-6.alpinelinux.org/alpine/edge/community" >> /etc/apk/repositories && \
    apk add --no-cache bash dumb-init curl wget && \
    rm -rf /var/cache/apk/*

RUN echo "$LOG_TAG Install java ${JAVA_VERSION}" && \
    { \
		echo '#!/bin/sh'; \
		echo 'set -e'; \
		echo; \
		echo 'dirname "$(dirname "$(readlink -f "$(which javac || which java)")")"'; \
	} > /usr/local/bin/docker-java-home \
	&& chmod +x /usr/local/bin/docker-java-home

ENV JAVA_HOME=/usr/lib/jvm/java-1.7-openjdk \
    PATH=$PATH:/usr/lib/jvm/java-1.7-openjdk/jre/bin:/usr/lib/jvm/java-1.7-openjdk/bin \
    JAVA_VERSION=7u121 \
    JAVA_ALPINE_VERSION=7.121.2.6.8-r0

RUN set -x \
	&& apk add --no-cache \
		openjdk7="$JAVA_ALPINE_VERSION" \
	&& [ "$JAVA_HOME" = "$(docker-java-home)" ]

ENTRYPOINT ["/usr/bin/dumb-init", "--"]
