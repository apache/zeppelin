---
layout: page
title: "Install"
description: "This page will help you get started and will guide you through installing Apache Zeppelin and running it in the command line."
group: quickstart
---
<!--
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
-->
{% include JB/setup %}

# Zeppelin interpreter on Docker

Zeppelin service runs on local server. zeppelin is able to run the interpreter in the docker container, Isolating the operating environment of the interpreter through the docker container. Zeppelin can be easily used without having to install python, spark, etc. on the local node.

Key benefits are

 - Interpreter environment isolating
 - Not need to install python, spark, etc. environment on the local node
 - Docker does not need to pre-install zeppelin binary package, Automatically upload local zeppelin interpreter lib files to the container
 - Automatically upload local configuration files (such as spark-conf, hadoop-conf-dir, keytab file, ...) to the container, so that the running environment in the container is exactly the same as the local.
 - Zeppelin server runs locally, making it easier to manage and maintain

## Prerequisites

 - apache/zeppelin docker image
 - Spark >= 2.2.0 docker image (in case of using Spark Interpreter)
 - Docker 1.6+ [Install Docker](https://docs.docker.com/v17.12/install/)
 - Use docker's host network, so there is no need to set up a network specifically

### Docker Configuration

Because `DockerInterpreterProcess` communicates via docker's tcp interface.

By default, docker provides an interface as a sock file, so you need to modify the configuration file to open the tcp interface remotely.

vi `/etc/docker/daemon.json`, Add `tcp://0.0.0.0:2375` to the `hosts` configuration item.

```
{
    ...
    "hosts": ["tcp://0.0.0.0:2375","unix:///var/run/docker.sock"]
}
```

`hosts` property reference: https://docs.docker.com/engine/reference/commandline/dockerd/


## Quickstart

Modify these 2 configuration items in `zeppelin-site.xml`.

```
  <property>
    <name>zeppelin.run.mode</name>
    <value>docker</value>
    <description>'auto|local|k8s|docker'</description>
  </property>

  <property>
    <name>zeppelin.docker.container.image</name>
    <value>apache/zeppelin</value>
    <description>Docker image for interpreters</description>
  </property>

```


## Build Zeppelin image manually

To build Zeppelin image, support Kerberos certification & install spark binary.

Use the `/scripts/docker/interpreter/Dockerfile` to build the image.

```
FROM apache/zeppelin:0.8.0
MAINTAINER Apache Software Foundation <dev@zeppelin.apache.org>

ENV SPARK_VERSION=2.3.3
ENV HADOOP_VERSION=2.7

# support Kerberos certification
RUN install -yq curl unzip wget grep sed vim krb5-user libpam-krb5 && apt-get clean

# auto upload zeppelin interpreter lib
RUN rm /zeppelin -R

RUN rm /spark -R
RUN wget https://www-us.apache.org/dist/spark/spark-${SPARK_VERSION}/spark-${SPARK_VERSION}-bin-hadoop${HADOOP_VERSION}.tgz
RUN tar zxvf spark-${SPARK_VERSION}-bin-hadoop${HADOOP_VERSION}.tgz
RUN mv spark-${SPARK_VERSION}-bin-hadoop${HADOOP_VERSION} spark
RUN rm spark-${SPARK_VERSION}-bin-hadoop${HADOOP_VERSION}.tgz
```

Then build docker image.

```
# build image. Replace <tag>.
$ docker build -t <tag> .
```

## How it works

### Zeppelin interpreter on Docker

Zeppelin service runs on local server, it auto configure itself to use `DockerInterpreterLauncher`.

`DockerInterpreterLauncher` via `DockerInterpreterProcess` launcher creates each interpreter in a container using docker image.

`DockerInterpreterProcess` uploads the binaries and configuration files of the local zeppelin service to the container:

 - ${ZEPPELIN_HOME}/bin
 - ${ZEPPELIN_HOME}/lib
 - ${ZEPPELIN_HOME}/interpreter/${interpreterGroupName}
 - ${ZEPPELIN_HOME}/conf/zeppelin-site.xml
 - ${ZEPPELIN_HOME}/conf/log4j.properties
 - ${ZEPPELIN_HOME}/conf/log4j_yarn_cluster.properties
 - HADOOP_CONF_DIR
 - SPARK_CONF_DIR
 - /etc/krb5.conf
 - Keytab file configured in the interpreter properties
   - zeppelin.shell.keytab.location
   - spark.yarn.keytab
   - submarine.hadoop.keytab
   - zeppelin.jdbc.keytab.location
   - zeppelin.server.kerberos.keytab

All file paths uploaded to the container, Keep the same path as the local one. This will ensure that all configurations are used correctly.

### Spark interpreter on Docker

When interpreter group is `spark`, Zeppelin sets necessary spark configuration automatically to use Spark on Docker.
Supports all running modes of `local[*]`, `yarn-client`, and `yarn-cluster` of zeppelin spark interpreter.

#### SPARK_CONF_DIR

1. Configuring in the zeppelin-env.sh

  Because there are only spark binary files in the interpreter image, no spark conf files are included.
  The configuration file in the `spark-<version>/conf/` local to the zeppelin service needs to be uploaded to the ``/spark/conf/` directory in the spark interpreter container.
  So you need to setting `export SPARK_CONF_DIR=/spark-<version>-path/conf/` in the `zeppelin-env.sh` file.

2. Configuring in the spark Properties

  You can also configure it in the spark interpreter properties.

  | properties name | Value | Description |
  | ----- | ----- | ----- |
  | SPARK_CONF_DIR | /spark-<version>-path.../conf/ | Spark-<version>-path/conf/ path local on the zeppelin service |


#### HADOOP_CONF_DIR

1. Configuring in the zeppelin-env.sh

  Because there are only spark binary files in the interpreter image, no configuration files are included.
  The configuration file in the `hadoop-<version>/etc/hadoop` local to the zeppelin service needs to be uploaded to the spark interpreter container.
  So you need to setting `export HADOOP_CONF_DIR=hadoop-<version>-path/etc/hadoop` in the `zeppelin-env.sh` file.

2. Configuring in the spark Properties

  You can also configure it in the spark interpreter properties.

  | properties name | Value | Description |
  | ----- | ----- | ----- |
  | HADOOP_CONF_DIR | hadoop-<version>-path/etc/hadoop | hadoop-<version>-path/etc/hadoop path local on the zeppelin service |


#### Accessing Spark UI (or Service running in interpreter container)

Because the zeppelin interpreter container uses the host network, the spark.ui.port port is automatically allocated, so do not configure `spark.ui.port=xxxx` in `spark-defaults.conf`


## Future work

 - Configuring container resources that can be used by different interpreters by configuration.


## Development

Instead of build Zeppelin distribution package and docker image everytime during development,
Zeppelin can run locally (such as inside your IDE in debug mode) and able to run Interpreter using [DockerInterpreterLauncher](https://github.com/apache/zeppelin/blob/master/zeppelin-plugins/launcher/docker/src/main/java/org/apache/zeppelin/interpreter/launcher/DockerInterpreterLauncher.java) by configuring following environment variables.


| Environment variable | Value | Description |
| ----- | ----- | ----- |
| ZEPPELIN_RUN_MODE | docker | Make Zeppelin run interpreter on Docker |
| ZEPPELIN_DOCKER_CONTAINER_IMAGE | <image>:<version> | Zeppelin interpreter docker image to use |

