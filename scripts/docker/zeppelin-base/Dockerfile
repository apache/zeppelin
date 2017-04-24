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

ENV JAVA_HOME /usr/lib/jvm/java-1.7-openjdk
ENV PATH $PATH:$JAVA_HOME/bin

RUN apk add --update bash curl openjdk7-jre wget ca-certificates python build-base make gcc g++ java-cacerts openssl && \
    rm /usr/lib/jvm/java-1.7-openjdk/jre/lib/security/cacerts && \
    ln -s /etc/ssl/certs/java/cacerts /usr/lib/jvm/java-1.7-openjdk/jre/lib/security/cacerts && \
    curl --silent \
    --location https://github.com/sgerrand/alpine-pkg-R/releases/download/3.3.1-r0/R-3.3.1-r0.apk --output /var/cache/apk/R-3.3.1-r0.apk && \
    apk add --update --allow-untrusted /var/cache/apk/R-3.3.1-r0.apk && \
    curl --silent \
    --location https://github.com/sgerrand/alpine-pkg-R/releases/download/3.3.1-r0/R-dev-3.3.1-r0.apk --output /var/cache/apk/R-dev-3.3.1-r0.apk && \
    apk add --update --allow-untrusted /var/cache/apk/R-dev-3.3.1-r0.apk && \
    R -e "install.packages('knitr', repos = 'http://cran.us.r-project.org')" && \
    apk del curl build-base make gcc g++ && \
    rm -rf /var/cache/apk/*

RUN wget -O /usr/local/bin/dumb-init https://github.com/Yelp/dumb-init/releases/download/v1.1.3/dumb-init_1.1.3_amd64
RUN chmod +x /usr/local/bin/dumb-init

# ports for zeppelin
EXPOSE 8080 7077

ENTRYPOINT ["/usr/local/bin/dumb-init", "--"]
