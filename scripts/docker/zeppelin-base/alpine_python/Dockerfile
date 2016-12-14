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

FROM zeppelin:base
MAINTAINER Apache Software Foundation <dev@zeppelin.apache.org>

ENV LOG_TAG="[ZEPPELIN_BASE_PYTHON]:"

RUN echo "$LOG_TAG Install required packages" && \
    echo @testing http://dl-cdn.alpinelinux.org/alpine/edge/testing >> /etc/apk/repositories && \
    apk add --no-cache --virtual=build_deps build-base make gcc g++ tar

RUN echo "$LOG_TAG Install python related packages" && \
        # for numpy: https://github.com/docker-library/python/issues/112
        ln -s /usr/include/locale.h /usr/include/xlocale.h && \
        apk add --no-cache --virtual=python_deps \
            py-numpy freetype libpng tk tcl && \
        apk add --no-cache --virtual=python_build_deps \
            musl-dev linux-headers gfortran \
            freetype-dev py-numpy-dev@testing \
            py-numpy python-dev libpng-dev libxml2-dev libxslt-dev \
            tk-dev tcl-dev && \
        apk add --no-cache python py-pip && \
        pip install --no-cache-dir --upgrade pip && \
        pip install --no-cache-dir numpy matplotlib

RUN echo "$LOG_TAG Cleanup" && \
        apk del build_deps && \
        apk del python_build_deps


