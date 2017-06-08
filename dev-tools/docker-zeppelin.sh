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
#!/bin/bash


display_usage() {
 green="\033[0;32m"
 reset_color="\033[0m" 
 echo "Usage:"
 echo -e "$0 build         $green#to build the Dockerfile$reset_color"
 echo -e "$0 run           $green#to run the image \"incubator-zeppelin\" with interaction$reset_color"
 echo -e "$0 run-with-m2   $green#to run the image \"incubator-zeppelin\" with interaction and mount your ~.m2 with container$reset_color" 
 echo -e "$0 run-zeppelin [-Pspark-XXX -Dhadoop.version=XXX -Phadoop-XXX] $green#to run the image \"incubator-zeppelin\" in daemon, mount your ~.m2 with container and run goal maven and start zeppelin$reset_color" 
} 

current=$(dirname "${BASH_SOURCE-$0}")
folderZeppelin=$(cd "${current}/..">/dev/null; pwd)
command="${1}"

case ${command} in
  build)
     echo "Build docker incubator-zeppelin"
     docker build -t base-incubator-zeppelin -f $current/Dockerfile .
     ;;
  run)
     docker run -p :8080 -ti --rm -v $folderZeppelin:/root/incubator-zeppelin -w /root/incubator-zeppelin --name iz base-incubator-zeppelin
     ;;
  run-with-m2)
     docker run -p :8080 -ti --rm -v ~/.m2:/root/.m2 -v $folderZeppelin:/root/incubator-zeppelin -w /root/incubator-zeppelin --name iz base-incubator-zeppelin
     ;;
  run-zeppelin)
     configuration_maven=${@:2}
     container=$(date "+iz-%Y%m%d%H%M")
     docker run -p :8080 -d -v ~/.m2:/root/.m2 -v $folderZeppelin:/root/incubator-zeppelin -w /root/incubator-zeppelin --name $container base-incubator-zeppelin \
        /bin/bash -c  "mvn clean package -DskipTests $configuration_maven; /home/zeppelin/incubator-zeppelin/bin/zeppelin.sh"
     echo "You can follow logs docker logs -f $container" 
     ;;
  *)
     display_usage 
     exit 1 # Command to come out of the program with status 1
     ;;
esac
