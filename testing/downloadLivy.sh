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

if [[ "$#" -ne 1 && "$#" -ne 2 ]]; then
    echo "usage) $0 <livy version> [scala version]"
    echo "   eg) $0 0.7.1-incubating"
    echo "       $0 0.8.0-incubating 2.11"
    exit 0
fi

# See simple version normalization:
# http://stackoverflow.com/questions/16989598/bash-comparing-version-numbers
function version { echo "$@" | awk -F. '{ printf("%03d%03d%03d\n", $1,$2,$3); }'; }
LIVY_VERSION="${1}"
SCALA_VERSION_SUFFIX=""
if [ $(version $LIVY_VERSION) -ge $(version "0.8.0") ]; then
    SCALA_VERSION_SUFFIX="_${2}"
fi

set -xe

MAX_DOWNLOAD_TIME_SEC=590
FWDIR="$(dirname "${BASH_SOURCE-$0}")"
ZEPPELIN_HOME="$(cd "${FWDIR}/.."; pwd)"

#######################################
# Downloads file from the givrn URL.
# Ties 3 times with 1s delay, 20s read and 15s connection timeouts.
# Globals:
#   None
# Arguments:
#   url - source URL
#   file - output filename
# Returns:
#   None
#######################################
download_with_retry() {
    local url="$1"
    local file="${2:-$(basename $url)}"
    wget --retry-connrefused --waitretry=1 --read-timeout=20 --timeout=15 -t 3 --output-document "${file}" "${url}"
    if [[ "$?" -ne 0 ]]; then
        echo "3 download attempts for ${url} failed"
    fi
}

LIVY_CACHE=".livy-dist"
LIVY_ARCHIVE="apache-livy-${LIVY_VERSION}${SCALA_VERSION_SUFFIX}-bin"
export LIVY_HOME="${ZEPPELIN_HOME}/livy-server-$LIVY_VERSION"
echo "LIVY_HOME is ${LIVY_HOME}"

if [[ ! -d "${LIVY_HOME}" ]]; then
    mkdir -p "${LIVY_CACHE}"
    cd "${LIVY_CACHE}"
    if [[ ! -f "${LIVY_ARCHIVE}.tar.gz" ]]; then
        pwd
        ls -la .
        echo "${LIVY_CACHE} does not have ${LIVY_ARCHIVE} downloading ..."

        # download livy from archive if not cached
        echo "${LIVY_VERSION} being downloaded from archives"
        STARTTIME=`date +%s`
        download_with_retry "https://www.apache.org/dyn/closer.lua/incubator/livy/${LIVY_VERSION}/${LIVY_ARCHIVE}.zip?action=download" "${LIVY_ARCHIVE}.zip"
        ENDTIME=`date +%s`
        DOWNLOADTIME="$((ENDTIME-STARTTIME))"
    fi

    # extract archive in un-cached root, clean-up on failure
    cp "${LIVY_ARCHIVE}.zip" ..
    cd ..
    if ! unzip "${LIVY_ARCHIVE}.zip" ; then
        echo "Unable to extract ${LIVY_ARCHIVE}.zip" >&2
        rm -rf "${LIVY_ARCHIVE}"
        rm -f "${LIVY_ARCHIVE}.zip"
    fi
fi

set +xe
