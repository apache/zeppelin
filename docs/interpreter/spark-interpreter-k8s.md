---
layout: page
title: "Apache Spark Interpreter for Apache Zeppelin on Kubernetes"
description: "Apache Spark is a fast and general-purpose cluster computing system. It provides high-level APIs in Java, Scala, Python and R, and an optimized engine that supports general execution engine. This interpreter runs on the https://github.com/apache-spark-on-k8s/spark version of Spark"
group: interpreter
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

# How to run Zeppelin Spark notebooks on a Kubernetes cluster

<div id="toc"></div>

## Prerequisites

The following tools are required:

 - Kubernetes cluster & kubectl

    For local testing Minikube can be used to create a single node cluster: https://kubernetes.io/docs/tasks/tools/install-minikube/

 - Docker https://kubernetes.io/docs/tasks/tools/install-minikube/

    This documentation uses a pre-built Spark 2.2 Docker images, however you may also build these images as described here: https://github.com/apache-spark-on-k8s/spark/blob/branch-2.2-kubernetes/resource-managers/kubernetes/README.md

## Checkout Zeppelin source code

Checkout the latest source code from https://github.com/apache/zeppelin then apply changes from the [Add support to run Spark interpreter on a Kubernetes cluster](https://github.com/apache/zeppelin/pull/2637) pull request.

## Build Zeppelin
- `./dev/change_scala_version.sh 2.11`
- `mvn clean install -DskipTests -Pspark-2.2 -Phadoop-2.4 -Pyarn -Ppyspark -Pscala-2.11`


## Create distribution
- `cd zeppelin-distribution`
- `mvn org.apache.maven.plugins:maven-assembly-plugin:3.0.0:single -P apache-release`

## Create Zeppelin Dockerfile in Zeppelin distribution target folder
```
cd {zeppelin_source}/zeppelin-distribution/target/zeppelin-0.8.0-SNAPSHOT
cat > Dockerfile <<EOF
FROM kubespark/spark-base:v2.2.0-kubernetes-0.5.0
COPY zeppelin-0.8.0-SNAPSHOT /opt/zeppelin
ADD https://storage.googleapis.com/kubernetes-release/release/v1.7.4/bin/linux/amd64/kubectl /usr/local/bin
WORKDIR /opt/zeppelin
ENTRYPOINT bin/zeppelin.sh
EOF
```

## Create / Start a Kubernetes cluster
In case of using Minikube on Linux with KVM:

`minikube start --vm-driver=kvm --cpus={nr_of_cpus} --memory={mem}`

You can check the Kubernetes dashboard address by running: `minikube dashboard`.

Init docker env: `eval $(minikube docker-env)`

## Build & tag Docker image

```
docker build -t zeppelin-server:v2.2.0-kubernetes -f Dockerfile .
```

You can retrieve the `imageid` by running docker images`

## Start ResourceStagingServer for spark-submit

Spark-submit will use ResourceStagingServer to distribute resources (in our case the Zeppelin Spark interpreter JAR) across Spark driver and executors.

```
wget https://github.com/apache-spark-on-k8s/spark/blob/branch-2.2-kubernetes/conf/kubernetes-resource-staging-server.yaml  
kubectl create -f kubernetes-resource-staging-server.yaml
```

## Create a Kubernetes service to reach Zeppelin server from outside the cluster

```
cat > zeppelin-service.yaml <<EOF
apiVersion: v1
kind: Service
metadata:
  name: zeppelin-k8-service
  labels:
	app: zeppelin-server
spec:
  ports:
  - port: 8080
	targetPort: 8080
  selector:
	app: zeppelin-server
  type: NodePort
EOF

kubectl create -f zeppelin-service.yaml

```

## Start Zeppelin server

```
cat > zeppelin-pod-local.yaml <<EOF
apiVersion: v1
kind: Pod
metadata:
  name: zeppelin-server
  labels:
	app: zeppelin-server
spec:
  containers:
  - name: zeppelin-server
	image: zeppelin-server:v2.2.0-kubernetes
	env:
	- name: SPARK_SUBMIT_OPTIONS
  	value: --kubernetes-namespace default
--conf spark.executor.instances=1
--conf spark.kubernetes.resourceStagingServer.uri=http://{RESOURCE_STAGING_SERVER_ADDRESS}:10000
--conf spark.kubernetes.resourceStagingServer.internal.uri=http://{RESOURCE_STAGING_SERVER_ADDRESS}:10000
--conf spark.kubernetes.driver.docker.image=kubespark/spark-driver:v2.2.0-kubernetes-0.5.0 --conf spark.kubernetes.executor.docker.image=kubespark/spark-executor:v2.2.0-kubernetes-0.5.0 --conf spark.kubernetes.initcontainer.docker.image=kubespark/spark-init:v2.2.0-kubernetes-0.5.0
	ports:
   	- containerPort: 8080
EOF
```

## Edit SPARK_SUBMIT_OPTIONS:

- Set RESOURCE_STAGING_SERVER_ADDRESS address retrieving either from K8 dashboard or running:

  `kubectl get svc spark-resource-staging-service -o jsonpath='{.spec.clusterIP}'`

## Start Zeppelin server:

`kubectl create -f zeppelin-pod-local.yaml`

You can retrieve Zeppelin server address either from K8 dashboard or using kubectl.
Zeppelin server should be reachable from outside of K8 cluster on K8 node address (same as in k8 master url KUBERNATES_NODE_ADDRESS) and nodePort property returned by running:

`kubectl get svc --selector=app=zeppelin-server -o jsonpath='{.items[0].spec.ports}'.`

## Edit spark interpreter settings
Set master url to point to your Kubernetes cluster: k8s://https://x.x.x.x:8443 or use default address which works inside a Kubernetes cluster:
k8s://https://kubernetes:443.
Add property 'spark.submit.deployMode' and set value to 'cluster'.


## Run ’Zeppelin Tutorial/Basic Features (Spark)’ notebook
In case of problems you can check for spark-submit output in Zeppelin logs after logging into zeppelin-server pod and restart Spark interpreter to try again.

`kubectl exec -it zeppelin-server bash`
Logs files are in  /opt/zeppelin/logs folder.
