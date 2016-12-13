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

# The script helps publishing release to maven.
# You need to specify release version and branch|tag name.
#
# Here's some helpful documents for the release.
# http://www.apache.org/dev/publishing-maven-artifacts.html

BASEDIR="$(dirname "$0")"
. "${BASEDIR}/common_release.sh"

if [[ $# -ne 2 ]]; then
  usage
fi

for var in GPG_PASSPHRASE ASF_USERID ASF_PASSWORD DOCKER_USERNAME DOCKER_PASSWORD DOCKER_EMAIL; do
  if [[ -z "${!var}" ]]; then
    echo "You need ${var} variable set"
    exit 1
  fi
done

export MAVEN_OPTS="-Xmx2g -XX:MaxPermSize=512m"
RED='\033[0;31m'
NC='\033[0m' # No Color

RELEASE_VERSION="$1"
GIT_TAG="$2"

PUBLISH_PROFILES="-Ppublish-distr -Pspark-2.0 -Phadoop-2.4 -Pyarn -Ppyspark -Psparkr -Pr"
PROJECT_OPTIONS="-pl !zeppelin-distribution"
NEXUS_STAGING="https://repository.apache.org/service/local/staging"
NEXUS_PROFILE="153446d1ac37c4"

function cleanup() {
  echo "Remove working directory and maven local repository"
  rm -rf ${WORKING_DIR}
  rm -rf ${tmp_repo}
}

function curl_error() {
  ret=${1}
  if [[ $ret -ne 0 ]]; then
    echo "curl response code is: ($ret)"
    echo "See https://curl.haxx.se/libcurl/c/libcurl-errors.html to know the detailed cause of error."
    echo -e "${RED}Failed to publish maven artifact to staging repository."
    echo -e "IMPORTANT: You will have to re-run publish_release.sh to complete maven artifact publish.${NC}"
    cleanup
    exit 1
  fi
}

function publish_to_dockerhub() {
  # publish images
  docker login --username="${DOCKER_USERNAME}" --password="${DOCKER_PASSWORD}" --email="${DOCKER_EMAIL}"
  docker push ${DOCKER_USERNAME}/zeppelin-base:latest
  docker push ${DOCKER_USERNAME}/zeppelin-release:"${RELEASE_VERSION}"
  
}

function publish_to_maven() {
  cd "${WORKING_DIR}/zeppelin"

  # Force release version
  mvn versions:set -DnewVersion="${RELEASE_VERSION}"

  # Using Nexus API documented here:
  # https://support.sonatype.com/hc/en-us/articles/213465868-Uploading-to-a-Staging-Repository-via-REST-API
  echo "Creating Nexus staging repository"
  repo_request="<promoteRequest><data><description>Apache Zeppelin ${RELEASE_VERSION}</description></data></promoteRequest>"
  out="$(curl -X POST -d "${repo_request}" -u "${ASF_USERID}:${ASF_PASSWORD}" \
    -H 'Content-Type:application/xml' -v \
    "${NEXUS_STAGING}/profiles/${NEXUS_PROFILE}/start")"
  create_ret=$?
  curl_error $create_ret
  staged_repo_id="$(echo ${out} | sed -e 's/.*\(orgapachezeppelin-[0-9]\{4\}\).*/\1/')"
  if [[ -z "${staged_repo_id}" ]]; then
    echo "Fail to create staging repository"
    exit 1
  fi

  echo "Created Nexus staging repository: ${staged_repo_id}"

  tmp_repo="$(mktemp -d /tmp/zeppelin-repo-XXXXX)"

  # build with scala-2.10
  echo "mvn clean install -DskipTests \
    -Dmaven.repo.local=${tmp_repo} -Pscala-2.10 \
    ${PUBLISH_PROFILES} ${PROJECT_OPTIONS}"
  mvn clean install -DskipTests -Dmaven.repo.local="${tmp_repo}" -Pscala-2.10 \
    ${PUBLISH_PROFILES} ${PROJECT_OPTIONS}
  if [[ $? -ne 0 ]]; then
    echo "Build with scala 2.10 failed."
    exit 1
  fi

  # build with scala-2.11
  "${BASEDIR}/change_scala_version.sh" 2.11

  echo "mvn clean install -DskipTests \
    -Dmaven.repo.local=${tmp_repo} -Pscala-2.11 \
    ${PUBLISH_PROFILES} ${PROJECT_OPTIONS}"
  mvn clean install -DskipTests -Dmaven.repo.local="${tmp_repo}" -Pscala-2.11 \
    ${PUBLISH_PROFILES} ${PROJECT_OPTIONS}
  if [[ $? -ne 0 ]]; then
    echo "Build with scala 2.11 failed."
    exit 1
  fi

  pushd "${tmp_repo}/org/apache/zeppelin"
  find . -type f | grep -v '\.jar$' | grep -v '\.pom$' |grep -v '\.war$' | xargs rm

  echo "Creating hash and signature files"
  for file in $(find . -type f); do
    echo "${GPG_PASSPHRASE}" | gpg --passphrase-fd 0 --output "${file}.asc" \
      --detach-sig --armor "${file}"
    md5 -q "${file}" > "${file}.md5"
    ${SHASUM} -a 1 "${file}" | cut -f1 -d' ' > "${file}.sha1"
  done

  nexus_upload="${NEXUS_STAGING}/deployByRepositoryId/${staged_repo_id}"
  echo "Uplading files to ${nexus_upload}"
  for file in $(find . -type f); do
    # strip leading ./
    file_short="$(echo "${file}" | sed -e 's/\.\///')"
    dest_url="${nexus_upload}/org/apache/zeppelin/$file_short"
    echo "  Uploading ${file_short}"
    curl -u "${ASF_USERID}:${ASF_PASSWORD}" --upload-file "${file_short}" "${dest_url}"
    upload_ret=$?
    curl_error $upload_ret
  done

  echo "Closing nexus staging repository"
  repo_request="<promoteRequest><data><stagedRepositoryId>${staged_repo_id}</stagedRepositoryId><description>Apache Zeppelin ${RELEASE_VERSION}</description></data></promoteRequest>"
  out="$(curl -X POST -d "${repo_request}" -u "${ASF_USERID}:${ASF_PASSWORD}" \
    -H 'Content-Type:application/xml' -v \
    "${NEXUS_STAGING}/profiles/${NEXUS_PROFILE}/finish")"
  close_ret=$?
  curl_error $close_ret
  echo "Closed Nexus staging repository: ${staged_repo_id}"
  popd
  echo "Complete publishing maven artifacts to apache staging repository"
  echo "Once release candidate pass the vote, do not forget to hit the release button in https://repository.apache.org"
}

git_clone
publish_to_dockerhub
publish_to_maven
cleanup
