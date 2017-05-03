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

if [ $# -lt 1 ];
then
    echo "USAGE: $0 version"
    echo "* version: 0.6.2 (released zeppelin binary version)"
    exit 1
fi

TAG="[CREATE-DOCKERFILE]"
VERSION=$1

BASE_DIR="./base/"
TEMPLATE_DOCKERFILE="./bin-template/Dockerfile"

if [ ! -d "$BASE_DIR" ]; then
    echo "${TAG} Base Directory doesn't exist: ${BASE_DIR}"
    exit 1
fi

TARGET_DIR="${VERSION}"
BASE_IMAGE_TAG="base"

if [ ! -d "$TARGET_DIR" ]; then
    echo "${TAG} Creating Directory: ${TARGET_DIR}"
    mkdir -p ${TARGET_DIR}

    echo "${TAG} Copying File: ${TARGET_DIR}/Dockerfile"
    cp ${TEMPLATE_DOCKERFILE} ${TARGET_DIR}/Dockerfile

    echo "${TAG} Set Version: ${VERSION}"
    sed -i '' -e "s/Z_VERSION=\"0.0.0\"/Z_VERSION=\"${VERSION}\"/g" ${TARGET_DIR}/Dockerfile
else
    echo "${TAG} Directory already exists: ${TARGET_DIR}"
fi
