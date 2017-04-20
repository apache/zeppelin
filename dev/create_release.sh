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
# You need to specify release version and branch|tag name.
#
# Here are some helpful documents for the release.
# http://www.apache.org/dev/release.html
# http://www.apache.org/dev/release-publishing
# http://www.apache.org/dev/release-signing.html

BASEDIR="$(dirname "$0")"
. "${BASEDIR}/common_release.sh"
echo "${BASEDIR}/common_release.sh"

if [[ $# -ne 2 ]]; then
  usage
fi

for var in GPG_PASSPHRASE; do
  if [[ -z "${!var}" ]]; then
    echo "You need ${var} variable set"
    exit 1
  fi
done

RELEASE_VERSION="$1"
GIT_TAG="$2"
SCALA_VERSION="2.11"

function make_source_package() {
  # create source package
  cd ${WORKING_DIR}
  cp -r "zeppelin" "zeppelin-${RELEASE_VERSION}"
  ${TAR} cvzf "zeppelin-${RELEASE_VERSION}.tgz" "zeppelin-${RELEASE_VERSION}"

  echo "Signing the source package"
  cd "${WORKING_DIR}"
  echo "${GPG_PASSPHRASE}" | gpg --passphrase-fd 0 --armor \
    --output "zeppelin-${RELEASE_VERSION}.tgz.asc" \
    --detach-sig "${WORKING_DIR}/zeppelin-${RELEASE_VERSION}.tgz"
  echo "${GPG_PASSPHRASE}" | gpg --passphrase-fd 0 \
    --print-md MD5 "zeppelin-${RELEASE_VERSION}.tgz" > \
    "${WORKING_DIR}/zeppelin-${RELEASE_VERSION}.tgz.md5"
  echo "${GPG_PASSPHRASE}" | gpg --passphrase-fd 0 \
    --print-md SHA512 "zeppelin-${RELEASE_VERSION}.tgz" > \
    "${WORKING_DIR}/zeppelin-${RELEASE_VERSION}.tgz.sha512"
}

function make_binary_release() {
  BIN_RELEASE_NAME="$1"
  BUILD_FLAGS="$2"

  cp -r "${WORKING_DIR}/zeppelin" "${WORKING_DIR}/zeppelin-${RELEASE_VERSION}-bin-${BIN_RELEASE_NAME}"
  cd "${WORKING_DIR}/zeppelin-${RELEASE_VERSION}-bin-${BIN_RELEASE_NAME}"
  ./dev/change_scala_version.sh "${SCALA_VERSION}"
  echo "mvn clean package -Pbuild-distr -DskipTests ${BUILD_FLAGS}"
  mvn clean package -Pbuild-distr -DskipTests ${BUILD_FLAGS}
  if [[ $? -ne 0 ]]; then
    echo "Build failed. ${BUILD_FLAGS}"
    exit 1
  fi

  # re-create package with proper dir name with binary license
  cd zeppelin-distribution/target/zeppelin-*
  mv zeppelin-* "zeppelin-${RELEASE_VERSION}-bin-${BIN_RELEASE_NAME}"
  cat ../../src/bin_license/LICENSE >> "zeppelin-${RELEASE_VERSION}-bin-${BIN_RELEASE_NAME}/LICENSE"
  cat ../../src/bin_license/NOTICE >> "zeppelin-${RELEASE_VERSION}-bin-${BIN_RELEASE_NAME}/NOTICE"
  cp ../../src/bin_license/licenses/* "zeppelin-${RELEASE_VERSION}-bin-${BIN_RELEASE_NAME}/licenses/"
  ${TAR} cvzf "zeppelin-${RELEASE_VERSION}-bin-${BIN_RELEASE_NAME}.tgz" "zeppelin-${RELEASE_VERSION}-bin-${BIN_RELEASE_NAME}"

  # sign bin package
  echo "${GPG_PASSPHRASE}" | gpg --passphrase-fd 0 --armor \
    --output "zeppelin-${RELEASE_VERSION}-bin-${BIN_RELEASE_NAME}.tgz.asc" \
    --detach-sig "zeppelin-${RELEASE_VERSION}-bin-${BIN_RELEASE_NAME}.tgz"
  echo "${GPG_PASSPHRASE}" | gpg --passphrase-fd 0 --print-md MD5 \
    "zeppelin-${RELEASE_VERSION}-bin-${BIN_RELEASE_NAME}.tgz" > \
    "zeppelin-${RELEASE_VERSION}-bin-${BIN_RELEASE_NAME}.tgz.md5"
  ${SHASUM} -a 512 "zeppelin-${RELEASE_VERSION}-bin-${BIN_RELEASE_NAME}.tgz" > \
    "zeppelin-${RELEASE_VERSION}-bin-${BIN_RELEASE_NAME}.tgz.sha512"

  mv "zeppelin-${RELEASE_VERSION}-bin-${BIN_RELEASE_NAME}.tgz" "${WORKING_DIR}/"
  mv "zeppelin-${RELEASE_VERSION}-bin-${BIN_RELEASE_NAME}.tgz.asc" "${WORKING_DIR}/"
  mv "zeppelin-${RELEASE_VERSION}-bin-${BIN_RELEASE_NAME}.tgz.md5" "${WORKING_DIR}/"
  mv "zeppelin-${RELEASE_VERSION}-bin-${BIN_RELEASE_NAME}.tgz.sha512" "${WORKING_DIR}/"

  # clean up build dir
  rm -rf "${WORKING_DIR}/zeppelin-${RELEASE_VERSION}-bin-${BIN_RELEASE_NAME}"
}

git_clone
make_source_package
make_binary_release all "-Pspark-2.1 -Phadoop-2.6 -Pscala-${SCALA_VERSION}"
make_binary_release netinst "-Pspark-2.1 -Phadoop-2.6 -Pscala-${SCALA_VERSION} -pl zeppelin-interpreter,zeppelin-zengine,:zeppelin-display_${SCALA_VERSION},:zeppelin-spark-dependencies_${SCALA_VERSION},:zeppelin-spark_${SCALA_VERSION},zeppelin-web,zeppelin-server,zeppelin-distribution -am"

# remove non release files and dirs
rm -rf "${WORKING_DIR}/zeppelin"
rm -rf "${WORKING_DIR}/zeppelin-${RELEASE_VERSION}"
echo "Release files are created under ${WORKING_DIR}"
