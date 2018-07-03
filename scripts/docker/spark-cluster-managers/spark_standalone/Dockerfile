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

ENV SPARK_PROFILE 2.1
ENV SPARK_VERSION 2.1.2
ENV HADOOP_PROFILE 2.7
ENV SPARK_HOME /usr/local/spark

# Update the image with the latest packages
RUN yum update -y; yum clean all

# Get utils
RUN yum install -y \
wget \
tar \
curl \
&& \
yum clean all

# Remove old jdk
RUN yum remove java; yum remove jdk

# install jdk7 
RUN yum install -y java-1.7.0-openjdk-devel
ENV JAVA_HOME /usr/lib/jvm/java
ENV PATH $PATH:$JAVA_HOME/bin

# install spark
RUN curl -s http://apache.mirror.cdnetworks.com/spark/spark-$SPARK_VERSION/spark-$SPARK_VERSION-bin-hadoop$HADOOP_PROFILE.tgz | tar -xz -C /usr/local/
RUN cd /usr/local && ln -s spark-$SPARK_VERSION-bin-hadoop$HADOOP_PROFILE spark

# update boot script
COPY entrypoint.sh /etc/entrypoint.sh
RUN chown root.root /etc/entrypoint.sh
RUN chmod 700 /etc/entrypoint.sh

#spark
EXPOSE 8080 7077 8888 8081

ENTRYPOINT ["/etc/entrypoint.sh"]
