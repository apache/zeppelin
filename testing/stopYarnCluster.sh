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

if [ $# -ne 1 ]; then
    echo "usage) $0 [hadoop version]"
    echo "   eg) $0 2.6.0"
    exit 1
fi

HADOOP_VERSION="${1}"

FWDIR=$(dirname "${BASH_SOURCE-$0}")
ZEPPELIN_HOME="$(cd "${FWDIR}/.."; pwd)"
export HADOOP_HOME=${ZEPPELIN_HOME}/hadoop-${HADOOP_VERSION}

DEFAULT_LIBEXEC_DIR=${HADOOP_HOME}/libexec
HADOOP_LIBEXEC_DIR=${HADOOP_LIBEXEC_DIR:-$DEFAULT_LIBEXEC_DIR}
. $HADOOP_LIBEXEC_DIR/yarn-config.sh

# stop nodeManager
$HADOOP_HOME/sbin/yarn-daemon.sh --config $YARN_CONF_DIR stop nodemanager

# stop resourceManager
$HADOOP_HOME/sbin/yarn-daemon.sh --config $YARN_CONF_DIR stop resourcemanager
