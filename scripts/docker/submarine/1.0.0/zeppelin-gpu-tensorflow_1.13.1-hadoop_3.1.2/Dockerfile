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

FROM nvidia/cuda:9.0-cudnn7-devel-ubuntu16.04
MAINTAINER Apache Software Foundation <dev@zeppelin.apache.org>

# Pick up some TF dependencies
RUN apt-get update && apt-get install -y --allow-downgrades --no-install-recommends \
  --allow-change-held-packages --allow-unauthenticated \
  build-essential libfreetype6-dev libpng12-dev \
  libzmq3-dev pkg-config python python-dev \
  rsync software-properties-common curl unzip wget grep sed vim \
    iputils-ping net-tools gdb python2.7-dbg tzdata \
    cuda-cublas-9-0 cuda-cufft-9-0 cuda-curand-9-0 cuda-cusolver-9-0 \
  cuda-cusparse-9-0 libcudnn7=7.0.5.15-1+cuda9.0 && \
  apt-get clean && \
  rm -rf /var/lib/apt/lists/*

RUN export DEBIAN_FRONTEND=noninteractive && apt-get update && apt-get install -yq krb5-user libpam-krb5 && apt-get clean

RUN curl -O https://bootstrap.pypa.io/get-pip.py && \
  python get-pip.py && \
  rm get-pip.py

RUN echo "Install python related packages" && \
  apt-get -y update && \
  apt-get install -y gfortran && \
  # numerical/algebra packages
  apt-get install -y libblas-dev libatlas-dev liblapack-dev && \
  # font, image for matplotlib
  apt-get install -y libpng-dev libxft-dev && \
  # for tkinter
  apt-get install -y python-tk libxml2-dev libxslt-dev zlib1g-dev

RUN pip --no-cache-dir install Pillow h5py ipykernel jupyter matplotlib numpy pandas scipy sklearn && \
  python -m ipykernel.kernelspec

# Set the locale
# disable bash: warning: setlocale: LC_ALL: cannot change locale (en_US.UTF-8)
RUN apt-get clean && apt-get update && apt-get install -y locales
RUN locale-gen en_US.UTF-8

# Install TensorFlow GPU version.
ENV TENSORFLOW_VERSION="1.13.1"
RUN pip --no-cache-dir install \
  http://storage.googleapis.com/tensorflow/linux/gpu/tensorflow_gpu-${TENSORFLOW_VERSION}-cp27-none-linux_x86_64.whl
RUN apt-get update && apt-get install git -y

# Install hadoop
ENV HADOOP_VERSION="3.1.2"
RUN wget https://www.apache.org/dyn/closer.cgi/hadoop/common/hadoop-${HADOOP_VERSION}/hadoop-${HADOOP_VERSION}.tar.gz
RUN tar zxf hadoop-${HADOOP_VERSION}.tar.gz
RUN ln -s hadoop-${HADOOP_VERSION} hadoop-current
RUN rm hadoop-${HADOOP_VERSION}.tar.gz

ENV JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64
RUN echo "$LOG_TAG Install java8" && \
  apt-get -y update && \
  apt-get install -y openjdk-8-jdk && \
  rm -rf /var/lib/apt/lists/*
