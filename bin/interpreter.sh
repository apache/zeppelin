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

bin=$(dirname "${BASH_SOURCE-$0}")
bin=$(cd "${bin}">/dev/null; pwd)


function usage() {
    echo "usage) $0 -p <port> -d <directory to load>"
}

while getopts "hp:d:" o; do
    case ${o} in
        h)
            usage
            exit 0
            ;;
        d)
            INTERPRETER_DIR=${OPTARG}
            ;;
        p)
            PORT=${OPTARG}
            ;;
        esac
done


if [ -z "${PORT}" ] || [ -z "${INTERPRETER_DIR}" ]; then
    usage
    exit 1
fi

. "${bin}/common.sh"

ZEPPELIN_CLASSPATH+=":${ZEPPELIN_CONF_DIR}"

# construct classpath
if [[ -d "${ZEPPELIN_HOME}/zeppelin-interpreter/target/classes" ]]; then
  ZEPPELIN_CLASSPATH+=":${ZEPPELIN_HOME}/zeppelin-interpreter/target/classes"
fi

addJarInDir "${ZEPPELIN_HOME}/zeppelin-interpreter/target/lib"
addJarInDir "${INTERPRETER_DIR}"

HOSTNAME=$(hostname)
ZEPPELIN_SERVER=org.apache.zeppelin.interpreter.remote.RemoteInterpreterServer

INTERPRETER_ID=$(basename "${INTERPRETER_DIR}")
ZEPPELIN_PID="${ZEPPELIN_PID_DIR}/zeppelin-interpreter-${INTERPRETER_ID}-${ZEPPELIN_IDENT_STRING}-${HOSTNAME}.pid"
ZEPPELIN_LOGFILE="${ZEPPELIN_LOG_DIR}/zeppelin-interpreter-${INTERPRETER_ID}-${ZEPPELIN_IDENT_STRING}-${HOSTNAME}.log"
JAVA_INTP_OPTS+=" -Dzeppelin.log.file=${ZEPPELIN_LOGFILE}"

if [[ ! -d "${ZEPPELIN_LOG_DIR}" ]]; then
  echo "Log dir doesn't exist, create ${ZEPPELIN_LOG_DIR}"
  $(mkdir -p "${ZEPPELIN_LOG_DIR}")
fi

# set spark related env variables
if [[ "${INTERPRETER_ID}" == "spark" ]]; then
  if [[ -n "${SPARK_HOME}" ]]; then
    export SPARK_SUBMIT="${SPARK_HOME}/bin/spark-submit"
    SPARK_APP_JAR="$(ls ${ZEPPELIN_HOME}/interpreter/spark/zeppelin-spark*.jar)"
    # This will evantually passes SPARK_APP_JAR to classpath of SparkIMain
    ZEPPELIN_CLASSPATH=${SPARK_APP_JAR}

    export PYTHONPATH="$SPARK_HOME/python/:$PYTHONPATH"
    export PYTHONPATH="$SPARK_HOME/python/lib/py4j-0.8.2.1-src.zip:$PYTHONPATH"    
  else
    # add Hadoop jars into classpath
    if [[ -n "${HADOOP_HOME}" ]]; then
      # Apache
      addEachJarInDir "${HADOOP_HOME}/share"

      # CDH
      addJarInDir "${HADOOP_HOME}"
      addJarInDir "${HADOOP_HOME}/lib"
    fi

    addJarInDir "${INTERPRETER_DIR}/dep"
    PYSPARKPATH="${ZEPPELIN_HOME}/interpreter/spark/pyspark/pyspark.zip:${ZEPPELIN_HOME}/interpreter/spark/pyspark/py4j-0.8.2.1-src.zip"

    if [[ -z "${PYTHONPATH}" ]]; then
      export PYTHONPATH="${PYSPARKPATH}"
    else
      export PYTHONPATH="${PYTHONPATH}:${PYSPARKPATH}"
    fi
    unset PYSPARKPATH

    # autodetect HADOOP_CONF_HOME by heuristic
    if [[ -n "${HADOOP_HOME}" ]] && [[ -z "${HADOOP_CONF_DIR}" ]]; then
      if [[ -d "${HADOOP_HOME}/etc/hadoop" ]]; then
        export HADOOP_CONF_DIR="${HADOOP_HOME}/etc/hadoop"
      elif [[ -d "/etc/hadoop/conf" ]]; then
        export HADOOP_CONF_DIR="/etc/hadoop/conf"
      fi
    fi

    if [[ -n "${HADOOP_CONF_DIR}" ]] && [[ -d "${HADOOP_CONF_DIR}" ]]; then
      ZEPPELIN_CLASSPATH+=":${HADOOP_CONF_DIR}"
    fi

    export SPARK_CLASSPATH+=":${ZEPPELIN_CLASSPATH}"
  fi
fi

CLASSPATH+=":${ZEPPELIN_CLASSPATH}"

if [[ -n "${SPARK_SUBMIT}" ]]; then
    ${SPARK_SUBMIT} --class ${ZEPPELIN_SERVER} --driver-class-path "${CLASSPATH}" --driver-java-options "${JAVA_INTP_OPTS}" ${SPARK_SUBMIT_OPTIONS} ${SPARK_APP_JAR} ${PORT} &
else
    ${ZEPPELIN_RUNNER} ${JAVA_INTP_OPTS} -cp ${CLASSPATH} ${ZEPPELIN_SERVER} ${PORT} &
fi

pid=$!
if [[ -z "${pid}" ]]; then
  return 1;
else
  echo ${pid} > ${ZEPPELIN_PID}
fi


trap 'shutdown_hook;' SIGTERM SIGINT SIGQUIT
function shutdown_hook() {
  local count
  count=0
  while [[ "${count}" -lt 10 ]]; do
    $(kill ${pid} > /dev/null 2> /dev/null)
    if kill -0 ${pid} > /dev/null 2>&1; then
      sleep 3
      let "count+=1"
    else
      rm -f "${ZEPPELIN_PID}"
      break
    fi
  if [[ "${count}" == "5" ]]; then
    $(kill -9 ${pid} > /dev/null 2> /dev/null)
    rm -f "${ZEPPELIN_PID}"
  fi
  done
}

wait
