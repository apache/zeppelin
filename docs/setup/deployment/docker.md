---
layout: page
title: "Apache Zeppelin Releases Docker Images"
description: "This document contains instructions about making docker containers for Zeppelin. It mainly provides guidance into how to create, publish and run docker images for zeppelin releases."
group: setup/deployment 
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

# Docker Image for Apache Zeppelin Releases 

<div id="toc"></div>

## Overview 
This document contains instructions about making docker containers for Zeppelin. It mainly provides guidance into how to create, publish and run docker images for zeppelin releases.

## Quick Start

### Installing Docker
You need to [install docker](https://docs.docker.com/engine/installation/) on your machine.

### Running docker image for Zeppelin distribution

```bash
docker run -p 8080:8080 --rm --name zeppelin apache/zeppelin:<release-version> 
```

* Zeppelin will run at `http://localhost:8080`.

If you want to specify `logs` and `notebook` dir, 

```bash
docker run -p 8080:8080 --rm \
-v $PWD/logs:/logs \
-v $PWD/notebook:/notebook \
-e ZEPPELIN_LOG_DIR='/logs' \
-e ZEPPELIN_NOTEBOOK_DIR='/notebook' \
--name zeppelin apache/zeppelin:<release-version> # e.g '0.7.1'
```

### Building dockerfile locally

```bash
cd $ZEPPELIN_HOME
cd scripts/docker/zeppelin/bin

docker build -t my-zeppelin:my-tag ./
```

### Build docker image for Zeppelin server & interpreters

Starting from 0.9, Zeppelin support to run in k8s or docker. So we add the capability to 
build docker images for zeppelin server & interpreter. Before building docker image, you need to build zeppelin first, please refer for how to build zeppelin.


Build base docker image for zeppelin components(including zeppelin server and its interpreters) and push it into your docker repo.
```bash
cd $ZEPPELIN_HOME
bin/build-images.sh -r <your_docker_repo> zeppelin-base
```

Build docker image for zeppelin server and push it into your docker repo.
```bash
cd $ZEPPELIN_HOME
bin/build-images.sh -r <your_docker_repo> server
```

Build base docker image for zeppelin interpreter and push it into your docker repo.
```bash
cd $ZEPPELIN_HOME
bin/build-images.sh -r <your_docker_repo> interpreter-base
```

Build image for zeppelin interpreter <interpreter_name> and push it into your docker repo. By default, we use the `scripts/docker/zeppelin-interpreter/Dockerfile` to build the interpreter image, but you can customize interpreter image by
creating new docker file under `scripts/docker/zeppelin-interpreter` and name it to be `Dockerfile_<interpreter_name>`. For examples, in offical Apache Zeppelin, we provide 4 customized images for python,r,spark.
```bash
cd $ZEPPELIN_HOME
bin/build-images.sh -r <your_docker_repo> interpreter <interpreter_name>
```
