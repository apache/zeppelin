#!/usr/bin/env bash

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

if [ $# -lt 3 ];
then
    echo "USAGE: $0 version linux platform"
    echo "* version: 0.6.2 (released zeppelin binary version)"
    echo "* linux: [alpine]"
    echo "* platform: [java, python, r]"
    exit 1
fi

TAG="[CREATE-DOCKERFILE]"
VERSION=$1
LINUX=$2
PLATFORM=$3

BASE_DIR="../zeppelin-base/${LINUX}/${PLATFORM}"
TEMPLATE_DOCKERFILE="Dockerfile.template"

if [ ! -d "$BASE_DIR" ]; then
    echo "${TAG} Base Directory doesn't exist: ${BASE_DIR}"
    exit 1
fi

DOCKER_DIR="${LINUX}/${VERSION}_${PLATFORM}"
BASE_IMAGE_TAG="${LINUX}-base_${PLATFORM}"

if [ ! -d "$DOCKER_DIR" ]; then
    echo "${TAG} Creating Directory: ${DOCKER_DIR}"
    mkdir -p ${DOCKER_DIR}

    echo "${TAG} Copying File: ${DOCKER_DIR}/Dockerfile"
    cp ${TEMPLATE_DOCKERFILE} ${DOCKER_DIR}/Dockerfile

    echo "${TAG} Set Base Image Tag: ${TAG}"
    sed -i '' -e "s/zeppelin:tag/zeppelin:${BASE_IMAGE_TAG}/g" ${DOCKER_DIR}/Dockerfile
    echo "${TAG} Set Version: ${VERSION}"
    sed -i '' -e "s/Z_VERSION=\"0.0.0\"/Z_VERSION=\"${VERSION}\"/g" ${DOCKER_DIR}/Dockerfile
else
    echo "${TAG} Directory already exists: ${DOCKER_DIR}"
fi

