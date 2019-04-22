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

Zeppelin can run on [Yarn](https://hadoop.apache.org/submarine). Zeppelin server runs in local host, it creates docker container for individual interpreter. All interpreters run in yarn (The local mode of the spark interpreter also runs on the yarn).

Key benefits are

 - Interpreter scale-out
 - Docker container environmental isolation
 - Users can easily install python and R libraries

## Prerequisites

 - YARN 3.2.0+, You can use [Submarine-installer](https://github.com/hadoopsubmarine/submarine-installer), Deploy Docker and runtime environments.
 - Zeppelin < 0.9.0 docker image, You need to set `UPLOAD_LOCAL_LIB_TO_CONTAINTER = true` in zeppelin-env.sh
 - Zeppelin >= 0.9.0 docker image, You can set `UPLOAD_LOCAL_LIB_TO_CONTAINTER = true or false` in zeppelin-env.sh, If equal true, When zeppelin starts the interpreter, it uploads the local interpreter lib library to the docker container, which keeps the local zeppelin service exactly the same as the version in the container. The disadvantage is that the container will start slightly slower.

## Spark Interpreter

If you set `zeppelin.run.mode=yarn` in zepplin-site.xml, All interpreters run in yarn (The local mode of the spark interpreter also runs on the yarn)

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

You can set `UPLOAD_LOCAL_LIB_TO_CONTAINTER = true or false` in zeppelin-env.sh. When zeppelin starts the interpreter, it uploads the local interpreter lib library to the docker container.

## How it works

### Zeppelin on Yarn

When you create a specific interpreter, Zeppelin uses the `yarn job run` command, Create a zeppelin docker container in yarn.
Send the start command of this interpreter to the container, After the interpreter in the container is started, Connect by using the IP and port of the server where the container is located.

Each interpreter starts a container, There is a completely isolated container environment between different interpreters.

### Spark on Yarn

Because spark originally supports the yarn mode, But there is a problem, You need to install the python and R packages in the nodemanager server of yarn.
It is difficult to maintain and it is easy to form version conflicts.

So only when the master configures `master=local[*]` in spark, The spark interpreter will be run in the docker container of yarn.
The purpose of doing this is because enables users of `pyspark` and `sparkR` to install and upgrade python and R libraries themselves in the container.


## Persist /notebook and /conf directory

Zeppelin on YARN mode, The zeppelin server still runs in local, so the notebook and conf is still stored in local, It is not stored in the docker container where the interpreter is located.


## Future work

 - Ability to create multiple spark containers to form a spark standalone mode to solve large data volume scenarios.
 - Let the python and spark interpreter embed the shell interpreter, Let the user container maintain the environment through shell commands.
 - Upgrade the shell interpreter, the current shell function is relatively simple, no ability to explore.


## Development

Instead of build Zeppelin distribution package and docker image everytime during development,
Zeppelin can run locally (such as inside your IDE in debug mode) and able to run Interpreter using [K8sStandardInterpreterLauncher](https://github.com/apache/zeppelin/blob/master/zeppelin-plugins/launcher/yarn-standard/src/main/java/org/apache/zeppelin/interpreter/launcher/YarnStandardInterpreterLauncher.java) by configuring following environment variables.

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

## Bugs & Contacts

+ **Zeppelin on Yarn BUG**
  If you encounter a bug for this interpreter, please create a sub **JIRA** ticket on [ZEPPELIN-3856](https://issues.apache.org/jira/browse/ZEPPELIN-4050).
+ **Submarine Running problem**
  If you encounter a problem for Submarine runtime, please create a **ISSUE** on [hadoop-submarine-ecosystem](https://github.com/hadoopsubmarine/hadoop-submarine-ecosystem).
+ **YARN Submarine BUG**
  If you encounter a bug for Yarn Submarine, please create a **JIRA** ticket on [SUBMARINE](https://issues.apache.org/jira/browse/SUBMARINE).

## Dependency

1. **YARN**
  Submarine currently need to run on Hadoop 3.3+

  + The hadoop version of the hadoop submarine team git repository is periodically submitted to the code repository of the hadoop.
  + The version of the git repository for the hadoop submarine team will be faster than the hadoop version release cycle.
  + You can use the hadoop version of the hadoop submarine team git repository.

2. **Submarine runtime environment**
  you can use Submarine-installer https://github.com/hadoopsubmarine, Deploy Docker and network environments.

## More

**Hadoop Submarine Project**: https://hadoop.apache.org/submarine
