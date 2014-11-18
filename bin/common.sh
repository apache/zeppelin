#!/bin/bash
#
#/**
# * Copyright 2007 The Apache Software Foundation
# *
# * Licensed to the Apache Software Foundation (ASF) under one
# * or more contributor license agreements.  See the NOTICE file
# * distributed with this work for additional information
# * regarding copyright ownership.  The ASF licenses this file
# * to you under the Apache License, Version 2.0 (the
# * "License"); you may not use this file except in compliance
# * with the License.  You may obtain a copy of the License at
# *
# *     http://www.apache.org/licenses/LICENSE-2.0
# *
# * Unless required by applicable law or agreed to in writing, software
# * distributed under the License is distributed on an "AS IS" BASIS,
# * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# * See the License for the specific language governing permissions and
# * limitations under the License.
# */

FWDIR="$(cd `dirname $0`; pwd)"

if [ "x$ZEPPELIN_HOME" == "x" ] ; then
    export ZEPPELIN_HOME=$FWDIR/..
fi

if [ "x$ZEPPELIN_CONF_DIR" == "x" ] ; then
    export ZEPPELIN_CONF_DIR="$ZEPPELIN_HOME/conf"
fi

if [ "x$ZEPPELIN_LOG_DIR" == "x" ]; then
    export ZEPPELIN_LOG_DIR="$ZEPPELIN_HOME/logs"
fi

if [ "x$ZEPPELIN_NOTEBOOK_DIR" == "x" ]; then
    export ZEPPELIN_NOTEBOOK_DIR="$ZEPPELIN_HOME/notebook"
fi

if [ "x$ZEPPELIN_PID_DIR" == "x" ]; then
    export ZEPPELIN_PID_DIR="$ZEPPELIN_HOME/run"
fi

if [ "x$ZEPPELIN_WAR" == "x" ]; then
    if [ -d "${ZEPPELIN_HOME}/zeppelin-web/src/main/webapp" ]; then
	    export ZEPPELIN_WAR="${ZEPPELIN_HOME}/zeppelin-web/src/main/webapp"
    else
        export ZEPPELIN_WAR=`find ${ZEPPELIN_HOME} -name "zeppelin-web*.war"`
    fi
fi

if [ "x$ZEPPELIN_API_WAR" == "x" ]; then
    if [ -d "${ZEPPELIN_HOME}/zeppelin-docs/src/main/swagger" ]; then
	    export ZEPPELIN_API_WAR="${ZEPPELIN_HOME}/zeppelin-docs/src/main/swagger"
    else
        export ZEPPELIN_API_WAR=`find ${ZEPPELIN_HOME}/ -name "zeppelin-api-ui*.war"`
    fi
fi

if [ "x$ZEPPELIN_INTERPRETER_DIR" == "x" ]; then
    export ZEPPELIN_INTERPRETER_DIR="$ZEPPELIN_HOME/interpreter"
fi


if [ -f "${ZEPPELIN_CONF_DIR}/zeppelin-env.sh" ]; then
    . "${ZEPPELIN_CONF_DIR}/zeppelin-env.sh"
fi

ZEPPELIN_CLASSPATH+=":${ZEPPELIN_CONF_DIR}"

function addJarInDir(){
    if [ -d "${1}" ]; then
	for jar in `find ${1} -maxdepth 1 -name '*jar'`; do
	    ZEPPELIN_CLASSPATH=$jar:$ZEPPELIN_CLASSPATH
	done
    fi
}

addJarInDir ${ZEPPELIN_HOME}

addJarInDir ${ZEPPELIN_HOME}/zeppelin-zengine/target/lib
addJarInDir ${ZEPPELIN_HOME}/zeppelin-server/target/lib
addJarInDir ${ZEPPELIN_HOME}/zeppelin-web/target/lib


if [ -d "${ZEPPELIN_HOME}/zeppelin-zengine/target/classes" ]; then
    ZEPPELIN_CLASSPATH+=":${ZEPPELIN_HOME}/zeppelin-zengine/target/classes"
fi

if [ -d "${ZEPPELIN_HOME}/zeppelin-server/target/classes" ]; then
    ZEPPELIN_CLASSPATH+=":${ZEPPELIN_HOME}/zeppelin-server/target/classes"
fi


if [ "x$SPARK_HOME" != "x" ] && [ -d "${SPARK_HOME}" ]; then
    addJarInDir "${SPARK_HOME}"
fi

if [ "x$HADOOP_HOME" != "x" ] && [ -d "${HADOOP_HOME}" ]; then
    addJarInDir "${HADOOP_HOME}"
fi

export ZEPPELIN_CLASSPATH
export SPARK_CLASSPATH+=${ZEPPELIN_CLASSPATH}
export CLASSPATH+=${ZEPPELIN_CLASSPATH}

# Text encoding for 
# read/write job into files,
# receiving/displaying query/result.
if [ "x$ZEPPELIN_ENCODING" == "x" ]; then
  export ZEPPELIN_ENCODING="UTF-8"
fi

if [ "x$ZEPPELIN_MEM" == "x" ]; then
  export ZEPPELIN_MEM="-Xmx1024m -XX:MaxPermSize=512m"
fi


JAVA_OPTS+="$ZEPPELIN_JAVA_OPTS -Dfile.encoding=${ZEPPELIN_ENCODING} ${ZEPPELIN_MEM}"
export JAVA_OPTS

if [ -n "$JAVA_HOME" ]; then
    ZEPPELIN_RUNNER="${JAVA_HOME}/bin/java"
else
    ZEPPELIN_RUNNER=java
fi

export RUNNER


if [ "x$ZEPPELIN_IDENT_STRING" == "x" ]; then
  export ZEPPELIN_IDENT_STRING="$USER"
fi

if [ "x$DEBUG" == "x" ] ; then
    export DEBUG=0
fi
