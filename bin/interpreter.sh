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

while getopts "hc:p:r:i:d:l:v:u:g:" o; do
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
        u)
            ZEPPELIN_IMPERSONATE_USER="${OPTARG}"
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

ZEPPELIN_INTP_CLASSPATH="${CLASSPATH}"

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
addJarInDirForIntp "${ZEPPELIN_HOME}/lib/interpreter"
addJarInDirForIntp "${INTERPRETER_DIR}"

HOSTNAME=$(hostname)
ZEPPELIN_SERVER=org.apache.zeppelin.interpreter.remote.RemoteInterpreterServer

INTERPRETER_ID=$(basename "${INTERPRETER_DIR}")
ZEPPELIN_PID="${ZEPPELIN_PID_DIR}/zeppelin-interpreter-${INTERPRETER_ID}-${ZEPPELIN_IDENT_STRING}-${HOSTNAME}-${PORT}.pid"
ZEPPELIN_LOGFILE="${ZEPPELIN_LOG_DIR}/zeppelin-interpreter-${INTERPRETER_GROUP_ID}-"

if [[ -z "$ZEPPELIN_IMPERSONATE_CMD" ]]; then
    if [[ "${INTERPRETER_ID}" != "spark" || "$ZEPPELIN_IMPERSONATE_SPARK_PROXY_USER" == "false" ]]; then
        ZEPPELIN_IMPERSONATE_RUN_CMD=`echo "ssh ${ZEPPELIN_IMPERSONATE_USER}@localhost" `
    fi
else
    ZEPPELIN_IMPERSONATE_RUN_CMD=$(eval "echo ${ZEPPELIN_IMPERSONATE_CMD} ")
fi


if [[ ! -z "$ZEPPELIN_IMPERSONATE_USER" ]]; then
    ZEPPELIN_LOGFILE+="${ZEPPELIN_IMPERSONATE_USER}-"
fi
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
fi

if [[ "${ZEPPELIN_RUN_MODE}" == "yarn" ]]; then
  export SUBMARINE_BIN="${JAVA_HOME}/bin/java -cp ${HADOOP_CONF_DIR}:${HADOOP_SUBMARINE_JAR} org.apache.hadoop.yarn.submarine.client.cli.Cli "
  export YARN_RUNNER="${SUBMARINE_BIN} job run --name ${YARN_APP_NAME} "
  YARN_RUNNER="${YARN_RUNNER} --env DOCKER_JAVA_HOME=${DOCKER_JAVA_HOME} "
  YARN_RUNNER="${YARN_RUNNER} --env DOCKER_HADOOP_HDFS_HOME=${DOCKER_HADOOP_HOME} "
  YARN_RUNNER="${YARN_RUNNER} --env DOCKER_HADOOP_CONF_DIR=${DOCKER_HADOOP_CONF_DIR} "
  YARN_RUNNER="${YARN_RUNNER} --env YARN_CONTAINER_RUNTIME_DOCKER_CONTAINER_NETWORK=bridge "
  YARN_RUNNER="${YARN_RUNNER} --env TZ=\"Etc/UTC\" "
  YARN_RUNNER="${YARN_RUNNER} --env HADOOP_LOG_DIR=/tmp "
  YARN_RUNNER="${YARN_RUNNER} ${ZEPPELIN_CONF_DIR_ENV} "
  YARN_RUNNER="${YARN_RUNNER} ${YARN_LOCALIZATION_ENV} "
  YARN_RUNNER="${YARN_RUNNER} --docker_image ${ZEPPELIN_YARN_CONTAINER_IMAGE} "
  YARN_RUNNER="${YARN_RUNNER} --input_path hdfs:/// "
  YARN_RUNNER="${YARN_RUNNER} --worker_resources ${ZEPPELIN_YARN_CONTAINER_RESOURCE} "
  YARN_RUNNER="${YARN_RUNNER} --num_workers 1 "
  YARN_RUNNER="${YARN_RUNNER} --keytab ${SUBMARINE_HADOOP_KEYTAB} "
  YARN_RUNNER="${YARN_RUNNER} --principal ${SUBMARINE_HADOOP_PRINCIPAL} "
  YARN_RUNNER="${YARN_RUNNER} --distribute_keytab "

  export YARN_DOCKER_JAVA_INTP_OPTS="java -Dfile.encoding=UTF-8 -Dlog4j.configuration=file:///zeppelin/conf/log4j.properties "
  YARN_DOCKER_JAVA_INTP_OPTS="${YARN_DOCKER_JAVA_INTP_OPTS} -Dzeppelin.log.file=/tmp/zeppelin-interpreter-on-yarn.log"

  export YARN_INTP_CLASSPATH=":/zeppelin/interpreter/${INTERPRETER_SETTING_NAME}/*"
  YARN_INTP_CLASSPATH="${YARN_INTP_CLASSPATH}:/zeppelin/lib/interpreter/*"
  YARN_INTP_CLASSPATH="${YARN_INTP_CLASSPATH}:${DOCKER_JAVA_HOME}/lib"
  YARN_INTP_CLASSPATH="${YARN_INTP_CLASSPATH}:${DOCKER_JAVA_HOME}/jre/lib"
  YARN_INTP_CLASSPATH="${YARN_INTP_CLASSPATH}:${DOCKER_JAVA_HOME}/jre/lib/charsets.jar"
  if [[ "${INTERPRETER_ID}" == "spark" ]]; then
    YARN_INTP_CLASSPATH="${YARN_INTP_CLASSPATH}:/zeppelin/interpreter/spark/dep/*"
  fi
fi

addJarInDirForIntp "${LOCAL_INTERPRETER_REPO}"

if [[ ! -z "$ZEPPELIN_IMPERSONATE_USER" ]]; then
  if [[ "${INTERPRETER_ID}" != "spark" || "$ZEPPELIN_IMPERSONATE_SPARK_PROXY_USER" == "false" ]]; then
    suid="$(id -u ${ZEPPELIN_IMPERSONATE_USER})"
    if [[ -n  "${suid}" || -z "${SPARK_SUBMIT}" ]]; then
       INTERPRETER_RUN_COMMAND=${ZEPPELIN_IMPERSONATE_RUN_CMD}" '"
       if [[ -f "${ZEPPELIN_CONF_DIR}/zeppelin-env.sh" ]]; then
           INTERPRETER_RUN_COMMAND+=" source "${ZEPPELIN_CONF_DIR}'/zeppelin-env.sh;'
       fi
    fi
  fi
fi

if [[ -n "${SPARK_SUBMIT}" && "${ZEPPELIN_RUN_MODE}" != "yarn" ]]; then
    INTERPRETER_RUN_COMMAND+=' '` echo ${SPARK_SUBMIT} --class ${ZEPPELIN_SERVER} --driver-class-path \"${ZEPPELIN_INTP_CLASSPATH_OVERRIDES}:${ZEPPELIN_INTP_CLASSPATH}\" --driver-java-options \"${JAVA_INTP_OPTS}\" ${SPARK_SUBMIT_OPTIONS} ${ZEPPELIN_SPARK_CONF} ${SPARK_APP_JAR} ${CALLBACK_HOST} ${PORT} ${INTP_GROUP_ID} ${INTP_PORT}`
elif [[ "${ZEPPELIN_RUN_MODE}" == "yarn" ]]; then
    INTERPRETER_RUN_COMMAND+=' '` echo ${YARN_RUNNER} --worker_launch_cmd \"${YARN_DOCKER_JAVA_INTP_OPTS} ${ZEPPELIN_INTP_MEM} -cp ${YARN_INTP_CLASSPATH} ${ZEPPELIN_SERVER} ${CALLBACK_HOST} ${PORT} ${INTP_GROUP_ID} ${INTP_PORT}\"`
else
    INTERPRETER_RUN_COMMAND+=' '` echo ${ZEPPELIN_RUNNER} ${JAVA_INTP_OPTS} ${ZEPPELIN_INTP_MEM} -cp ${ZEPPELIN_INTP_CLASSPATH_OVERRIDES}:${ZEPPELIN_INTP_CLASSPATH} ${ZEPPELIN_SERVER} ${CALLBACK_HOST} ${PORT} ${INTP_GROUP_ID} ${INTP_PORT}`
fi

if [[ ! -z "$ZEPPELIN_IMPERSONATE_USER" ]] && [[ -n "${suid}" || -z "${SPARK_SUBMIT}" ]]; then
    INTERPRETER_RUN_COMMAND+="'"
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
