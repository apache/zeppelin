---
layout: page
title: "Apache Zeppelin Releases Docker Images"
description: "This document contains instructions about making docker containers for Zeppelin. It mainly provides guidance into how to create, publish and run docker images for zeppelin releases."
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

# Apache Zeppelin Releases Docker Images

<div id="toc"></div>

## Overview 
This document contains instructions about making docker containers for Zeppelin. It mainly provides guidance into how to create, publish and run docker images for zeppelin releases.

## Quick Start
### Installing Docker
You need to [install docker](https://docs.docker.com/engine/installation/) on your machine.

### Creating and Publishing Zeppelin docker image 
* In order to be able to create and/or publish an image, you need to set the **DockerHub** credentials `DOCKER_USERNAME, DOCKER_PASSWORD, DOCKER_EMAIL` variables as environment variables.
 
* To create an image for some release use :
`create_release.sh <release-version> <git-tag>`.
* To publish the created image use :
`publish_release.sh <release-version> <git-tag>`

### Running a Zeppelin  docker image 

* To start Zeppelin, you need to pull the zeppelin release image: 
```
docker pull ${DOCKER_USERNAME}/zeppelin-release:<release-version>

docker run --rm -it -p 7077:7077 -p 8080:8080 ${DOCKER_USERNAME}/zeppelin-release:<release-version> -c bash
```
* Then a docker container will start with a Zeppelin release on path :
`/usr/local/zeppelin/`

* Run zeppelin inside docker:
```
/usr/local/zeppelin/bin/zeppelin.sh
```

* To Run Zeppelin in daemon mode
Mounting logs and notebooks zeppelin to folders on your host machine

```
docker run -p 7077:7077 -p 8080:8080 --privileged=true -v $PWD/logs:/logs -v $PWD/notebook:/notebook \
-e ZEPPELIN_NOTEBOOK_DIR='/notebook' \
-e ZEPPELIN_LOG_DIR='/logs' \
-d ${DOCKER_USERNAME}/zeppelin-release:<release-version> \
/usr/local/zeppelin/bin/zeppelin.sh
```


* Zeppelin will run at `http://localhost:8080`.

