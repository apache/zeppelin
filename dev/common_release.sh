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

# common fucntions

if [[ -z "${TAR}" ]]; then
  TAR="/usr/bin/tar"
fi

if [[ -z "${SHASUM}" ]]; then
  SHASUM="/usr/bin/shasum"
fi

if [[ -z "${WORKING_DIR}" ]]; then
  WORKING_DIR="/tmp/zeppelin-release"
fi

mkdir "${WORKING_DIR}"

# If set to 'yes', release script will deploy artifacts to SNAPSHOT repository.
DO_SNAPSHOT='no'

usage() {
  echo "usage) $0 [Release version] [Branch or Tag]"
  echo "   ex. $0 0.6.0 v0.6.0"
  exit 1
}

function git_clone() { 
  echo "Clone the source"
  # clone source
  git clone https://git-wip-us.apache.org/repos/asf/zeppelin.git "${WORKING_DIR}/zeppelin"

  if [[ $? -ne 0 ]]; then
    echo "Can not clone source repository"
    exit 1
  fi

  cd "${WORKING_DIR}/zeppelin"
  git checkout "${GIT_TAG}"
  echo "Checked out ${GIT_TAG}"

  # remove unnecessary files
  rm "${WORKING_DIR}/zeppelin/.gitignore"
  rm -rf "${WORKING_DIR}/zeppelin/.git"
  rm -rf "${WORKING_DIR}/zeppelin/.github"
  rm -rf "${WORKING_DIR}/zeppelin/docs"
}
