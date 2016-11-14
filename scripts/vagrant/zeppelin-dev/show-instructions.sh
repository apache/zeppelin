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

echo '# Post vagrant up instructions.'
echo '# From your host machine,'
echo '# git clone the zeppelin branch into this directory'
echo
echo 'git clone git://git.apache.org/zeppelin.git'
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
echo 'mvn clean package -DskipTests'
echo
echo '# or for a specific Spark/Hadoop build with additional options such as python and R support'
echo
echo 'mvn clean package -Pspark-1.6 -Ppyspark -Phadoop-2.4 -Psparkr -DskipTests'
echo './bin/zeppelin-daemon.sh start'
echo
echo 'On your host machine browse to http://localhost:8080/'

