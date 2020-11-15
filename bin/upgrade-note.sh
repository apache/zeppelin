#!/bin/bash
#
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
# Convert note format from 0.9.0 before to 0.9.0 after
#

USAGE="Usage: bin/upgrade-note.sh [-d]"

bin=$(dirname "${BASH_SOURCE-$0}")
bin=$(cd "${bin}">/dev/null; pwd)

. "${bin}/common.sh"

JAVA_OPTS="-Dzeppelin.log.file=logs/upgrade-note.log"
MAIN_CLASS=org.apache.zeppelin.notebook.repo.UpgradeNoteFileTool

# construct classpath
if [[ -d "${ZEPPELIN_HOME}/zeppelin-interpreter/target/classes" ]]; then
  ZEPPELIN_CLASSPATH+=":${ZEPPELIN_HOME}/zeppelin-interpreter/target/classes"
fi

if [[ -d "${ZEPPELIN_HOME}/zeppelin-zengine/target/classes" ]]; then
  ZEPPELIN_CLASSPATH+=":${ZEPPELIN_HOME}/zeppelin-zengine/target/classes"
fi

if [[ -d "${ZEPPELIN_HOME}/zeppelin-server/target/classes" ]]; then
  ZEPPELIN_CLASSPATH+=":${ZEPPELIN_HOME}/zeppelin-server/target/classes"
fi

addJarInDir "${ZEPPELIN_HOME}"
addJarInDir "${ZEPPELIN_HOME}/lib"
addJarInDir "${ZEPPELIN_HOME}/lib/interpreter"
addJarInDir "${ZEPPELIN_HOME}/zeppelin-interpreter/target/lib"
addJarInDir "${ZEPPELIN_HOME}/zeppelin-zengine/target/lib"
addJarInDir "${ZEPPELIN_HOME}/zeppelin-server/target/lib"

ZEPPELIN_CLASSPATH="$CLASSPATH:$ZEPPELIN_CLASSPATH"

## Add hadoop jars when env USE_HADOOP is true
if [[ "${USE_HADOOP}" != "false"  ]]; then
  if [[ -z "${HADOOP_CONF_DIR}" ]]; then
    echo "Please specify HADOOP_CONF_DIR if USE_HADOOP is true"
  else
    ZEPPELIN_CLASSPATH+=":${HADOOP_CONF_DIR}"
    if ! [ -x "$(command -v hadoop)" ]; then
      echo 'hadoop command is not in PATH when HADOOP_CONF_DIR is specified.'
    else
      ZEPPELIN_CLASSPATH+=":`hadoop classpath`"
    fi
  fi
fi

exec $ZEPPELIN_RUNNER $JAVA_OPTS -cp $ZEPPELIN_CLASSPATH_OVERRIDES:${ZEPPELIN_CLASSPATH} $MAIN_CLASS "$@"
