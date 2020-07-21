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
Apache Spark is supported in Zeppelin with Spark interpreter group which consists of below six interpreters.

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
    <td>Provides an R environment with SparkR support</td>
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
      In Kubernetes mode, value can be Jinja template string with 3 template variables 'PORT', 'SERVICE_NAME' and 'SERVICE_DOMAIN'.
      (ex: http://{{PORT}}-{{SERVICE_NAME}}.{{SERVICE_DOMAIN}})
     </td>
  </tr>
  <td>spark.webui.yarn.useProxy</td>
    <td>false</td>
    <td>whether use yarn proxy url as spark weburl, e.g. http://localhost:8088/proxy/application_1583396598068_0004</td>
  </tr>
</table>

Without any configuration, Spark interpreter works out of box in local mode. But if you want to connect to your Spark cluster, you'll need to follow below two simple steps.

### Export SPARK_HOME

There are several options for setting `SPARK_HOME`.

* Set `SPARK_HOME` in `zeppelin-env.sh`
* Set `SPARK_HOME` in Interpreter setting page
* Set `SPARK_HOME` via [inline generic configuration](../usage/interpreter/overview.html#inline-generic-confinterpreter) 

#### 1. Set `SPARK_HOME` in `zeppelin-env.sh`

If you work with only one version of spark, then you can set `SPARK_HOME` in `zeppelin-env.sh` because any setting in `zeppelin-env.sh` is globally applied.

e.g. 

```bash
export SPARK_HOME=/usr/lib/spark
```

You can optionally set more environment variables in `zeppelin-env.sh`

```bash
# set hadoop conf dir
export HADOOP_CONF_DIR=/usr/lib/hadoop

```


#### 2. Set `SPARK_HOME` in Interpreter setting page

If you want to use multiple versions of spark, then you need create multiple spark interpreters and set `SPARK_HOME` for each of them. e.g.
Create a new spark interpreter `spark24` for spark 2.4 and set `SPARK_HOME` in interpreter setting page
<center>
<img src="{{BASE_PATH}}/assets/themes/zeppelin/img/docs-img/spark_SPARK_HOME24.png" width="80%">
</center>

Create a new spark interpreter `spark16` for spark 1.6 and set `SPARK_HOME` in interpreter setting page
<center>
<img src="{{BASE_PATH}}/assets/themes/zeppelin/img/docs-img/spark_SPARK_HOME16.png" width="80%">
</center>


#### 3. Set `SPARK_HOME` via [inline generic configuration](../usage/interpreter/overview.html#inline-generic-confinterpreter) 

Besides setting `SPARK_HOME` in interpreter setting page, you can also use inline generic configuration to put the 
configuration with code together for more flexibility. e.g.
<center>
<img src="{{BASE_PATH}}/assets/themes/zeppelin/img/docs-img/spark_inline_configuration.png" width="80%">
</center>

### Set master in Interpreter menu
After starting Zeppelin, go to **Interpreter** menu and edit **spark.master** property in your Spark interpreter setting. The value may vary depending on your Spark cluster deployment type.

For example,

 * **local[*]** in local mode
 * **spark://master:7077** in standalone cluster
 * **yarn-client** in Yarn client mode  (Not supported in spark 3.x, refer below for how to configure yarn-client in Spark 3.x)
 * **yarn-cluster** in Yarn cluster mode  (Not supported in spark 3.x, refer below for how to configure yarn-client in Spark 3.x)
 * **mesos://host:5050** in Mesos cluster

That's it. Zeppelin will work with any version of Spark and any deployment type without rebuilding Zeppelin in this way.
For the further information about Spark & Zeppelin version compatibility, please refer to "Available Interpreters" section in [Zeppelin download page](https://zeppelin.apache.org/download.html).

> Note that without exporting `SPARK_HOME`, it's running in local mode with included version of Spark. The included version may vary depending on the build profile.

> Yarn client mode and local mode will run driver in the same machine with zeppelin server, this would be dangerous for production. Because it may run out of memory when there's many spark interpreters running at the same time. So we suggest you only allow yarn-cluster mode via setting `zeppelin.spark.only_yarn_cluster` in `zeppelin-site.xml`.

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


## SparkContext, SQLContext, SparkSession, ZeppelinContext

SparkContext, SQLContext, SparkSession (for spark 2.x) and ZeppelinContext are automatically created and exposed as variable names `sc`, `sqlContext`, `spark` and `z`, respectively, in Scala, Kotlin, Python and R environments.


> Note that Scala/Python/R environment shares the same SparkContext, SQLContext, SparkSession and ZeppelinContext instance.

## YARN Mode
Zeppelin support both yarn client and yarn cluster mode (yarn cluster mode is supported from 0.8.0). For yarn mode, you must specify `SPARK_HOME` & `HADOOP_CONF_DIR`. 
Usually you only have one hadoop cluster, so you can set `HADOOP_CONF_DIR` in `zeppelin-env.sh` which is applied to all spark interpreters. If you want to use spark against multiple hadoop cluster, then you need to define
`HADOOP_CONF_DIR` in interpreter setting or via inline generic configuration.

## Dependency Management

For spark interpreter, it is not recommended to use Zeppelin's [Dependency Management](../usage/interpreter/dependency_management.html) for managing 
third party dependencies (`%spark.dep` is removed from Zeppelin 0.9 as well). Instead you should set the standard Spark properties.

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

You can either set Spark properties in interpreter setting page or set Spark submit arguments in `zeppelin-env.sh` via environment variable `SPARK_SUBMIT_OPTIONS`. 
For examples:

```bash
export SPARK_SUBMIT_OPTIONS="--files <my_file> --jars <my_jar> --packages <my_package>"
```

But it is not recommended to set them in `SPARK_SUBMIT_OPTIONS`. Because it will be shared by all spark interpreters, which means you can not set different dependencies for different users.


## PySpark

There're 2 ways to use PySpark in Zeppelin:

* Vanilla PySpark
* IPySpark

### Vanilla PySpark (Not Recommended)
Vanilla PySpark interpreter is almost the same as vanilla Python interpreter except Zeppelin inject SparkContext, SQLContext, SparkSession via variables `sc`, `sqlContext`, `spark`.

By default, Zeppelin would use IPython in `%spark.pyspark` when IPython is available, Otherwise it would fall back to the original PySpark implementation.
If you don't want to use IPython, then you can set `zeppelin.pyspark.useIPython` as `false` in interpreter setting. For the IPython features, you can refer doc
[Python Interpreter](python.html)

### IPySpark (Recommended)
You can use `IPySpark` explicitly via `%spark.ipyspark`. IPySpark interpreter is almost the same as IPython interpreter except Zeppelin inject SparkContext, SQLContext, SparkSession via variables `sc`, `sqlContext`, `spark`.
For the IPython features, you can refer doc [Python Interpreter](python.html)

## SparkR

Zeppelin support SparkR via `%spark.r`. Here's configuration for SparkR Interpreter.

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
</table>


## SparkSql

Spark Sql Interpreter share the same SparkContext/SparkSession with other Spark interpreter. That means any table registered in scala, python or r code can be accessed by Spark Sql.
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

By default, each sql statement would run sequentially in `%spark.sql`. But you can run them concurrently by following setup.

1. Set `zeppelin.spark.concurrentSQL` to true to enable the sql concurrent feature, underneath zeppelin will change to use fairscheduler for spark. And also set `zeppelin.spark.concurrentSQL.max` to control the max number of sql statements running concurrently.
2. Configure pools by creating `fairscheduler.xml` under your `SPARK_CONF_DIR`, check the official spark doc [Configuring Pool Properties](http://spark.apache.org/docs/latest/job-scheduling.html#configuring-pool-properties)
3. Set pool property via setting paragraph property. e.g.

```
%spark(pool=pool1)

sql statement
```

This pool feature is also available for all versions of scala Spark, PySpark. For SparkR, it is only available starting from 2.3.0.
 
## Interpreter Setting Option

You can choose one of `shared`, `scoped` and `isolated` options when you configure Spark interpreter.
e.g. 

* In `scoped` per user mode, Zeppelin creates separated Scala compiler for each user but share a single SparkContext.
* In `isolated` per user mode, Zeppelin creates separated SparkContext for each user.

## ZeppelinContext
Zeppelin automatically injects `ZeppelinContext` as variable `z` in your Scala/Python environment. `ZeppelinContext` provides some additional functions and utilities.
See [Zeppelin-Context](../usage/other_features/zeppelin_context.html) for more details.

## User Impersonation

In yarn mode, the user who launch the zeppelin server will be used to launch the spark yarn application. This is not a good practise.
Most of time, you will enable shiro in Zeppelin and would like to use the login user to submit the spark yarn app. For this purpose,
you need to enable user impersonation for more security control. In order the enable user impersonation, you need to do the following steps

**Step 1** Enable user impersonation setting hadoop's `core-site.xml`. E.g. if you are using user `zeppelin` to launch Zeppelin, then add the following to `core-site.xml`, then restart both hdfs and yarn. 

```
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


## Setting up Zeppelin with Kerberos
Logical setup with Zeppelin, Kerberos Key Distribution Center (KDC), and Spark on YARN:

<img src="{{BASE_PATH}}/assets/themes/zeppelin/img/docs-img/kdc_zeppelin.png">

## Deprecate Spark 2.2 and earlier versions
Starting from 0.9, Zeppelin deprecate Spark 2.2 and earlier versions. So you will see a warning message when you use Spark 2.2 and earlier.
You can get rid of this message by setting `zeppelin.spark.deprecatedMsg.show` to `false`.

<img src="{{BASE_PATH}}/assets/themes/zeppelin/img/docs-img/spark_deprecate.png">

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

