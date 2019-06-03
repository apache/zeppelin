#!/usr/bin/env bash
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

USAGE="-e Usage: build-images.sh\n\t
        [zeppelin_base|interpreter_base|server|interpreter {interpreter_name}]"

bin=$(dirname "${BASH_SOURCE-$0}")
bin=$(cd "${bin}">/dev/null; pwd)

. "${bin}/common.sh"

VERSION=$(getZeppelinVersion)

ZEPPELIN_DIST_DIR=${ZEPPELIN_HOME}/zeppelin-distribution/target/zeppelin-${VERSION}

function copy_log4j_files() {
    # copy all log4j files under ZEPPELIN distribution
    echo "Copying log4j.properties"
    cp -rf ${ZEPPELIN_HOME}/scripts/docker/zeppelin-interpreter/log4j.properties ${ZEPPELIN_DIST_DIR}
    cp -rf ${ZEPPELIN_HOME}/scripts/docker/zeppelin-interpreter/log4j_yarn_cluster.properties ${ZEPPELIN_DIST_DIR}
}

function build_zeppelin_base_image() {
    copy_log4j_files
    echo "Building zeppelin base image"
    docker build --build-arg version=${VERSION} -t ${REPO}/zeppelin-base:${VERSION} -f ${ZEPPELIN_HOME}/scripts/docker/zeppelin-server/Dockerfile_zeppelin_base ${ZEPPELIN_DIST_DIR}
    echo "Pushing zeppelin base image"
    docker push ${REPO}/zeppelin-base:${VERSION}
}

function build_zeppelin_server_image() {
    copy_log4j_files
    echo "Building zeppelin server image"
    docker build --build-arg version=${VERSION} --build-arg repo=${REPO} -t ${REPO}/zeppelin-server:${VERSION} -f ${ZEPPELIN_HOME}/scripts/docker/zeppelin-server/Dockerfile_server ${ZEPPELIN_DIST_DIR}
    echo "Pushing zeppelin server image"
    docker push ${REPO}/zeppelin-server:${VERSION}
}

function build_zeppelin_interpreter_base_image() {
    copy_log4j_files
    echo "Building interpreter base image"
    docker build --build-arg version=${VERSION} --build-arg repo=${REPO} -t ${REPO}/zeppelin-interpreter-base:${VERSION} -f ${ZEPPELIN_HOME}/scripts/docker/zeppelin-interpreter/Dockerfile_interpreter_base ${ZEPPELIN_DIST_DIR}
    echo "Pushing zeppelin interpreter base image"
    docker push ${REPO}/zeppelin-interpreter-base:${VERSION}
}

# build the specific zeppelin-interpreter image
function build_zeppelin_interpreter_image() {
    interpreter_name=$1

    dockerfile="${ZEPPELIN_HOME}/scripts/docker/zeppelin-interpreter/Dockerfile"

    if [[ -f "${dockerfile}_${interpreter_name}" ]]; then
        dockerfile="${dockerfile}_${interpreter_name}"
    fi

    echo "Building interpreter ${interpreter_name} image via docker file: ${dockerfile}"
    docker build -t ${REPO}/zeppelin-interpreter-${interpreter_name}:${VERSION} \
      -f ${dockerfile} \
      --build-arg interpreter_name=${interpreter_name} --build-arg repo=${REPO} --build-arg version=${VERSION} \
      ${ZEPPELIN_HOME}/zeppelin-distribution/target/zeppelin-${VERSION}
    echo "Pushing interpreter ${interpreter_name} image"
    docker push ${REPO}/zeppelin-interpreter-${interpreter_name}:${VERSION}
}

REPO=apache

while getopts ":r:" opt; do
  case ${opt} in
    r)
      REPO=$OPTARG
      ;;
   \?)
     echo $opt
     echo "Invalid Option: -$OPTARG" 1>&2
     exit 1
     ;;
  esac
done

shift $((OPTIND -1))
subcommand=$1;

case "$subcommand" in
  zeppelin-base)
    build_zeppelin_base_image
    ;;
  server)
    build_zeppelin_server_image
    ;;
  interpreter-base)
    build_zeppelin_interpreter_base_image
    ;;
  interpreter)
    build_zeppelin_interpreter_image $2
    ;;
  *)
    echo ${USAGE}
esac
