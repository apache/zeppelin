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

#
# This utility creates a local branch PR_<PR_NUM> from specified pull request,
# to help the test and review.
#
# Prerequisites:
#   Add Apache Zeppelin as remote repo, with name "apache" (or something else
#   defined by environment variable APACHE_ZEPPELIN_REMOTE_REPO_NAME)
#
#    git remote add apache git@github.com:apache/zeppelin.git
#

set -o pipefail
set -e
set -x

APACHE_ZEPPELIN_REMOTE_REPO_NAME=${APACHE_ZEPPELIN_REMOTE_REPO_NAME:-"apache"}

function usage {
  echo "Usage: dev/checkout_zeppelin_pr.sh [-f] <PR_NUM>"
  echo "   -f  force overwrite of local branch (default: fail if exists)"
  exit 1
}

if [[ ${#} -eq 0 ]]; then
  usage
fi

FORCE=""
while getopts ":f" arg; do
  case "${arg}" in
    f)
      FORCE="--force"
      ;;
    ?)
      usage
      ;;
  esac
done
shift "$(($OPTIND -1))"

PR_NUM=$1

if [[ $(git rev-parse --abbrev-ref HEAD) == "PR_${PR_NUM}" ]]; then
  git pull ${APACHE_ZEPPELIN_REMOTE_REPO_NAME} pull/${PR_NUM}/head:PR_${PR_NUM} ${FORCE}
else
  git fetch ${APACHE_ZEPPELIN_REMOTE_REPO_NAME} pull/${PR_NUM}/head:PR_${PR_NUM} ${FORCE}
  git checkout PR_${PR_NUM}
fi
