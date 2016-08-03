---
layout: page
title: "Apache Zeppelin on Spark cluster mode"
description: ""
group: install
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

# Apache Zeppelin on Spark Cluster Mode

<div id="toc"></div>

## Overview 
[Apache Spark](http://spark.apache.org/) has supported three cluster manager types([Standalone](http://spark.apache.org/docs/latest/spark-standalone.html), [Apache Mesos](http://spark.apache.org/docs/latest/running-on-mesos.html) and [Hadoop YARN](http://spark.apache.org/docs/latest/running-on-yarn.html)) so far.
This document will guide you how you can build and configure the environment on 3 types of Spark cluster manager with Apache Zeppelin using [Docker](https://www.docker.com/) scripts.
So [install docker](https://docs.docker.com/engine/installation/) on the machine first.

## Spark standalone mode
[Spark standalone](http://spark.apache.org/docs/latest/spark-standalone.html) is a simple cluster manager included with Spark that makes it easy to set up a cluster.
You can simply set up Spark standalone environment with below steps. 

> **Note :** Since Apache Zeppelin and Spark use same `8080` port for their web UI, you might need to change `zeppelin.server.port` in `conf/zeppelin-site.xml`.

### 1. Build Docker file
You can find docker script files under `scripts/docker/spark-cluster-managers`.

```
cd $ZEPPELIN_HOME/scripts/docker/spark-cluster-managers/spark_standalone
docker build -t "spark_standalone" .
```

### 2. Run docker

```
docker run -it \
-p 8080:8080 \
-p 7077:7077 \
-p 8888:8888 \
-p 8081:8081 \
-h sparkmaster \
--name spark_standalone \
spark_standalone bash; 
```

### 3. Configure Spark interpreter in Zeppelin
Set Spark master as `spark://localhost:7077` in Zeppelin **Interpreters** setting page.

<img src="../assets/themes/zeppelin/img/docs-img/standalone_conf.png" />

### 4. Run Zeppelin with Spark interpreter
After running single paragraph with Spark interpreter in Zeppelin, browse `https://localhost:8080` and check whether Spark cluster is running well or not.

<img src="../assets/themes/zeppelin/img/docs-img/spark_ui.png" />

You can also simply verify that Spark is running well in Docker with below command.

```
ps -ef | grep spark
```


