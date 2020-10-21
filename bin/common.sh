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

if [ -L "${BASH_SOURCE-$0}" ]; then
  FWDIR=$(dirname "$(readlink "${BASH_SOURCE-$0}")")
else
  FWDIR=$(dirname "${BASH_SOURCE-$0}")
fi

if [[ -z "${ZEPPELIN_HOME}" ]]; then
  # Make ZEPPELIN_HOME look cleaner in logs by getting rid of the
  # extra ../
  ZEPPELIN_HOME="$(cd "${FWDIR}/.." || exit; pwd)"
  export ZEPPELIN_HOME
fi

if [[ -z "${ZEPPELIN_CONF_DIR}" ]]; then
  export ZEPPELIN_CONF_DIR="${ZEPPELIN_HOME}/conf"
fi

if [[ -z "${ZEPPELIN_LOG_DIR}" ]]; then
  export ZEPPELIN_LOG_DIR="${ZEPPELIN_HOME}/logs"
fi

if [[ -z "$ZEPPELIN_PID_DIR" ]]; then
  export ZEPPELIN_PID_DIR="${ZEPPELIN_HOME}/run"
fi

if [[ -z "${ZEPPELIN_WAR}" ]]; then
  if [[ -d "${ZEPPELIN_HOME}/zeppelin-web/dist" ]]; then
    export ZEPPELIN_WAR="${ZEPPELIN_HOME}/zeppelin-web/dist"
  else
    ZEPPELIN_WAR=$(find -L "${ZEPPELIN_HOME}" -name "zeppelin-web-[0-9]*.war")
    export ZEPPELIN_WAR
  fi
fi

if [[ -z "${ZEPPELIN_ANGULAR_WAR}" ]]; then
  if [[ -d "${ZEPPELIN_HOME}/zeppelin-web/dist" ]]; then
    export ZEPPELIN_ANGULAR_WAR="${ZEPPELIN_HOME}/zeppelin-web-angular/dist/zeppelin"
  else
    ZEPPELIN_ANGULAR_WAR=$(find -L "${ZEPPELIN_HOME}" -name "zeppelin-web-angular*.war")
    export ZEPPELIN_ANGULAR_WAR
  fi
fi

if [[ -f "${ZEPPELIN_CONF_DIR}/zeppelin-env.sh" ]]; then
  . "${ZEPPELIN_CONF_DIR}/zeppelin-env.sh"
fi

ZEPPELIN_CLASSPATH+=":${ZEPPELIN_CONF_DIR}"

function check_java_version() {
    java_ver_output=$("${JAVA:-java}" -version 2>&1)
    jvmver=$(echo "$java_ver_output" | grep '[openjdk|java] version' | awk -F'"' 'NR==1 {print $2}' | cut -d\- -f1)
    JVM_VERSION=$(echo "$jvmver"|sed -e 's|^\([0-9][0-9]*\)\..*$|\1|')
    if [ "$JVM_VERSION" = "1" ]; then
        JVM_VERSION=$(echo "$jvmver"|sed -e 's|^1\.\([0-9][0-9]*\)\..*$|\1|')
    fi

    if [ "$JVM_VERSION" -lt 8 ] || { [ "$JVM_VERSION" -eq 8 ] && [ "${jvmver#*_}" -lt 151 ]; } ; then
        echo "Apache Zeppelin requires either Java 8 update 151 or newer"
        exit 1;
    fi
}

function addEachJarInDir(){
  if [[ -d "${1}" ]]; then
    for jar in "${1}"/*.jar ; do
      ZEPPELIN_CLASSPATH="$jar:$ZEPPELIN_CLASSPATH"
    done
  fi
}

function addEachJarInDirRecursive(){
  if [[ -d "${1}" ]]; then
    for jar in "${1}"/**/*.jar ; do
      ZEPPELIN_CLASSPATH="$jar:$ZEPPELIN_CLASSPATH"
    done
  fi
}

function addEachJarInDirRecursiveForIntp(){
  if [[ -d "${1}" ]]; then
    for jar in "${1}"/*.jar; do
      ZEPPELIN_INTP_CLASSPATH="$jar:${ZEPPELIN_INTP_CLASSPATH}"
    done
  fi
}

function addJarInDir(){
  if [[ -d "${1}" ]]; then
    ZEPPELIN_CLASSPATH="${1}/*:${ZEPPELIN_CLASSPATH}"
  fi
}

function addJarInDirForIntp() {
  if [[ -d "${1}" ]]; then
    ZEPPELIN_INTP_CLASSPATH="${1}/*:${ZEPPELIN_INTP_CLASSPATH}"
  fi
}

ZEPPELIN_COMMANDLINE_MAIN=org.apache.zeppelin.utils.CommandLineUtils

function getZeppelinVersion(){
    if [[ -d "${ZEPPELIN_HOME}/zeppelin-server/target/classes" ]]; then
      ZEPPELIN_CLASSPATH+=":${ZEPPELIN_HOME}/zeppelin-server/target/classes"
    fi
    addJarInDir "${ZEPPELIN_HOME}/zeppelin-server/target/lib"
    CLASSPATH+=":${ZEPPELIN_CLASSPATH}"
    $ZEPPELIN_RUNNER -cp "${CLASSPATH}" "${ZEPPELIN_COMMANDLINE_MAIN}" -v
    exit 0
}

# Text encoding for
# read/write job into files,
# receiving/displaying query/result.
if [[ -z "${ZEPPELIN_ENCODING}" ]]; then
  export ZEPPELIN_ENCODING="UTF-8"
fi

if [[ -z "${ZEPPELIN_MEM}" ]]; then
  export ZEPPELIN_MEM="-Xms1024m -Xmx1024m"
fi

if [[ ( -z "${ZEPPELIN_INTP_MEM}" ) && ( "${ZEPPELIN_INTERPRETER_LAUNCHER}" != "yarn" ) ]]; then
  export ZEPPELIN_INTP_MEM="-Xms1024m -Xmx2048m"
fi

JAVA_OPTS+=" ${ZEPPELIN_JAVA_OPTS} -Dfile.encoding=${ZEPPELIN_ENCODING} ${ZEPPELIN_MEM}"
if [[ -n "${ZEPPELIN_IN_DOCKER}" ]]; then
    JAVA_OPTS+=" -Dlog4j.configuration=file://${ZEPPELIN_CONF_DIR}/log4j_docker.properties"
else
    JAVA_OPTS+=" -Dlog4j.configuration=file://${ZEPPELIN_CONF_DIR}/log4j.properties"
fi
export JAVA_OPTS

JAVA_INTP_OPTS="${ZEPPELIN_INTP_JAVA_OPTS} -Dfile.encoding=${ZEPPELIN_ENCODING}"
if [[ -n "${ZEPPELIN_IN_DOCKER}" ]]; then
    JAVA_INTP_OPTS+=" -Dlog4j.configuration=file://${ZEPPELIN_CONF_DIR}/log4j_docker.properties -Dlog4j.configurationFile=file://${ZEPPELIN_CONF_DIR}/log4j2_docker.properties"
elif [[ -z "${ZEPPELIN_SPARK_YARN_CLUSTER}" ]]; then
    JAVA_INTP_OPTS+=" -Dlog4j.configuration=file://${ZEPPELIN_CONF_DIR}/log4j.properties -Dlog4j.configurationFile=file://${ZEPPELIN_CONF_DIR}/log4j2.properties"
else
    JAVA_INTP_OPTS+=" -Dlog4j.configuration=log4j_yarn_cluster.properties"
fi
export JAVA_INTP_OPTS


if [[ -n "${JAVA_HOME}" ]]; then
  ZEPPELIN_RUNNER="${JAVA_HOME}/bin/java"
else
  ZEPPELIN_RUNNER=java
fi
export ZEPPELIN_RUNNER

if [[ -z "$ZEPPELIN_IDENT_STRING" ]]; then
  export ZEPPELIN_IDENT_STRING="${USER}"
fi

if [[ -z "$ZEPPELIN_INTERPRETER_REMOTE_RUNNER" ]]; then
  export ZEPPELIN_INTERPRETER_REMOTE_RUNNER="bin/interpreter.sh"
fi
