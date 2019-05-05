---
layout: page
title: "Apache Hadoop Submarine Interpreter for Apache Zeppelin"
description: "Hadoop Submarine is the latest machine learning framework subproject in the Hadoop 3.1 release. It allows Hadoop to support Tensorflow, MXNet, Caffe, Spark, etc."
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

# Submarine Interpreter for Apache Zeppelin

<div id="toc"></div>

[Hadoop Submarine ](https://hadoop.apache.org/submarine/) is the latest machine learning framework subproject in the Hadoop 3.1 release. It allows Hadoop to support Tensorflow, MXNet, Caffe, Spark, etc. A variety of deep learning frameworks provide a full-featured system framework for machine learning algorithm development, distributed model training, model management, and model publishing, combined with hadoop's intrinsic data storage and data processing capabilities to enable data scientists to Good mining and the value of the data.

A deep learning algorithm project requires data acquisition, data processing, data cleaning, interactive visual programming adjustment parameters, algorithm testing, algorithm publishing, algorithm job scheduling, offline model training, model online services and many other processes and processes. Zeppelin is a web-based notebook that supports interactive data analysis. You can use SQL, Scala, Python, etc. to make data-driven, interactive, collaborative documents.

You can use the more than 20 interpreters in zeppelin (for example: spark, hive, Cassandra, Elasticsearch, Kylin, HBase, etc.) to collect data, clean data, feature extraction, etc. in the data in Hadoop before completing the machine learning model training. The data preprocessing process.

By integrating submarine in zeppelin, we use zeppelin's data discovery, data analysis and data visualization and collaboration capabilities to visualize the results of algorithm development and parameter adjustment during machine learning model training.

## Architecture

<img class="submarine-dashboard" src="{{BASE_PATH}}/assets/themes/zeppelin/img/docs-img/submarine-architecture.png" />

As shown in the figure above, how the Submarine develops and models the machine learning algorithms through Zeppelin is explained from the system architecture.

After installing and deploying Hadoop 3.1+ and Zeppelin, submarine will create a fully separate Zeppelin Submarine interpreter Docker container for each user in YARN. This container contains the development and runtime environment for Tensorflow. Zeppelin Server connects to the Zeppelin Submarine interpreter Docker container in YARN. allows algorithmic engineers to perform algorithm development and data visualization in Tensorflow's stand-alone environment in Zeppelin Notebook.

After the algorithm is developed, the algorithm engineer can submit the algorithm directly to the YARN in offline transfer training in Zeppelin, real-time demonstration of model training with Submarine's TensorBoard for each algorithm engineer.

You can not only complete the model training of the algorithm, but you can also use the more than twenty interpreters in Zeppelin. Complete the data preprocessing of the model, For example, you can perform data extraction, filtering, and feature extraction through the Spark interpreter in Zeppelin in the Algorithm Note.

In the future, you can also use Zeppelin's upcoming Workflow workflow orchestration service. You can complete Spark, Hive data processing and Tensorflow model training in one Note. It is organized into a workflow through visualization, etc., and the scheduling of jobs is performed in the production environment.

## Overview

<img class="submarine-dashboard" src="{{BASE_PATH}}/assets/themes/zeppelin/img/docs-img/submarine-interpreter.png" />

As shown in the figure above, from the internal implementation, how Submarine combines Zeppelin's machine learning algorithm development and model training.

1. The algorithm engineer created a Tensorflow notebook (left image) in Zeppelin by using Submarine interpreter.

   It is important to note that you need to complete the development of the entire algorithm in a Note.

2. You can use Spark for data preprocessing in some of the paragraphs in Note.

3. Use Python for algorithm development and debugging of Tensorflow in other paragraphs of notebook, Submarine creates a Zeppelin Submarine Interpreter Docker Container for you in YARN, which contains the following features and services:

   + **Shell Command line tool**：Allows you to view the system environment in the Zeppelin Submarine Interpreter Docker Container, Install the extension tools you need or the Python dependencies.
   + **Kerberos lib**：Allows you to perform kerberos authentication and access to Hadoop clusters with Kerberos authentication enabled.
   + **Tensorflow environment**：Allows you to develop tensorflow algorithm code.
   + **Python environment**：Allows you to develop tensorflow code.
   + Complete a complete algorithm development with a Note in Zeppelin. If this algorithm contains multiple modules, You can write different algorithm modules in multiple paragraphs in Note. The title of each paragraph is the name of the algorithm module. The content of the paragraph is the code content of this algorithm module.
   + **HDFS Client**：Zeppelin Submarine Interpreter will automatically submit the algorithm code you wrote in Note to HDFS.

   **Submarine interpreter Docker Image** It is Submarine that provides you with an image file that supports Tensorflow (CPU and GPU versions).
And installed the algorithm library commonly used by Python.
You can also install other development dependencies you need on top of the base image provided by Submarine.

4. When you complete the development of the algorithm module, You can do this by creating a new paragraph in Note and typing `%submarine dashboard`. Zeppelin will create a Submarine Dashboard. The machine learning algorithm written in this Note can be submitted to YARN as a JOB by selecting the `JOB RUN` command option in the Control Panel. Create a Tensorflow Model Training Docker Container, The container contains the following sections:

   + Tensorflow environment
   + HDFS Client Will automatically download the algorithm file Mount from HDFS into the container for distributed model training. Mount the algorithm file to the Work Dir path of the container.

   **Submarine Tensorflow Docker Image** There is Submarine that provides you with an image file that supports Tensorflow (CPU and GPU versions). And installed the algorithm library commonly used by Python. You can also install other development dependencies you need on top of the base image provided by Submarine.

<table class="table-configuration">
  <tr>
    <th>Name</th>
    <th>Class</th>
    <th>Description</th>
  </tr>
  <tr>
    <td>%submarine</td>
    <td>SubmarineInterpreter</td>
    <td>Provides interpreter for Apache Submarine dashboard</td>
  </tr>
  <tr>
    <td>%submarine.sh</td>
    <td>SubmarineShellInterpreter</td>
    <td>Provides interpreter for Apache Submarine shell</td>
  </tr>
  <tr>
    <td>%submarine.python</td>
    <td>PySubmarineInterpreter</td>
    <td>Provides interpreter for Apache Submarine python</td>
  </tr>
</table>

### Submarine shell

After creating a Note with Submarine Interpreter in Zeppelin, You can add a paragraph to Note if you need it. Using the %submarine.sh identifier, you can use the Shell command to perform various operations on the Submarine Interpreter Docker Container, such as:

1. View the Pythone version in the Container
2. View the system environment of the Container
3. Install the dependencies you need yourself
4. Kerberos certification with kinit
5. Use Hadoop in Container for HDFS operations, etc.

### Submarine python

You can add one or more paragraphs to Note. Write the algorithm module for Tensorflow in Python using the `%submarine.python` identifier.

### Submarine Dashboard

After writing the Tensorflow algorithm by using `%submarine.python`, You can add a paragraph to Note. Enter the %submarine dashboard and execute it. Zeppelin will create a Submarine Dashboard.

<img class="submarine-dashboard" src="{{BASE_PATH}}/assets/themes/zeppelin/img/docs-img/submarine-dashboard.gif" />

With Submarine Dashboard you can do all the operational control of Submarine, for example:

1. **Usage**：Display Submarine's command description to help developers locate problems.

2. **Refresh**：Zeppelin will erase all your input in the Dashboard.

3. **Tensorboard**：You will be redirected to the Tensorboard WEB system created by Submarine for each user. With Tensorboard you can view the real-time status of the Tensorflow model training in real time.

4. **Command**

   + **JOB RUN**：Selecting `JOB RUN` will display the parameter input interface for submitting JOB.


<table class="table-configuration">
  <tr>
    <th>Name</th>
    <th>Description</th>
  </tr>
  <tr>
    <td>Checkpoint Path/td>
    <td>Submarine sets up a separate Checkpoint path for each user's Note for Tensorflow training. Saved the training data for this Note history, Used to train the output of model data, Tensorboard uses the data in this path for model presentation. Users cannot modify it. For example: `hdfs://cluster1/...` , The environment variable name for Checkpoint Path is `%checkpoint_path%`, You can use `%checkpoint_path%` instead of the input value in Data Path in `PS Launch Cmd` and `Worker Launch Cmd`.</td>
  </tr>
  <tr>
    <td>Input Path</td>
    <td>The user specifies the data data directory of the Tensorflow algorithm. Only HDFS-enabled directories are supported. The environment variable name for Data Path is `%input_path%`, You can use `%input_path%` instead of the input value in Data Path in `PS Launch Cmd` and `Worker Launch Cmd`.</td>
  </tr>
  <tr>
    <td>PS Launch Cmd</td>
    <td>Tensorflow Parameter services launch command，例如：`python cifar10_main.py --data-dir=%input_path% --job-dir=%checkpoint_path% --num-gpus=0 ...`</td>
  </tr>
  <tr>
    <td>Worker Launch Cmd</td>
    <td>Tensorflow Worker services launch command，例如：`python cifar10_main.py --data-dir=%input_path% --job-dir=%checkpoint_path% --num-gpus=1 ...`</td>
  </tr>
</table>

   + **JOB STOP**

     You can choose to execute the `JOB STOP` command. Stop a Tensorflow model training task that has been submitted and is running

   + **TENSORBOARD START**

     You can choose to execute the `TENSORBOARD START` command to create your TENSORBOARD Docker Container.

   + **TENSORBOARD STOP**

     You can choose to execute the `TENSORBOARD STOP` command to stop and destroy your TENSORBOARD Docker Container.

5. **Run Command**：Execute the action command of your choice
6. **Clean Chechkpoint**：Checking this option will clear the data in this Note's Checkpoint Path before each `JOB RUN` execution.

### Configuration

Zeppelin Submarine interpreter provides the following properties to customize the Submarine interpreter

<table class="table-configuration">
  <tr>
    <th>Attribute name</th>
    <th>Attribute value</th>
    <th>Description</th>
  </tr>
  <tr>
    <td>DOCKER_CONTAINER_TIME_ZONE</td>
    <td>Etc/UTC</td>
    <td>Set the time zone in the container                           |
  </tr>
  <tr>
    <td>DOCKER_HADOOP_HDFS_HOME</td>
    <td>/hadoop-3.1-0</td>
    <td>Hadoop path in the following 3 images（SUBMARINE_INTERPRETER_DOCKER_IMAGE、tf.parameter.services.docker.image、tf.worker.services.docker.image） |
  </tr>
  <tr>
    <td>DOCKER_JAVA_HOME</td>
    <td>/opt/java</td>
    <td>JAVA path in the following 3 images（SUBMARINE_INTERPRETER_DOCKER_IMAGE、tf.parameter.services.docker.image、tf.worker.services.docker.image） |
  </tr>
  <tr>
    <td>HADOOP_YARN_SUBMARINE_JAR</td>
    <td></td>
    <td>Path to the Submarine JAR package in the Hadoop-3.1+ release installed on the Zeppelin server |
  </tr>
  <tr>
    <td>INTERPRETER_LAUNCH_MODE</td>
    <td>local/yarn</td>
    <td>Run the Submarine interpreter instance in local or YARN local mainly for submarine interpreter development and debugging YARN mode for production environment |
  </tr>
  <tr>
    <td>SUBMARINE_HADOOP_CONF_DIR</td>
    <td></td>
    <td>Set the HADOOP-CONF path to support multiple Hadoop cluster environments</td>
  </tr>
  <tr>
    <td>SUBMARINE_HADOOP_HOME</td>
    <td></td>
    <td>Hadoop-3.1+ above path installed on the Zeppelin server</td>
  </tr>
  <tr>
    <td>SUBMARINE_HADOOP_KEYTAB</td>
    <td></td>
    <td>Keytab file path for a hadoop cluster with kerberos authentication turned on</td>
  </tr>
  <tr>
    <td>SUBMARINE_HADOOP_PRINCIPAL</td>
    <td></td>
    <td>PRINCIPAL information for the keytab file of the hadoop cluster with kerberos authentication turned on</td>
  </tr>
  <tr>
    <td>SUBMARINE_INTERPRETER_DOCKER_IMAGE</td>
    <td></td>
    <td>At INTERPRETER_LAUNCH_MODE=yarn, Submarine uses this image to create a Zeppelin Submarine interpreter container to create an algorithm development environment for the user. |
  </tr>
  <tr>
    <td>docker.container.network</td>
    <td></td>
    <td>YARN's Docker network name</td>
  </tr>
  <tr>
    <td>machinelearing.distributed.enable</td>
    <td></td>
    <td>Whether to use the model training of the distributed mode JOB RUN submission</td>
  </tr>
  <tr>
    <td>shell.command.timeout.millisecs</td>
    <td>60000</td>
    <td>Execute timeout settings for shell commands in the Submarine interpreter container</td>
  </tr>
  <tr>
    <td>submarine.algorithm.hdfs.path</td>
    <td></td>
    <td>Save machine-based algorithms developed using Submarine interpreter to HDFS as files</td>
  </tr>
  <tr>
    <td>submarine.yarn.queue</td>
    <td>root.default</td>
    <td>Submarine submits model training YARN queue name</td>
  </tr>
  <tr>
    <td>tf.checkpoint.path</td>
    <td></td>
    <td>Tensorflow checkpoint path, Each user will create a user's checkpoint secondary path using the username under this path. Each algorithm submitted by the user will create a checkpoint three-level path using the note id (the user's Tensorboard uses the checkpoint data in this path for visual display)</td>
  </tr>
  <tr>
    <td>tf.parameter.services.cpu</td>
    <td></td>
    <td>Number of CPU cores applied to Tensorflow parameter services when Submarine submits model distributed training</td>
  </tr>
  <tr>
    <td>tf.parameter.services.docker.image</td>
    <td></td>
    <td>Submarine creates a mirror for Tensorflow parameter services when submitting model distributed training</td>
  </tr>
  <tr>
    <td>tf.parameter.services.gpu</td>
    <td></td>
    <td>GPU cores applied to Tensorflow parameter services when Submarine submits model distributed training</td>
  </tr>
  <tr>
    <td>tf.parameter.services.memory</td>
    <td>2G</td>
    <td>Memory resources requested by Tensorflow parameter services when Submarine submits model distributed training</td>
  </tr>
  <tr>
    <td>tf.parameter.services.num</td>
    <td></td>
    <td>Number of Tensorflow parameter services used by Submarine to submit model distributed training</td>
  </tr>
  <tr>
    <td>tf.tensorboard.enable</td>
    <td>true</td>
    <td>Create a separate Tensorboard for each user</td>
  </tr>
  <tr>
    <td>tf.worker.services.cpu</td>
    <td></td>
    <td>Submarine submits model resources for Tensorflow worker services when submitting model training</td>
  </tr>
  <tr>
    <td>tf.worker.services.docker.image</td>
    <td></td>
    <td>Submarine creates a mirror for Tensorflow worker services when submitting model distributed training</td>
  </tr>
  <tr>
    <td>tf.worker.services.gpu</td>
    <td></td>
    <td>Submarine submits GPU resources for Tensorflow worker services when submitting model training</td>
  </tr>
  <tr>
    <td>tf.worker.services.memory</td>
    <td></td>
    <td>Submarine submits model resources for Tensorflow worker services when submitting model training</td>
  </tr>
  <tr>
    <td>tf.worker.services.num</td>
    <td></td>
    <td>Number of Tensorflow worker services used by Submarine to submit model distributed training</td>
  </tr>
  <tr>
    <td>yarn.webapp.http.address</td>
    <td>http://hadoop:8088</td>
    <td>YARN web ui address</td>
  </tr>
  <tr>
    <td>zeppelin.interpreter.rpc.portRange</td>
    <td>29914</td>
    <td>You need to export this port in the SUBMARINE_INTERPRETER_DOCKER_IMAGE configuration image. RPC communication for Zeppelin Server and Submarine interpreter containers</td>
  </tr>
  <tr>
    <td>zeppelin.ipython.grpc.message_size</td>
    <td>33554432</td>
    <td>Message size setting for IPython grpc in Submarine interpreter container</td>
  </tr>
  <tr>
    <td>zeppelin.ipython.launch.timeout</td>
    <td>30000</td>
    <td>IPython execution timeout setting in Submarine interpreter container</td>
  </tr>
  <tr>
    <td>zeppelin.python</td>
    <td>python</td>
    <td>Execution path of python in Submarine interpreter container</td>
  </tr>
  <tr>
    <td>zeppelin.python.maxResult</td>
    <td>10000</td>
    <td>The maximum number of python execution results returned from the Submarine interpreter container</td>
  </tr>
  <tr>
    <td>zeppelin.python.useIPython</td>
    <td>false</td>
    <td>IPython is currently not supported and must be false</td>
  </tr>
  <tr>
    <td>zeppelin.submarine.auth.type</td>
    <td>simple/kerberos</td>
    <td>Has Hadoop turned on kerberos authentication?</td>
  </tr>
</table>

### Docker images

The docker images file is stored in the `zeppelin/scripts/docker/submarine` directory.

1. submarine interpreter cpu version

2. submarine interpreter gpu version

3. tensorflow 1.10 & hadoop 3.1.2 cpu version

4. tensorflow 1.10 & hadoop 3.1.2 gpu version


## Change Log

**0.1.0** _(Zeppelin 0.9.0)_ :

* Support distributed or standolone tensorflow model training.
* Support submarine interpreter running local.
* Support submarine interpreter running YARN.
* Support Docker on YARN-3.3.0, Plan compatible with lower versions of yarn.

## Bugs & Contacts

+ **Submarine interpreter BUG**
  If you encounter a bug for this interpreter, please create a sub **JIRA** ticket on [ZEPPELIN-3856](https://issues.apache.org/jira/browse/ZEPPELIN-3856).
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
**Youtube Submarine Channel**: https://www.youtube.com/channel/UC4JBt8Y8VJ0BW0IM9YpdCyQ