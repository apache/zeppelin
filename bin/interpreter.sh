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
    echo "usage) $0 -p <port> -r <intp_port> -d <interpreter dir to load> -l <local interpreter repo dir to load> -g <interpreter group name>"
}

while getopts "hc:p:r:i:d:l:v:g:" o; do
    case ${o} in
        h)
            usage
            exit 0
            ;;
        d)
            INTERPRETER_DIR=${OPTARG}
            ;;
        c)
            CALLBACK_HOST=${OPTARG} # This will be used callback host
            ;;
        p)
            PORT=${OPTARG} # This will be used for callback port
            ;;
        r)
            INTP_PORT=${OPTARG} # This will be used for interpreter process port
            ;;
        i)
            INTP_GROUP_ID=${OPTARG} # This will be used for interpreter group id
            ;;
        l)
            LOCAL_INTERPRETER_REPO=${OPTARG}
            ;;
        v)
            . "${bin}/common.sh"
            getZeppelinVersion
            ;;
        g)
            INTERPRETER_SETTING_NAME=${OPTARG}
            ;;
        esac
done


if [ -z "${PORT}" ] || [ -z "${INTERPRETER_DIR}" ]; then
    usage
    exit 1
fi

. "${bin}/common.sh"

ZEPPELIN_INTERPRETER_API_JAR=$(find "${ZEPPELIN_HOME}/interpreter" -name 'zeppelin-interpreter-api-*.jar')
ZEPPELIN_INTP_CLASSPATH="${CLASSPATH}:${ZEPPELIN_INTERPRETER_API_JAR}"

# construct classpath
if [[ -d "${ZEPPELIN_HOME}/zeppelin-interpreter/target/classes" ]]; then
  ZEPPELIN_INTP_CLASSPATH+=":${ZEPPELIN_HOME}/zeppelin-interpreter/target/classes"
fi

# add test classes for unittest
if [[ -d "${ZEPPELIN_HOME}/zeppelin-zengine/target/test-classes" ]]; then
  ZEPPELIN_INTP_CLASSPATH+=":${ZEPPELIN_HOME}/zeppelin-zengine/target/test-classes"
  addJarInDirForIntp "${ZEPPELIN_HOME}/zeppelin-zengine/target/test-classes"
fi

addJarInDirForIntp "${ZEPPELIN_HOME}/zeppelin-interpreter-api/target"
addJarInDirForIntp "${INTERPRETER_DIR}"

HOSTNAME=$(hostname)
ZEPPELIN_SERVER=org.apache.zeppelin.interpreter.remote.RemoteInterpreterServer

INTERPRETER_ID=$(basename "${INTERPRETER_DIR}")
ZEPPELIN_PID="${ZEPPELIN_PID_DIR}/zeppelin-interpreter-${INTERPRETER_ID}-${ZEPPELIN_IDENT_STRING}-${HOSTNAME}-${PORT}.pid"
ZEPPELIN_LOGFILE="${ZEPPELIN_LOG_DIR}/zeppelin-interpreter-${INTERPRETER_GROUP_ID}-"


ZEPPELIN_LOGFILE+="${ZEPPELIN_IDENT_STRING}-${HOSTNAME}.log"
JAVA_INTP_OPTS+=" -Dzeppelin.log.file=${ZEPPELIN_LOGFILE}"

if [[ ! -d "${ZEPPELIN_LOG_DIR}" ]]; then
  echo "Log dir doesn't exist, create ${ZEPPELIN_LOG_DIR}"
  $(mkdir -p "${ZEPPELIN_LOG_DIR}")
fi

# set spark related env variables
if [[ "${INTERPRETER_ID}" == "spark" ]]; then

  # run kinit
  if [[ -n "${ZEPPELIN_SERVER_KERBEROS_KEYTAB}" ]] && [[ -n "${ZEPPELIN_SERVER_KERBEROS_PRINCIPAL}" ]]; then
    kinit -kt ${ZEPPELIN_SERVER_KERBEROS_KEYTAB} ${ZEPPELIN_SERVER_KERBEROS_PRINCIPAL}
  fi
  if [[ -n "${SPARK_HOME}" ]]; then
    export SPARK_SUBMIT="${SPARK_HOME}/bin/spark-submit"
    SPARK_APP_JAR="$(ls ${ZEPPELIN_HOME}/interpreter/spark/spark-interpreter*.jar)"
    # This will evantually passes SPARK_APP_JAR to classpath of SparkIMain
    ZEPPELIN_INTP_CLASSPATH+=":${SPARK_APP_JAR}"

    pattern="$SPARK_HOME/python/lib/py4j-*-src.zip"
    py4j=($pattern)
    # pick the first match py4j zip - there should only be one
    export PYTHONPATH="$SPARK_HOME/python/:$PYTHONPATH"
    export PYTHONPATH="${py4j[0]}:$PYTHONPATH"
  else
    # add Hadoop jars into classpath
    if [[ -n "${HADOOP_HOME}" ]]; then
      # Apache
      addEachJarInDirRecursiveForIntp "${HADOOP_HOME}/share"

      # CDH
      addJarInDirForIntp "${HADOOP_HOME}"
      addJarInDirForIntp "${HADOOP_HOME}/lib"
    fi

    addJarInDirForIntp "${INTERPRETER_DIR}/dep"

    pattern="${ZEPPELIN_HOME}/interpreter/spark/pyspark/py4j-*-src.zip"
    py4j=($pattern)
    # pick the first match py4j zip - there should only be one
    PYSPARKPATH="${ZEPPELIN_HOME}/interpreter/spark/pyspark/pyspark.zip:${py4j[0]}"

    if [[ -z "${PYTHONPATH}" ]]; then
      export PYTHONPATH="${PYSPARKPATH}"
    else
      export PYTHONPATH="${PYTHONPATH}:${PYSPARKPATH}"
    fi
    unset PYSPARKPATH
    export SPARK_CLASSPATH+=":${ZEPPELIN_INTP_CLASSPATH}"
  fi

  if [[ -n "${HADOOP_CONF_DIR}" ]] && [[ -d "${HADOOP_CONF_DIR}" ]]; then
    ZEPPELIN_INTP_CLASSPATH+=":${HADOOP_CONF_DIR}"
    export HADOOP_CONF_DIR=${HADOOP_CONF_DIR}
  else
    # autodetect HADOOP_CONF_HOME by heuristic
    if [[ -n "${HADOOP_HOME}" ]] && [[ -z "${HADOOP_CONF_DIR}" ]]; then
      if [[ -d "${HADOOP_HOME}/etc/hadoop" ]]; then
        export HADOOP_CONF_DIR="${HADOOP_HOME}/etc/hadoop"
      elif [[ -d "/etc/hadoop/conf" ]]; then
        export HADOOP_CONF_DIR="/etc/hadoop/conf"
      fi
    fi
  fi

elif [[ "${INTERPRETER_ID}" == "hbase" ]]; then
  if [[ -n "${HBASE_CONF_DIR}" ]]; then
    ZEPPELIN_INTP_CLASSPATH+=":${HBASE_CONF_DIR}"
  elif [[ -n "${HBASE_HOME}" ]]; then
    ZEPPELIN_INTP_CLASSPATH+=":${HBASE_HOME}/conf"
  else
    echo "HBASE_HOME and HBASE_CONF_DIR are not set, configuration might not be loaded"
  fi
elif [[ "${INTERPRETER_ID}" == "pig" ]]; then
   # autodetect HADOOP_CONF_HOME by heuristic
  if [[ -n "${HADOOP_HOME}" ]] && [[ -z "${HADOOP_CONF_DIR}" ]]; then
    if [[ -d "${HADOOP_HOME}/etc/hadoop" ]]; then
      export HADOOP_CONF_DIR="${HADOOP_HOME}/etc/hadoop"
    elif [[ -d "/etc/hadoop/conf" ]]; then
      export HADOOP_CONF_DIR="/etc/hadoop/conf"
    fi
  fi

  if [[ -n "${HADOOP_CONF_DIR}" ]] && [[ -d "${HADOOP_CONF_DIR}" ]]; then
    ZEPPELIN_INTP_CLASSPATH+=":${HADOOP_CONF_DIR}"
  fi

  # autodetect TEZ_CONF_DIR
  if [[ -n "${TEZ_CONF_DIR}" ]]; then
    ZEPPELIN_INTP_CLASSPATH+=":${TEZ_CONF_DIR}"
  elif [[ -d "/etc/tez/conf" ]]; then
    ZEPPELIN_INTP_CLASSPATH+=":/etc/tez/conf"
  else
    echo "TEZ_CONF_DIR is not set, configuration might not be loaded"
  fi
elif [[ "${INTERPRETER_ID}" == "flink" ]]; then
  addJarInDirForIntp "${FLINK_HOME}/lib"
  addJarInDirForIntp "${FLINK_HOME}/opt"

  if [[ -n "${HADOOP_CONF_DIR}" ]] && [[ -d "${HADOOP_CONF_DIR}" ]]; then
    ZEPPELIN_INTP_CLASSPATH+=`hadoop classpath`
    export HADOOP_CONF_DIR=${HADOOP_CONF_DIR}
  else
    # autodetect HADOOP_CONF_HOME by heuristic
    if [[ -n "${HADOOP_HOME}" ]] && [[ -z "${HADOOP_CONF_DIR}" ]]; then
      if [[ -d "${HADOOP_HOME}/etc/hadoop" ]]; then
        export HADOOP_CONF_DIR="${HADOOP_HOME}/etc/hadoop"
      elif [[ -d "/etc/hadoop/conf" ]]; then
        export HADOOP_CONF_DIR="/etc/hadoop/conf"
      fi
    fi
  fi

fi

addJarInDirForIntp "${LOCAL_INTERPRETER_REPO}"

if [[ -n "${SPARK_SUBMIT}" ]]; then
    INTERPRETER_RUN_COMMAND+=' '` echo ${SPARK_SUBMIT} --class ${ZEPPELIN_SERVER} --driver-class-path \"${ZEPPELIN_INTP_CLASSPATH_OVERRIDES}:${ZEPPELIN_INTP_CLASSPATH}\" --driver-java-options \"${JAVA_INTP_OPTS}\" ${SPARK_SUBMIT_OPTIONS} ${ZEPPELIN_SPARK_CONF} ${SPARK_APP_JAR} ${CALLBACK_HOST} ${PORT} ${INTP_GROUP_ID} ${INTP_PORT}`
else
    INTERPRETER_RUN_COMMAND+=' '` echo ${ZEPPELIN_RUNNER} ${JAVA_INTP_OPTS} ${ZEPPELIN_INTP_MEM} -cp ${ZEPPELIN_INTP_CLASSPATH_OVERRIDES}:${ZEPPELIN_INTP_CLASSPATH} ${ZEPPELIN_SERVER} ${CALLBACK_HOST} ${PORT} ${INTP_GROUP_ID} ${INTP_PORT}`
fi


echo "Interpreter launch command: $INTERPRETER_RUN_COMMAND"
eval $INTERPRETER_RUN_COMMAND &
pid=$!

if [[ -z "${pid}" ]]; then
  exit 1;
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
      break
    fi
  if [[ "${count}" == "5" ]]; then
    $(kill -9 ${pid} > /dev/null 2> /dev/null)
  fi
  done
}

wait

rm -f "${ZEPPELIN_PID}" > /dev/null 2> /dev/null
