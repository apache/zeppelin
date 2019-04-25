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

# Zeppelin on Yarn

Zeppelin can run on [Yarn](https://hadoop.apache.org). Zeppelin server runs in local host, it creates docker container for individual interpreter. All interpreters run in yarn (The local mode of the spark interpreter also runs on the yarn).

Key benefits are

 - Interpreter scale-out
 - Docker container environmental isolation
 - Users can easily install python and R libraries

## Prerequisites

 - YARN 3.3.0+, You can use [Submarine-installer](https://github.com/hadoopsubmarine/submarine-installer), install Yarn and Docker runtime environments.

## Build Zeppelin image manually

To build your own Zeppelin image, first build Zeppelin project with `-Pbuild-distr` flag.

```
$ mvn package -DskipTests -Pbuild-distr <your flags>
```

Binary package will be created under `zeppelin-distribution/target` directory. Move created package file under `scripts/docker/zeppelin/bin/` directory.

```
$ mv zeppelin-distribution/target/zeppelin-*.tar.gz scripts/docker/zeppelin/bin/
```

`scripts/docker/zeppelin/bin/Dockerfile` downloads package from internet. Modify the file to add package from filesystem.

```
...

# Find following section and comment out
#RUN echo "$LOG_TAG Download Zeppelin binary" && \
#    wget -O /tmp/zeppelin-${Z_VERSION}-bin-all.tgz http://archive.apache.org/dist/zeppelin/zeppelin-${Z_VERSION}/zeppelin-${Z_VERSION}-bin-all.tgz && \
#    tar -zxvf /tmp/zeppelin-${Z_VERSION}-bin-all.tgz && \
#    rm -rf /tmp/zeppelin-${Z_VERSION}-bin-all.tgz && \
#    mv /zeppelin-${Z_VERSION}-bin-all ${Z_HOME}

# Add following lines right after the commented line above
ADD zeppelin-${Z_VERSION} /
RUN mv /zeppelin-${Z_VERSION} /zeppelin
...
```

Then build docker image.

```
# change directory
$ cd scripts/docker/zeppelin/bin/

# build image. Replace <tag>.
$ docker build -t <tag> .
```

## How it works

### Zeppelin on Yarn

When you create a specific interpreter, Zeppelin uses the `yarn job run` command, Create a zeppelin docker container in yarn.
Send the start command of this interpreter to the container, After the interpreter in the container is started, Connect by using the IP and port of the server where the container is located.

Each interpreter starts a docker container, There is a completely isolated container environment between different interpreters.

### Spark on Yarn

The spark interpreter for `master=yarn-client` and `master=yarn-cluster` mode is already supported in yarn.
The downside is that `pyspark` and `sparkR` need to have `Python` and `R` dependent libraries installed on all YARN servers. The advantage is the ability to process large amounts of data.

So, we only run the spark interpreter for `master=local[*]` in the docker of yarn.
The advantage is that it is convenient to install Python and R dependent libraries. The disadvantage is that the amount of data that can be processed is limited.

Wait for the new version of hadoop to support spark on yarn docker, Upgrade the spark interpreter On Yarn.

## Persist /notebook and /conf directory

Zeppelin On YARN mode, The zeppelin server still runs in local, so the notebook and conf is still stored in local, It is not stored in the docker container where the interpreter is located.


## Future work

 - Ability to create multiple spark containers to form a spark standalone mode to solve large data volume scenarios.
 - Let the `python` and `spark` interpreter embed the `shell` interpreter, Let the user container maintain the environment through shell commands.
 - Upgrade the `shell` interpreter, the current shell function is relatively simple, no ability to explore.
 - Wait for the new version of hadoop to support spark on yarn docker, Upgrade the spark interpreter On Yarn.

## Development

Instead of build Zeppelin distribution package and docker image everytime during development,
Zeppelin can run locally (such as inside your IDE in debug mode) and able to run Interpreter using [YarnStandardInterpreterLauncher](https://github.com/apache/zeppelin/blob/master/zeppelin-plugins/launcher/yarn-standard/src/main/java/org/apache/zeppelin/interpreter/launcher/YarnStandardInterpreterLauncher.java) by configuring following environment variables.

### bin/zeppelin-env.sh

| Environment variable | Value | Description |
| ----- | ----- | ----- |
| HADOOP_HOME | hadoop home dir | bin/interpreter.sh need this environment variable |
| HADOOP_YARN_SUBMARINE_JAR | /hadoop-<version>/submarine/hadoop-yarn-submarine-<version>-standalone.jar | Create docker container in the yarn |
| DOCKER_HADOOP_HOME | /hadoop | Haodop home dir in docker containter |
| DOCKER_JAVA_HOME | /usr/lib/jvm/java-8-openjdk-amd64 | Java home dir in docker containter |
| UPLOAD_LOCAL_LIB_TO_CONTAINTER | `true` or `false` | Upload the local zeppelin library to the container, Default equal true |

### conf/zeppelin-site.xml

| Environment variable | Value | Description |
| ----- | ----- | ----- |
| zeppelin.run.mode | yarn | Make Zeppelin run interpreter on Yarn |
| zeppelin.yarn.webapp.address |  | Yarn webapp address, The zeppelin server gets the state of the interpreter container through this webapp  |
| zeppelin.yarn.container.image | <image>:<version> | Zeppelin interpreter docker image to use |
| zeppelin.yarn.container.resource | memory=8G,vcores=1,gpu=0 | Docker default resource for interpreters container |
| zeppelin.yarn.container.${INTERPRETER_SETTING_NAME}.resource | memory=8G,vcores=1,gpu=0 | Set different resources for different interpreters, e.g. zeppelin.yarn.container.`python`.resource |

## Dependency

1. **YARN**
  You can compile and deploy the yarn 3.3+ version separately, Yarn 3.3 is compatible with hdfs 2.7+ version.

2. **Yarn runtime environment**
  you can use Submarine-installer https://github.com/hadoopsubmarine, Help you install the yarn3.3 and docker runtime environment.

## Bugs & Contacts

+ **Zeppelin on Yarn BUG**
  If you encounter a bug, please create a sub **JIRA** ticket on [ZEPPELIN-4050](https://issues.apache.org/jira/browse/ZEPPELIN-4050).
+ **YARN runtime environment problem**
  If you encounter a problem for Yarn runtime, please create a **ISSUE** on [Yarn installer](https://github.com/hadoopsubmarine/submarine-installer).
