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
# -- Documentation
#
#We need to build image:
#    docker build -t incubator-zeppelin .
#
#We can play with the image to run goal maven:
#  docker run -ti p 8080:8080 --rm [-v ~/.m2:/home/zeppelin/.m2] $(pwd):/home/zeppelin/incubator-zeppelin --name iz incubator-zeppelin mvn clean install -DskipTests [-Pspark-XXX -Dhadoop.version=XXX -Phadoop-XXX]
#
# We must mount your project directory with "/home/zeppelin/incubator-zeppelin"
#  -v "$(path project zeppelin):/home/zeppelin/incubator-zeppelin"
# We can mount your directory ".m2" with VOLUME "/home/zeppelin/.m2"
#  -v "~/.m2:/home/zeppelin/.m2"
#
#Remove container:
#  docker rm -f iz
#Stop:
#  docker stop iz
#Remove image:
#  docker rmi -f incubator-zeppelin
#Run with bash: 
#  docker run -p 8080:8080 -ti --rm [-v ~/.m2:/home/zeppelin/.m2] -v $(pwd):/home/zeppelin/incubator-zeppelin --name iz incubator-zeppelin
#Push:
#  docker push incubator-zeppelin
FROM centos:latest

MAINTAINER Zeppelin Project (incubating) 

#for plugin frontend-maven-plugin we need maven >= 3.1.0
ENV MAVEN_VERSION=3.3.3 
ENV MAVEN_HOME=/usr/local/maven 
ENV MAVEN_OPTS="-Xmx512m -XX:MaxPermSize=256m"

ENV PATH=${PATH}:${MAVEN_HOME}/bin


EXPOSE 8080

#bzip2 for npm (tar .bz2)
RUN yum -y update
#fontconfig to phantomJS
#bzip2 to install modules npm
RUN yum install -y git bzip2 fontconfig 
RUN yum clean all

#install jdk
RUN curl http://download.oracle.com/otn-pub/java/jdk/7u80-b15/jdk-7u80-linux-x64.rpm -L -C - -b "oraclelicense=accept-securebackup-cookie" -o /tmp/jdk.rpm
RUN rpm -ivh /tmp/jdk.rpm

#install maven
RUN curl -L http://wwwftp.ciril.fr/pub/apache/maven/maven-3/$MAVEN_VERSION/binaries/apache-maven-$MAVEN_VERSION-bin.tar.gz -o /tmp/apache-maven.tar.gz 
RUN mkdir -p $MAVEN_HOME 
RUN tar --strip-components 1 -xzvf /tmp/apache-maven.tar.gz -C $MAVEN_HOME 
RUN curl -L http://central.maven.org/maven2/org/apache/maven/wagon/wagon-http-lightweight/2.10/wagon-http-lightweight-2.10.jar -o /usr/local/maven/lib/ext/wagon-http-lightweight-2.10.jar

#clear download
RUN rm -f /tmp/apache-maven.tar.gz /tmp/jdk.rpm


#Add user zeppelin
RUN useradd -ms /bin/bash zeppelin
RUN mkdir -p touch /home/zeppelin/.m2; touch /home/zeppelin/.m2/toChown.txt
RUN mkdir -p /home/zeppelin/incubator-zeppelin; touch /home/zeppelin/incubator-zeppelin/toChown.txt
RUN chown -R zeppelin:zeppelin /home/zeppelin/.m2
RUN chown -R zeppelin:zeppelin /home/zeppelin/incubator-zeppelin

USER zeppelin
WORKDIR /home/zeppelin/incubator-zeppelin

VOLUME ["/home/zeppelin/.m2", "/home/zeppelin/incubator-zeppelin"]
