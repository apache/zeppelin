#!/bin/bash

#
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

# The script helps making a release.
# You need specify a release name and branch|tag name.
#
# Here's some helpful documents for the release
# http://www.apache.org/dev/release.html
# http://www.apache.org/dev/release-publishing
# http://incubator.apache.org/guides/releasemanagement.html
# http://incubator.apache.org/guides/release.html
# http://www.apache.org/dev/release-signing.html
# http://www.apache.org/dev/publishing-maven-artifacts.html

if [[ -z "${TAR}" ]]; then
    TAR=/usr/bin/bsdtar    
fi

if [[ -z "${WORKING_DIR}" ]]; then
    WORKING_DIR=/tmp/incubator-zeppelin-release
fi


if [[ $# -ne 2 ]]; then
    echo "usage) $0 [Release name] [Branch or Tag]"
    echo "   ex. $0 0.5.0-incubating branch-0.5"
    exit 1
fi

RELEASE_NAME="${1}"
BRANCH="${2}"


if [[ -d "${WORKING_DIR}" ]]; then
    echo "Dir ${WORKING_DIR} already exists"
    exit 1
fi

mkdir ${WORKING_DIR}

echo "Cloning the source and packaging"
# clone source
git clone -b ${BRANCH} git@github.com:apache/incubator-zeppelin.git ${WORKING_DIR}/zeppelin
if [[ $? -ne 0 ]]; then
    echo "Can not clone source repository"
    exit 1
fi

# remove unnecessary files
rm ${WORKING_DIR}/zeppelin/.gitignore
rm -rf ${WORKING_DIR}/zeppelin/.git



# create source package
cp -r ${WORKING_DIR}/zeppelin ${WORKING_DIR}/zeppelin-${RELEASE_NAME}
tar cvzf ${WORKING_DIR}/zeppelin-${RELEASE_NAME}.tgz ${WORKING_DIR}/zeppelin-${RELEASE_NAME}

echo "Signing the source package"
cd ${WORKING_DIR}
gpg --armor --output zeppelin-${RELEASE_NAME}.tgz.asc --detach-sig ${WORKING_DIR}/zeppelin-${RELEASE_NAME}.tgz
gpg --print-md MD5 zeppelin-${RELEASE_NAME}.tgz > ${WORKING_DIR}/zeppelin-${RELEASE_NAME}.tgz.md5
gpg --print-md SHA512 zeppelin-${RELEASE_NAME}.tgz > ${WORKING_DIR}/zeppelin-${RELEASE_NAME}.tgz.sha




# remove non release files and dirs
rm -rf ${WORKING_DIR}/zeppelin
rm -rf ${WORKING_DIR}/zeppelin-${RELEASE_NAME}
echo "Release files are created under ${WORKING_DIR}"
