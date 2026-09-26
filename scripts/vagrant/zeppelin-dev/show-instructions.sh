#!/bin/bash
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements. See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License. You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

echo '############################################################'
echo '# DEPRECATED: this Vagrant environment is no longer maintained'
echo '# and is not expected to provision a usable build environment.'
echo '# See scripts/vagrant/zeppelin-dev/README.md and'
echo '# https://issues.apache.org/jira/browse/ZEPPELIN-6460'
echo '#'
echo '# To build Zeppelin, follow docs/setup/basics/how_to_build.md.'
echo '# It needs only Git and JDK 11: ./mvnw supplies Maven, and the'
echo '# frontend build downloads its own Node.js and npm.'
echo '############################################################'
echo
echo '# Post vagrant up instructions.'
echo '# From your host machine,'
echo '# git clone the zeppelin branch into this directory'
echo
echo 'git clone https://github.com/apache/zeppelin.git'
echo
echo '# Cloning the project again may seem counter intuitive, since this script'
echo '# originated from the project repository.  Consider copying just the vagrant/zeppelin-dev'
echo '# script from the zeppelin project as a stand alone directory, then once again clone'
echo '# the specific branch you wish to build.'
echo
echo 'vagrant ssh'
echo
echo '# then when running inside the VM'
echo
echo 'cd /vagrant/zeppelin'
echo './mvnw clean package -DskipTests'
echo './bin/zeppelin-daemon.sh start'
echo
echo '# See docs/setup/basics/how_to_build.md for the Spark, Flink and Hadoop'
echo '# build profiles this project currently supports.'
echo
echo 'On your host machine browse to http://localhost:8080/'
