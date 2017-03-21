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

WORKING_DIR="/tmp/apache-zeppelin"

for var in CURRENT_VERSION RELEASE_VERSION NEXT_DEV_VERSION RC_TAG GIT_BRANCH; do
  if [[ -z "${!var}" ]]; then
    echo "You need ${var} variable set"
    exit 1
  fi
done

set -e

git clone https://git-wip-us.apache.org/repos/asf/zeppelin.git "${WORKING_DIR}"
pushd "${WORKING_DIR}" 

git checkout "${GIT_BRANCH}"

# Create release version
./dev/change_zeppelin_version.sh "${CURRENT_VERSION}" "${RELEASE_VERSION}"
git commit -a -m "Preparing Apache Zeppelin release ${RELEASE_VERSION}"
echo "Creating tag ${RC_TAG} at the head of ${GIT_BRANCH}"
git tag "${RC_TAG}"

# Create next dev version
./dev/change_zeppelin_version.sh "${RELEASE_VERSION}" "${NEXT_DEV_VERSION}"
git commit -a -m "Preparing development version ${NEXT_DEV_VERSION}"

git push origin "${RC_TAG}"
git push origin HEAD:"${GIT_BRANCH}" 

popd
rm -rf "${WORKING_DIR}"
