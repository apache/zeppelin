
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
#

USAGE="-e Usage: start-zepppelin.sh {start|stop|restart}\n\t"

if [[ -z "$ZEPPELIN_MEM" ]]; then
    export ZEPPELIN_MEM="-Xmx1024m -XX:MaxPermSize=512m"
fi
if [[ -z "$ZEPPELIN_INTP_MEM" ]]; then
    export ZEPPELIN_INTP_MEM=$ZEPPELIN_MEM
fi
if [[ -z "$ZEPPELIN_JAVA_OPTS" ]]; then
    # By default N/A
    export ZEPPELIN_JAVA_OPTS=""
fi
if [[ -z "$ZEPPELIN_NOTEBOOK_DIR" ]]; then
    export ZEPPELIN_NOTEBOOK_DIR="/usr/local/zeppelin/notebook"
fi
if [[ -z "$ZEPPELIN_LOG_DIR" ]]; then
    export ZEPPELIN_LOG_DIR="/usr/local/zeppelin/logs"
fi
if [[ -z "$ZEPPELIN_WEBSOCKET_MAX_TEXT_MESSAGE_SIZE" ]]; then
    export ZEPPELIN_WEBSOCKET_MAX_TEXT_MESSAGE_SIZE=1024000
fi

export ZEPPELIN_PORT=8080
export ZEPPELIN_HOME="/usr/local/zeppelin"

function start(){
  ${ZEPPELIN_HOME}/bin/zeppelin-daemon.sh start
}

function stop(){
  ${ZEPPELIN_HOME}/bin/zeppelin-daemon.sh stop
}

case "${1}" in
  start)
    start
    ;;
  stop)
    stop
    ;;
  restart)
    stop
    start
    ;;
  *)
    echo ${USAGE}
esac
