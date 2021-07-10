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

set -e

VALID_VERSIONS=( 2.10 2.11 2.12 )

usage() {
  echo "Usage: $(basename $0) [-h|--help] <version>
where :
  -h| --help Display this help text
  valid version values : ${VALID_VERSIONS[*]}
" 1>&2
  exit 1
}

if [[ ($# -ne 1) || ( $1 == "--help") ||  $1 == "-h" ]]; then
  usage
fi

TO_VERSION="$1"

check_scala_version() {
  for i in ${VALID_VERSIONS[*]}; do [ $i = "$1" ] && return 0; done
  echo "Invalid Scala version: $1. Valid versions: ${VALID_VERSIONS[*]}" 1>&2
  exit 1
}

check_scala_version "${TO_VERSION}"

BASEDIR=$(dirname $0)/..

CURRENT_SCALA_VERSION_FILE=$BASEDIR/dev/current_scala_version
if [ -s "$CURRENT_SCALA_VERSION_FILE" ]
then
   FROM_VERSION=$(cat $CURRENT_SCALA_VERSION_FILE)
else
   FROM_VERSION="2.10"
fi

if [ "${TO_VERSION}" = "2.11" ]; then
  SCALA_LIB_VERSION="2.11.7"
elif [ "${TO_VERSION}" = "2.10" ]; then
  SCALA_LIB_VERSION="2.10.5"
else
  SCALA_LIB_VERSION="2.12.10"
fi

sed_i() {
  sed -e "$1" "$2" > "$2.tmp" && mv "$2.tmp" "$2"
}

export -f sed_i

find "${BASEDIR}" -name 'pom.xml' -not -path '*target*' -print \
  -exec bash -c "sed_i 's/\(artifactId.*\)_'${FROM_VERSION}'/\1_'${TO_VERSION}'/g' {}" \;

# update <scala.binary.version> in parent POM
# Match any scala binary version to ensure idempotency
sed_i '1,/<scala\.binary\.version>[0-9]*\.[0-9]*</s/<scala\.binary\.version>[0-9]*\.[0-9]*</<scala.binary.version>'${TO_VERSION}'</' \
  "${BASEDIR}/pom.xml"

# update <scala.version> in parent POM
# This is to make variables in leaf pom to be substituted to real value when flattened-pom is created. 
# maven-flatten plugin doesn't take properties defined under profile even if scala-2.11/scala-2.10 is activated via -Pscala-2.11/-Pscala-2.10,
# and use default defined properties to create flatten pom.
sed_i '1,/<scala\.version>[0-9]*\.[0-9]*\.[0-9]*</s/<scala\.version>[0-9]*\.[0-9]*\.[0-9]*</<scala.version>'${SCALA_LIB_VERSION}'</' \
  "${BASEDIR}/pom.xml"

echo $TO_VERSION > $CURRENT_SCALA_VERSION_FILE