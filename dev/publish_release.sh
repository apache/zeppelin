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

# The script helps publishing release to mavene.
# You need to specify release version and tag name.
#
# Here are some helpful documents for the release.
# http://www.apache.org/dev/release.html
# http://www.apache.org/dev/release-publishing
# http://www.apache.org/dev/release-signing.html
# http://www.apache.org/dev/publishing-maven-artifacts.html

if [[ -z "${SHASUM}" ]]; then
  SHASUM="/usr/bin/shasum -a 1"
fi

if [[ -z "${WORKING_DIR}" ]]; then
  WORKING_DIR=/tmp/zeppelin-release
fi

if [ $# -ne 2 ]; then
  echo "usage) $0 [Release version] [Tag]"
  echo "   ex. $0 0.6.0 v0.6.0"
  exit 1
fi

for var in GPG_PASSPHRASE ASF_USERID ASF_PASSWORD
do
  if [[ -z "${!var}" ]]; then
    echo "You need ${var} variable set"
    exit 1
  fi
done

export MAVEN_OPTS="-Xmx2g -XX:MaxPermSize=512m"

RELEASE_VERSION="${1}"
GIT_TAG="${2}"

PUBLISH_PROFILES="-Pspark-1.6 -Phadoop-2.4 -Pyarn -Ppyspark -Psparkr -Pr"
PROJECT_OPTIONS="-pl !zeppelin-server,!zeppelin-distribution"
NEXUS_STAGING="https://repository.apache.org/service/local/staging"
NEXUS_PROFILE="153446d1ac37c4"

if [[ -d "${WORKING_DIR}" ]]; then
  echo "Dir ${WORKING_DIR} already exists"
  exit 1
fi

mkdir ${WORKING_DIR}

echo "Cloning the source"
# clone source
git clone https://git-wip-us.apache.org/repos/asf/zeppelin.git ${WORKING_DIR}/zeppelin

if [[ $? -ne 0 ]]; then
  echo "Can not clone source repository"
  exit 1
fi

cd ${WORKING_DIR}/zeppelin
git checkout ${GIT_TAG}
echo "Checked out ${GIT_TAG}"

# remove unnecessary files
rm ${WORKING_DIR}/zeppelin/.gitignore
rm -rf ${WORKING_DIR}/zeppelin/.git

function publish_to_maven() {
  cd ${WORKING_DIR}/zeppelin

  # Force release version
  mvn versions:set -DnewVersion=$RELEASE_VERSION

  # Using Nexus API documented here:
  # https://support.sonatype.com/hc/en-us/articles/213465868-Uploading-to-a-Staging-Repository-via-REST-API
  echo "Creating Nexus staging repository"
  repo_request="<promoteRequest><data><description>Apache Zeppelin $RELEASE_VERSION</description></data></promoteRequest>"
  out=$(curl -X POST -d "$repo_request" -u $ASF_USERID:$ASF_PASSWORD \
    -H "Content-Type:application/xml" -v \
    $NEXUS_STAGING/profiles/$NEXUS_PROFILE/start)
  staged_repo_id=$(echo $out | sed -e "s/.*\(orgapachezeppelin-[0-9]\{4\}\).*/\1/")
  echo "Created Nexus staging repository: $staged_repo_id"

  tmp_repo=$(mktemp -d /tmp/zeppelin-repo-XXXXX)

  echo "mvn clean install -Ppublish-distr \
    -Dmaven.repo.local=$tmp_repo \
    $PUBLISH_PROFILES $PROJECT_OPTIONS"
  mvn clean install -Ppublish-distr -Dmaven.repo.local=$tmp_repo \
    $PUBLISH_PROFILES $PROJECT_OPTIONS
  if [[ $? -ne 0 ]]; then
    echo "Build failed."
    exit 1
  fi

  pushd $tmp_repo/org/apache/zeppelin
  find . -type f | grep -v \.jar$ | grep -v \.pom$ |grep -v \.war$ | xargs rm

  echo "Creating hash and signature files"
  for file in $(find . -type f)
  do
    echo $GPG_PASSPHRASE | gpg --passphrase-fd 0 --output $file.asc \
      --detach-sig --armor $file;
    md5 -q $file > $file.md5
    $SHASUM $file | cut -f1 -d' ' > $file.sha1
  done

  nexus_upload=$NEXUS_STAGING/deployByRepositoryId/$staged_repo_id
  echo "Uplading files to $nexus_upload"
  for file in $(find . -type f)
  do
    # strip leading ./
    file_short=$(echo $file | sed -e "s/\.\///")
    dest_url="$nexus_upload/org/apache/zeppelin/$file_short"
    echo "  Uploading $file_short"
    curl -u $ASF_USERID:$ASF_PASSWORD --upload-file $file_short $dest_url
  done

  echo "Closing nexus staging repository"
  repo_request="<promoteRequest><data><stagedRepositoryId>$staged_repo_id</stagedRepositoryId><description>Apache Zeppelin $RELEASE_VERSION</description></data></promoteRequest>"
  out=$(curl -X POST -d "$repo_request" -u $ASF_USERID:$ASF_PASSWORD \
    -H "Content-Type:application/xml" -v \
    $NEXUS_STAGING/profiles/$NEXUS_PROFILE/finish)
  echo "Closed Nexus staging repository: $staged_repo_id"
  popd
  rm -rf $tmp_repo
}

publish_to_maven

# remove working directory
rm -rf ${WORKING_DIR}
