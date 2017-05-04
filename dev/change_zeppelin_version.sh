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

usage() {
  echo "usage) $0 [OLD version] [NEW version]"
  echo "   ex. $0 0.8.0-SNAPSHOT 0.8.0"
  exit 1
}

if [[ $# -ne 2 ]]; then
  usage
fi

FROM_VERSION="$1"
TO_VERSION="$2"

is_dev_version() {
  if [[ "$1" == *"SNAPSHOT" ]]; then
    return 0
  else
    return 1
  fi
} 

is_maintenance_version() {
  local version="$1"
  if [[ "${version}" == *"SNAPSHOT" ]]; then
    version = $(echo ${1} | cut -d'-' -f 1)
  fi 
  if [[ "${version}" == *".0" ]]; then
    return 1
  else
    return 0
  fi
}

# Change version in pom.xml
mvn versions:set -DnewVersion="${TO_VERSION}" -DgenerateBackupPoms=false > /dev/null 2>&1 

# Change version in example and package files
sed -i '' 's/-'"${FROM_VERSION}"'.jar",/-'"${TO_VERSION}"'.jar",/g' zeppelin-examples/zeppelin-example-clock/zeppelin-example-clock.json
sed -i '' 's/"version": "'"${FROM_VERSION}"'",/"version": "'"${TO_VERSION}"'",/g' zeppelin-web/src/app/tabledata/package.json
sed -i '' 's/"version": "'"${FROM_VERSION}"'",/"version": "'"${TO_VERSION}"'",/g' zeppelin-web/src/app/visualization/package.json
sed -i '' 's/"version": "'"${FROM_VERSION}"'",/"version": "'"${TO_VERSION}"'",/g' zeppelin-web/src/app/spell/package.json

# Change version in Dockerfile
sed -i '' 's/Z_VERSION="'"${FROM_VERSION}"'"/Z_VERSION="'"${TO_VERSION}"'"/g' scripts/docker/zeppelin/bin/Dockerfile

# When preparing new dev version from release tag, doesn't need to change docs version
if is_dev_version "${FROM_VERSION}" || ! is_dev_version "${TO_VERSION}"; then
  # When prepare new rc for the maintenance release
  if is_dev_version "${FROM_VERSION}" && is_maintenance_version "${TO_VERSION}" \
     && [[ "${FROM_VERSION}" == "${TO_VERSION}"* ]]; then
    FROM_VERSION=$(echo "${TO_VERSION}" | awk -F. '{ printf("%d.%d.%d", $1, $2, $3-1) }')
  fi

  # Change zeppelin version in docs config
  sed -i '' 's/ZEPPELIN_VERSION : '"${FROM_VERSION}"'$/ZEPPELIN_VERSION : '"$TO_VERSION"'/g' docs/_config.yml
  sed -i '' 's/BASE_PATH : \/docs\/'"${FROM_VERSION}"'$/BASE_PATH : \/docs\/'"$TO_VERSION"'/g' docs/_config.yml

  # Change interpreter's maven version in docs and interpreter-list
  sed -i '' 's/:'"${FROM_VERSION}"'/:'"${TO_VERSION}"'/g' conf/interpreter-list
  sed -i '' 's/:'"${FROM_VERSION}"'/:'"${TO_VERSION}"'/g' docs/manual/interpreterinstallation.md 
fi
