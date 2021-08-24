---
layout: page
title: "Apache Spark Interpreter for Apache Zeppelin"
description: "Apache Spark is a fast and general-purpose cluster computing system. It provides high-level APIs in Java, Scala, Python and R, and an optimized engine that supports general execution engine."
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

# Spark Interpreter for Apache Zeppelin

<div id="toc"></div>

## Overview
[Apache Spark](http://spark.apache.org) is a fast and general-purpose cluster computing system.
It provides high-level APIs in Java, Scala, Python and R, and an optimized engine that supports general execution graphs.
Apache Spark is supported in Zeppelin with Spark interpreter group which consists of following interpreters.

<table class="table-configuration">
  <tr>
    <th>Name</th>
    <th>Class</th>
    <th>Description</th>
  </tr>
  <tr>
    <td>%spark</td>
    <td>SparkInterpreter</td>
    <td>Creates a SparkContext/SparkSession and provides a Scala environment</td>
  </tr>
  <tr>
    <td>%spark.pyspark</td>
    <td>PySparkInterpreter</td>
    <td>Provides a Python environment</td>
  </tr>
  <tr>
    <td>%spark.ipyspark</td>
    <td>IPySparkInterpreter</td>
    <td>Provides a IPython environment</td>
  </tr>
  <tr>
    <td>%spark.r</td>
    <td>SparkRInterpreter</td>
    <td>Provides an vanilla R environment with SparkR support</td>
  </tr>
  <tr>
    <td>%spark.ir</td>
    <td>SparkIRInterpreter</td>
    <td>Provides an R environment with SparkR support based on Jupyter IRKernel</td>
  </tr>
  <tr>
    <td>%spark.shiny</td>
    <td>SparkShinyInterpreter</td>
    <td>Used to create R shiny app with SparkR support</td>
  </tr>
  <tr>
    <td>%spark.sql</td>
    <td>SparkSQLInterpreter</td>
    <td>Provides a SQL environment</td>
  </tr>
  <tr>
    <td>%spark.kotlin</td>
    <td>KotlinSparkInterpreter</td>
    <td>Provides a Kotlin environment</td>
  </tr>
</table>

## Main Features

<table class="table-configuration">
  <tr>
    <th>Feature</th>
    <th>Description</th>
  </tr>
  <tr>
    <td>Support multiple versions of Spark</td>
    <td>You can run different versions of Spark in one Zeppelin instance</td>
  </tr>
  <tr>
    <td>Support multiple versions of Scala</td>
    <td>You can run different Scala versions (2.10/2.11/2.12) of Spark in on Zeppelin instance</td>
  </tr>
  <tr>
    <td>Support multiple languages</td>
    <td>Scala, SQL, Python, R are supported, besides that you can also collaborate across languages, e.g. you can write Scala UDF and use it in PySpark</td>
  </tr>
  <tr>
    <td>Support multiple execution modes</td>
    <td>Local | Standalone | Yarn | K8s </td>
  </tr>
  <tr>
    <td>Interactive development</td>
    <td>Interactive development user experience increase your productivity</td>
  </tr>

  <tr>
    <td>Inline Visualization</td>
    <td>You can visualize Spark Dataset/DataFrame vis Python/R's plotting libraries, and even you can make SparkR Shiny app in Zeppelin</td>
  </tr>

  </tr>
    <td>Multi-tenancy</td>
    <td>Multiple user can work in one Zeppelin instance without affecting each other.</td>
  </tr>

  </tr>
    <td>Rest API Support</td>
    <td>You can not only submit Spark job via Zeppelin notebook UI, but also can do that via its rest api (You can use Zeppelin as Spark job server).</td>
  </tr>
</table>

## Play Spark in Zeppelin docker

For beginner, we would suggest you to play Spark in Zeppelin docker.
In the Zeppelin docker image, we have already installed
miniconda and lots of [useful python and R libraries](https://github.com/apache/zeppelin/blob/branch-0.10/scripts/docker/zeppelin/bin/env_python_3_with_R.yml)
including IPython and IRkernel prerequisites, so `%spark.pyspark` would use IPython and `%spark.ir` is enabled.
Without any extra configuration, you can run most of tutorial notes under folder `Spark Tutorial` directly.

First you need to download Spark, because there's no Spark binary distribution shipped with Zeppelin.
e.g. Here we download Spark 3.1.2 to`/mnt/disk1/spark-3.1.2`,
and we mount it to Zeppelin docker container and run the following command to start Zeppelin docker container.

```bash
docker run -u $(id -u) -p 8080:8080 -p 4040:4040 --rm -v /mnt/disk1/spark-3.1.2:/opt/spark -e SPARK_HOME=/opt/spark  --name zeppelin apache/zeppelin:0.10.0
```

After running the above command, you can open `http://localhost:8080` to play Spark in Zeppelin. We only verify the spark local mode in Zeppelin docker, other modes may not work due to network issues.
`-p 4040:4040` is to expose Spark web ui, so that you can access Spark web ui via `http://localhost:8081`.

## Configuration
The Spark interpreter can be configured with properties provided by Zeppelin.
You can also set other Spark properties which are not listed in the table. For a list of additional properties, refer to [Spark Available Properties](http://spark.apache.org/docs/latest/configuration.html#available-properties).
<table class="table-configuration">
  <tr>
    <th>Property</th>
    <th>Default</th>
    <th>Description</th>
  </tr>
  <tr>
    <td>`SPARK_HOME`</td>
    <td></td>
    <td>Location of spark distribution</td>
  <tr>
  <tr>
    <td>spark.master</td>
    <td>local[*]</td>
    <td>Spark master uri. <br/> e.g. spark://master_host:7077</td>
  <tr>
  <tr>
    <td>spark.submit.deployMode</td>
    <td></td>
    <td>The deploy mode of Spark driver program, either "client" or "cluster", Which means to launch driver program locally ("client") or remotely ("cluster") on one of the nodes inside the cluster.</td>
  <tr>
    <td>spark.app.name</td>
    <td>Zeppelin</td>
    <td>The name of spark application.</td>
  </tr>
  <tr>
    <td>spark.driver.cores</td>
    <td>1</td>
    <td>Number of cores to use for the driver process, only in cluster mode.</td>
  </tr>
  <tr>
    <td>spark.driver.memory</td>
    <td>1g</td>
    <td>Amount of memory to use for the driver process, i.e. where SparkContext is initialized, in the same format as JVM memory strings with a size unit suffix ("k", "m", "g" or "t") (e.g. 512m, 2g).</td>
  </tr>
  <tr>
    <td>spark.executor.cores</td>
    <td>1</td>
    <td>The number of cores to use on each executor</td>
  </tr>
  <tr>
    <td>spark.executor.memory</td>
    <td>1g</td>
    <td>Executor memory per worker instance. <br/> e.g. 512m, 32g</td>
  </tr>
  <tr>
    <td>spark.executor.instances</td>
    <td>2</td>
    <td>The number of executors for static allocation</td>
  </tr>
  <tr>
    <td>spark.files</td>
    <td></td>
    <td>Comma-separated list of files to be placed in the working directory of each executor. Globs are allowed.</td>
  </tr>
  <tr>
    <td>spark.jars</td>
    <td></td>
    <td>Comma-separated list of jars to include on the driver and executor classpaths. Globs are allowed.</td>
  </tr>
  <tr>
    <td>spark.jars.packages</td>
    <td></td>
    <td>Comma-separated list of Maven coordinates of jars to include on the driver and executor classpaths. The coordinates should be groupId:artifactId:version. If spark.jars.ivySettings is given artifacts will be resolved according to the configuration in the file, otherwise artifacts will be searched for in the local maven repo, then maven central and finally any additional remote repositories given by the command-line option --repositories.</td>
  </tr>
  <tr>
    <td>`PYSPARK_PYTHON`</td>
    <td>python</td>
    <td>Python binary executable to use for PySpark in both driver and executors (default is <code>python</code>).
            Property <code>spark.pyspark.python</code> take precedence if it is set</td>
  </tr>
  <tr>
    <td>`PYSPARK_DRIVER_PYTHON`</td>
    <td>python</td>
    <td>Python binary executable to use for PySpark in driver only (default is `PYSPARK_PYTHON`).
            Property <code>spark.pyspark.driver.python</code> take precedence if it is set</td>
  </tr>
  <tr>
    <td>zeppelin.pyspark.useIPython</td>
    <td>false</td>
    <td>Whether use IPython when the ipython prerequisites are met in `%spark.pyspark`</td>
  </tr>
  <tr>
    <td>zeppelin.R.cmd</td>
    <td>R</td>
    <td>R binary executable path.</td>
  </tr>  
  <tr>
    <td>zeppelin.spark.concurrentSQL</td>
    <td>false</td>
    <td>Execute multiple SQL concurrently if set true.</td>
  </tr>
  <tr>
    <td>zeppelin.spark.concurrentSQL.max</td>
    <td>10</td>
    <td>Max number of SQL concurrently executed</td>
  </tr>
  <tr>
    <td>zeppelin.spark.maxResult</td>
    <td>1000</td>
    <td>Max number rows of Spark SQL result to display.</td>
  </tr>
  <tr>
    <td>zeppelin.spark.run.asLoginUser</td>
    <td>true</td>
    <td>Whether run spark job as the zeppelin login user, it is only applied when running spark job in hadoop yarn cluster and shiro is enabled.</td>
  </tr>
  <tr>
    <td>zeppelin.spark.printREPLOutput</td>
    <td>true</td>
    <td>Print scala REPL output</td>
  </tr>
  <tr>
    <td>zeppelin.spark.useHiveContext</td>
    <td>true</td>
    <td>Use HiveContext instead of SQLContext if it is true. Enable hive for SparkSession</td>
  </tr>
  <tr>
    <td>zeppelin.spark.enableSupportedVersionCheck</td>
    <td>true</td>
    <td>Do not change - developer only setting, not for production use</td>
  </tr>
  <tr>
    <td>zeppelin.spark.sql.interpolation</td>
    <td>false</td>
    <td>Enable ZeppelinContext variable interpolation into spark sql</td>
  </tr>
  <tr>
  <td>zeppelin.spark.uiWebUrl</td>
    <td></td>
    <td>
      Overrides Spark UI default URL. Value should be a full URL (ex: http://{hostName}/{uniquePath}.
      In Kubernetes mode, value can be Jinja template string with 3 template variables PORT, {% raw %} SERVICE_NAME {% endraw %}  and  {% raw %} SERVICE_DOMAIN {% endraw %}.
      (e.g.: {% raw %}http://{{PORT}}-{{SERVICE_NAME}}.{{SERVICE_DOMAIN}} {% endraw %}). In yarn mode, value could be a knox url with {% raw %} {{applicationId}} {% endraw %} as placeholder,
      (e.g.: {% raw %}https://knox-server:8443/gateway/yarnui/yarn/proxy/{{applicationId}}/{% endraw %})
     </td>
  </tr>
  <tr>
    <td>spark.webui.yarn.useProxy</td>
    <td>false</td>
    <td>whether use yarn proxy url as Spark weburl, e.g. http://localhost:8088/proxy/application_1583396598068_0004</td>
  </tr>
  <tr>
    <td>spark.repl.target</td>
    <td>jvm-1.6</td>
    <td>
      Manually specifying the Java version of Spark Interpreter Scala REPL,Available options:<br/> 
      scala-compile v2.10.7 to v2.11.12 supports "jvm-1.5, jvm-1.6, jvm-1.7 and jvm-1.8", and the default value is jvm-1.6.<br/> 
      scala-compile v2.10.1 to v2.10.6 supports "jvm-1.5, jvm-1.6, jvm-1.7", and the default value is jvm-1.6.<br/> 
      scala-compile v2.12.x defaults to jvm-1.8, and only supports jvm-1.8.
    </td>
  </tr>
</table>

Without any configuration, Spark interpreter works out of box in local mode. But if you want to connect to your Spark cluster, you'll need to follow below two simple steps.

* Set SPARK_HOME
* Set master


### Set SPARK_HOME

There are several options for setting `SPARK_HOME`.

* Set `SPARK_HOME` in `zeppelin-env.sh`
* Set `SPARK_HOME` in interpreter setting page
* Set `SPARK_HOME` via [inline generic configuration](../usage/interpreter/overview.html#inline-generic-confinterpreter) 

#### Set `SPARK_HOME` in `zeppelin-env.sh`

If you work with only one version of Spark, then you can set `SPARK_HOME` in `zeppelin-env.sh` because any setting in `zeppelin-env.sh` is globally applied.

e.g. 

```bash
export SPARK_HOME=/usr/lib/spark
```

You can optionally set more environment variables in `zeppelin-env.sh`

```bash
# set hadoop conf dir
export HADOOP_CONF_DIR=/usr/lib/hadoop

```


#### Set `SPARK_HOME` in interpreter setting page

If you want to use multiple versions of Spark, then you need to create multiple Spark interpreters and set `SPARK_HOME` separately. e.g.
Create a new Spark interpreter `spark24` for Spark 2.4 and set its `SPARK_HOME` in interpreter setting page as following,
<center>
<img src="{{BASE_PATH}}/assets/themes/zeppelin/img/docs-img/spark_SPARK_HOME24.png" width="80%">
</center>

Create a new Spark interpreter `spark16` for Spark 1.6 and set its `SPARK_HOME` in interpreter setting page as following,
<center>
<img src="{{BASE_PATH}}/assets/themes/zeppelin/img/docs-img/spark_SPARK_HOME16.png" width="80%">
</center>


#### Set `SPARK_HOME` via [inline generic configuration](../usage/interpreter/overview.html#inline-generic-confinterpreter) 

Besides setting `SPARK_HOME` in interpreter setting page, you can also use inline generic configuration to put the 
configuration with code together for more flexibility. e.g.
<center>
<img src="{{BASE_PATH}}/assets/themes/zeppelin/img/docs-img/spark_inline_configuration.png" width="80%">
</center>

### Set master

After setting `SPARK_HOME`, you need to set **spark.master** property in either interpreter setting page or inline configuartion. The value may vary depending on your Spark cluster deployment type.

For example,

 * **local[*]** in local mode
 * **spark://master:7077** in standalone cluster
 * **yarn-client** in Yarn client mode  (Not supported in Spark 3.x, refer below for how to configure yarn-client in Spark 3.x)
 * **yarn-cluster** in Yarn cluster mode  (Not supported in Spark 3.x, refer below for how to configure yarn-cluster in Spark 3.x)
 * **mesos://host:5050** in Mesos cluster

That's it. Zeppelin will work with any version of Spark and any deployment type without rebuilding Zeppelin in this way.
For the further information about Spark & Zeppelin version compatibility, please refer to "Available Interpreters" section in [Zeppelin download page](https://zeppelin.apache.org/download.html).

Note that without setting `SPARK_HOME`, it's running in local mode with included version of Spark. The included version may vary depending on the build profile. And this included version Spark has limited function, so it 
is always recommended to set `SPARK_HOME`.

Yarn client mode and local mode will run driver in the same machine with zeppelin server, this would be dangerous for production. Because it may run out of memory when there's many Spark interpreters running at the same time. So we suggest you 
only allow yarn-cluster mode via setting `zeppelin.spark.only_yarn_cluster` in `zeppelin-site.xml`.

#### Configure yarn mode for Spark 3.x

Specifying `yarn-client` & `yarn-cluster` in `spark.master` is not supported in Spark 3.x any more, instead you need to use `spark.master` and `spark.submit.deployMode` together.

<table class="table-configuration">
  <tr>
    <th>Mode</th>
    <th>spark.master</th>
    <th>spark.submit.deployMode</th>
  </tr>
  <tr>
    <td>Yarn Client</td>
    <td>yarn</td>
    <td>client</td>
  </tr>
  <tr>
    <td>Yarn Cluster</td>
    <td>yarn</td>
    <td>cluster</td>
  </tr>  
</table>


## Interpreter binding mode

The default [interpreter binding mode](../usage/interpreter/interpreter_binding_mode.html) is `globally shared`. That means all notes share the same Spark interpreter.

So we recommend you to use `isolated per note` which means each note has own Spark interpreter without affecting each other. But it may run out of your machine resource if too many
Spark interpreters are created, so we recommend to always use yarn-cluster mode in production if you run Spark in hadoop cluster. And you can use [inline configuration](../usage/interpreter/overview.html#inline-generic-configuration) via `%spark.conf` in the first paragraph to customize your spark configuration.

You can also choose `scoped` mode. For `scoped` per note mode, Zeppelin creates separated scala compiler/python shell for each note but share a single `SparkContext/SqlContext/SparkSession`.


## SparkContext, SQLContext, SparkSession, ZeppelinContext

SparkContext, SQLContext, SparkSession (for spark 2.x, 3.x) and ZeppelinContext are automatically created and exposed as variable names `sc`, `sqlContext`, `spark` and `z` respectively, in Scala, Kotlin, Python and R environments.


> Note that Scala/Python/R environment shares the same SparkContext, SQLContext, SparkSession and ZeppelinContext instance.

## Yarn Mode

Zeppelin support both yarn client and yarn cluster mode (yarn cluster mode is supported from 0.8.0). For yarn mode, you must specify `SPARK_HOME` & `HADOOP_CONF_DIR`. 
Usually you only have one hadoop cluster, so you can set `HADOOP_CONF_DIR` in `zeppelin-env.sh` which is applied to all Spark interpreters. If you want to use spark against multiple hadoop cluster, then you need to define
`HADOOP_CONF_DIR` in interpreter setting or via inline generic configuration.

## K8s Mode

Regarding how to run Spark on K8s in Zeppelin, please check [this doc](../quickstart/kubernetes.html).


## PySpark

There are 2 ways to use PySpark in Zeppelin:

* Vanilla PySpark
* IPySpark

### Vanilla PySpark (Not Recommended)

Vanilla PySpark interpreter is almost the same as vanilla Python interpreter except Spark interpreter inject SparkContext, SQLContext, SparkSession via variables `sc`, `sqlContext`, `spark`.

By default, Zeppelin would use IPython in `%spark.pyspark` when IPython is available (Zeppelin would check whether ipython's prerequisites are met), Otherwise it would fall back to the vanilla PySpark implementation.

### IPySpark (Recommended)

You can use `IPySpark` explicitly via `%spark.ipyspark`. IPySpark interpreter is almost the same as IPython interpreter except Spark interpreter inject SparkContext, SQLContext, SparkSession via variables `sc`, `sqlContext`, `spark`.
For the IPython features, you can refer doc [Python Interpreter](python.html#ipython-interpreter-pythonipython-recommended)

## SparkR

Zeppelin support SparkR via `%spark.r`, `%spark.ir` and `%spark.shiny`. Here's configuration for SparkR Interpreter.

<table class="table-configuration">
  <tr>
    <th>Spark Property</th>
    <th>Default</th>
    <th>Description</th>
  </tr>
  <tr>
    <td>zeppelin.R.cmd</td>
    <td>R</td>
    <td>R binary executable path.</td>
  </tr>
  <tr>
    <td>zeppelin.R.knitr</td>
    <td>true</td>
    <td>Whether use knitr or not. (It is recommended to install knitr and use it in Zeppelin)</td>
  </tr>
  <tr>
    <td>zeppelin.R.image.width</td>
    <td>100%</td>
    <td>R plotting image width.</td>
  </tr>
  <tr>
    <td>zeppelin.R.render.options</td>
    <td>out.format = 'html', comment = NA, echo = FALSE, results = 'asis', message = F, warning = F, fig.retina = 2</td>
    <td>R plotting options.</td>
  </tr>
  <tr>
    <td>zeppelin.R.shiny.iframe_width</td>
    <td>100%</td>
    <td>IFrame width of Shiny App</td>
  </tr>
  <tr>
    <td>zeppelin.R.shiny.iframe_height</td>
    <td>500px</td>
    <td>IFrame height of Shiny App</td>
  </tr>
  <tr>
    <td>zeppelin.R.shiny.portRange</td>
    <td>:</td>
    <td>Shiny app would launch a web app at some port, this property is to specify the portRange via format '<start>:<end>', e.g. '5000:5001'. By default it is ':' which means any port</td>
  </tr>
</table>

Refer [R doc](r.html) for how to use R in Zeppelin.

## SparkSql

Spark sql interpreter share the same SparkContext/SparkSession with other Spark interpreters. That means any table registered in scala, python or r code can be accessed by Spark sql.
For examples:

```scala
%spark

case class People(name: String, age: Int)
var df = spark.createDataFrame(List(People("jeff", 23), People("andy", 20)))
df.createOrReplaceTempView("people")
```

```sql

%spark.sql

select * from people
```

You can write multiple sql statements in one paragraph. Each sql statement is separated by semicolon.
Sql statement in one paragraph would run sequentially. 
But sql statements in different paragraphs can run concurrently by the following configuration.

1. Set `zeppelin.spark.concurrentSQL` to true to enable the sql concurrent feature, underneath zeppelin will change to use fairscheduler for Spark. And also set `zeppelin.spark.concurrentSQL.max` to control the max number of sql statements running concurrently.
2. Configure pools by creating `fairscheduler.xml` under your `SPARK_CONF_DIR`, check the official spark doc [Configuring Pool Properties](http://spark.apache.org/docs/latest/job-scheduling.html#configuring-pool-properties)
3. Set pool property via setting paragraph local property. e.g.

 ```
 %spark(pool=pool1)

 sql statement
 ```

This pool feature is also available for all versions of scala Spark, PySpark. For SparkR, it is only available starting from 2.3.0.

## Dependency Management

For Spark interpreter, it is not recommended to use Zeppelin's [Dependency Management](../usage/interpreter/dependency_management.html) for managing
third party dependencies (`%spark.dep` is removed from Zeppelin 0.9 as well). Instead, you should set the standard Spark properties as following:

<table class="table-configuration">
  <tr>
    <th>Spark Property</th>
    <th>Spark Submit Argument</th>
    <th>Description</th>
  </tr>
  <tr>
    <td>spark.files</td>
    <td>--files</td>
    <td>Comma-separated list of files to be placed in the working directory of each executor. Globs are allowed.</td>
  </tr>
  <tr>
    <td>spark.jars</td>
    <td>--jars</td>
    <td>Comma-separated list of jars to include on the driver and executor classpaths. Globs are allowed.</td>
  </tr>
  <tr>
    <td>spark.jars.packages</td>
    <td>--packages</td>
    <td>Comma-separated list of Maven coordinates of jars to include on the driver and executor classpaths. The coordinates should be groupId:artifactId:version. If spark.jars.ivySettings is given artifacts will be resolved according to the configuration in the file, otherwise artifacts will be searched for in the local maven repo, then maven central and finally any additional remote repositories given by the command-line option --repositories.</td>
  </tr>
</table>

As general Spark properties, you can set them in via inline configuration or interpreter setting page or in `zeppelin-env.sh` via environment variable `SPARK_SUBMIT_OPTIONS`.
For examples:

```bash
export SPARK_SUBMIT_OPTIONS="--files <my_file> --jars <my_jar> --packages <my_package>"
```

To be noticed, `SPARK_SUBMIT_OPTIONS` is deprecated and will be removed in future release.


## ZeppelinContext

Zeppelin automatically injects `ZeppelinContext` as variable `z` in your Scala/Python environment. `ZeppelinContext` provides some additional functions and utilities.
See [Zeppelin-Context](../usage/other_features/zeppelin_context.html) for more details. For Spark interpreter, you can use z to display Spark `Dataset/Dataframe`.


<img src="{{BASE_PATH}}/assets/themes/zeppelin/img/docs-img/spark_zshow.png">


## Setting up Zeppelin with Kerberos

Logical setup with Zeppelin, Kerberos Key Distribution Center (KDC), and Spark on YARN:

<img src="{{BASE_PATH}}/assets/themes/zeppelin/img/docs-img/kdc_zeppelin.png">

There are several ways to make Spark work with kerberos enabled hadoop cluster in Zeppelin. 

1. Share one single hadoop cluster.
In this case you just need to specify `zeppelin.server.kerberos.keytab` and `zeppelin.server.kerberos.principal` in zeppelin-site.xml, Spark interpreter will use these setting by default.

2. Work with multiple hadoop clusters.
In this case you can specify `spark.yarn.keytab` and `spark.yarn.principal` to override `zeppelin.server.kerberos.keytab` and `zeppelin.server.kerberos.principal`.

### Configuration Setup

1. On the server that Zeppelin is installed, install Kerberos client modules and configuration, krb5.conf.
   This is to make the server communicate with KDC.

2. Add the two properties below to Spark configuration (`[SPARK_HOME]/conf/spark-defaults.conf`):

    ```
    spark.yarn.principal
    spark.yarn.keytab
    ```

> **NOTE:** If you do not have permission to access for the above spark-defaults.conf file, optionally, you can add the above lines to the Spark Interpreter setting through the Interpreter tab in the Zeppelin UI.

3. That's it. Play with Zeppelin!

## User Impersonation

In yarn mode, the user who launch the zeppelin server will be used to launch the Spark yarn application. This is not a good practise.
Most of time, you will enable shiro in Zeppelin and would like to use the login user to submit the Spark yarn app. For this purpose,
you need to enable user impersonation for more security control. In order the enable user impersonation, you need to do the following steps

**Step 1** Enable user impersonation setting hadoop's `core-site.xml`. E.g. if you are using user `zeppelin` to launch Zeppelin, then add the following to `core-site.xml`, then restart both hdfs and yarn. 

```xml
<property>
  <name>hadoop.proxyuser.zeppelin.groups</name>
  <value>*</value>
</property>
<property>
  <name>hadoop.proxyuser.zeppelin.hosts</name>
  <value>*</value>
</property>
```

**Step 2** Enable interpreter user impersonation in Spark interpreter's interpreter setting. (Enable shiro first of course)
<img src="{{BASE_PATH}}/assets/themes/zeppelin/img/docs-img/spark_user_impersonation.png">

**Step 3(Optional)** If you are using kerberos cluster, then you need to set `zeppelin.server.kerberos.keytab` and `zeppelin.server.kerberos.principal` to the user(aka. user in Step 1) you want to 
impersonate in `zeppelin-site.xml`.



## Deprecate Spark 2.2 and earlier versions
Starting from 0.9, Zeppelin deprecate Spark 2.2 and earlier versions. So you will see a warning message when you use Spark 2.2 and earlier.
You can get rid of this message by setting `zeppelin.spark.deprecatedMsg.show` to `false`.

<img src="{{BASE_PATH}}/assets/themes/zeppelin/img/docs-img/spark_deprecate.png">


## Community

[Join our community](http://zeppelin.apache.org/community.html) to discuss with others.
