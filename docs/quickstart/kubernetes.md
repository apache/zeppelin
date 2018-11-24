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

# Zeppelin on Kubernetes

Zeppelin can run on clusters managed by [Kubernetes](https://kubernetes.io/). When Zeppelin runs in Pod, it creates pods for individual interpreter. Also Spark interpreter auto configured to use Spark on Kubernetes.

Key benefits are

 - Interpreter scale-out
 - Spark interpreter auto configure Spark on Kubernetes
 - Able to customize Kubernetes yaml file
 - Spark UI access

## Prerequisites

 - Zeppelin >= 0.9.0
 - Spark >= 2.4.0
 - A running Kubernetes cluster with access configured to it using [kubectl](https://kubernetes.io/docs/tasks/tools/install-kubectl/) 
 - [Kubernetes DNS](https://kubernetes.io/docs/concepts/services-networking/dns-pod-service/) configured in your cluster
 - Enough cpu and memory in your Kubernetes cluster. We recommend 4CPUs, 6g of memory to be able to start Spark Interpreter with few executors.
  
   - If you're using [minikube](https://kubernetes.io/docs/setup/minikube/), check your cluster capacity (`kubectl describe node`) and increase if necessary
     ```
     $ minikube delete    # otherwise configuration won't apply
     $ minikube config set cpus <number>
     $ minikube config set memory <number in MB>
     $ minikube start
     $ minikube config view
     ``` 
 
## Quickstart

Get `zeppelin-server.yaml` from github repository or find it from Zeppelin distribution package.

```
# download it from github 
$ curl -s -O https://raw.githubusercontent.com/apache/zeppelin/master/k8s/zeppelin-server.yaml

# or get it from Zeppelin distribution package.
$ ls <zeppelin-distribution>/k8s/zeppelin-server.yaml
```

Start zeppelin on kubernetes cluster,

```
kubectl apply -f zeppelin-server.yaml
```

Port forward Zeppelin server port,
 
```
kubectl port-forward zeppelin-server 8080:80
```

and browse [localhost:8080](http://localhost:8080).


To shutdown,

```
kubectl delete -f zeppelin-server.yaml
```


## Spark Interpreter

Build spark docker image to use Spark Interpreter.
Download spark binary distribution and run following command.
Spark 2.4.0 or later version is required.

```
# if you're using minikube, set docker-env
$ eval $(minikube docker-env)

# build docker image
$ <spark-distribution>/bin/docker-image-tool.sh -m -t 2.4.0 build
```

Run `docker images` and check if `spark:2.4.0` is created.
Configure `sparkContainerImage` of `zeppelin-server-conf` ConfigMap in `zeppelin-server.yaml`.


Create note and configure executor number (default 1)

```
%spark.conf
spark.executor.instances  5
``` 

And then start your spark interpreter

```
%spark
sc.parallelize(1 to 100).count
...
```
While `master` property of SparkInterpreter starts with `k8s://` (default `k8s://https://kubernetes.default.svc` when Zeppelin started using zeppelin-server.yaml), Spark executors will be automatically created in your Kubernetes cluster.
Spark UI is accessible by clicking `SPARK JOB` on the Paragraph. 

Check [here](https://spark.apache.org/docs/latest/running-on-kubernetes.html) to know more about Running Spark on Kubernetes.


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
ADD zeppelin-${Z_VERSION}.tar.gz /
RUN ln -s /zeppelin-${Z_VERSION} /zeppelin
...
```

Then build docker image.

```
# configure docker env, if you're using minikube
$ eval $(minikube docker-env) 

# change directory
$ cd scripts/docker/zeppelin/bin/

# build image. Replace <tag>.
$ docker build -t <tag> .
```

Finally, set custom image `<tag>` just created to `image` and `ZEPPELIN_K8S_CONTAINER_IMAGE` env variable of `zeppelin-server` container spec in `zeppelin-server.yaml` file.
