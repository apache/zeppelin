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
FROM centos:centos6

ENV SPARK_PROFILE 2.0
ENV SPARK_VERSION 2.0.0
ENV HADOOP_PROFILE 2.3
ENV HADOOP_VERSION 2.3.0

# Update the image with the latest packages
RUN yum update -y; yum clean all

# Get utils
RUN yum install -y \
wget \
tar \
curl \
svn \
cyrus-sasl-md5 \
libevent2-devel \
&& \
yum clean all

# Remove old jdk
RUN yum remove java; yum remove jdk

# install jdk7
RUN yum install -y java-1.7.0-openjdk-devel
ENV JAVA_HOME /usr/lib/jvm/java
ENV PATH $PATH:$JAVA_HOME/bin

# install spark
RUN curl -s http://www.apache.org/dist/spark/spark-$SPARK_VERSION/spark-$SPARK_VERSION-bin-hadoop$HADOOP_PROFILE.tgz | tar -xz -C /usr/local/
RUN cd /usr/local && ln -s spark-$SPARK_VERSION-bin-hadoop$HADOOP_PROFILE spark

# update boot script
COPY entrypoint.sh /etc/entrypoint.sh
RUN chown root.root /etc/entrypoint.sh
RUN chmod 700 /etc/entrypoint.sh

# install mesos
RUN wget http://repos.mesosphere.com/el/6/x86_64/RPMS/mesos-1.0.0-2.0.89.centos65.x86_64.rpm
RUN rpm -Uvh mesos-1.0.0-2.0.89.centos65.x86_64.rpm

#spark
EXPOSE 8080 7077 7072 8081 8082

#mesos
EXPOSE 5050 5051

ENTRYPOINT ["/etc/entrypoint.sh"]
