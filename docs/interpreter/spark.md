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
    <td>Creates a SparkContext and provides a Scala environment</td>
  </tr>
  <tr>
    <td>%spark.kotlin</td>
    <td>KotlinSparkInterpreter</td>
    <td>Provides a Kotlin environment</td>
  </tr>
  <tr>
    <td>%spark.pyspark</td>
    <td>PySparkInterpreter</td>
    <td>Provides a Python environment</td>
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
    <td>%spark.dep</td>
    <td>DepInterpreter</td>
    <td>Dependency loader</td>
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
    <td>args</td>
    <td></td>
    <td>Spark commandline args</td>
  </tr>
    <td>master</td>
    <td>local[*]</td>
    <td>Spark master uri. <br/> ex) spark://masterhost:7077</td>
  <tr>
    <td>spark.app.name</td>
    <td>Zeppelin</td>
    <td>The name of spark application.</td>
  </tr>
  <tr>
    <td>spark.cores.max</td>
    <td></td>
    <td>Total number of cores to use. <br/> Empty value uses all available core.</td>
  </tr>
  <tr>
    <td>spark.executor.memory </td>
    <td>1g</td>
    <td>Executor memory per worker instance. <br/> ex) 512m, 32g</td>
  </tr>
  <tr>
    <td>zeppelin.dep.additionalRemoteRepository</td>
    <td>spark-packages, <br/> http://dl.bintray.com/spark-packages/maven, <br/> false;</td>
    <td>A list of `id,remote-repository-URL,is-snapshot;` <br/> for each remote repository.</td>
  </tr>
  <tr>
    <td>zeppelin.dep.localrepo</td>
    <td>local-repo</td>
    <td>Local repository for dependency loader</td>
  </tr>
  <tr>
    <td>`PYSPARK_PYTHON`</td>
    <td>python</td>
    <td>Python binary executable to use for PySpark in both driver and workers (default is <code>python</code>).
            Property <code>spark.pyspark.python</code> take precedence if it is set</td>
  </tr>
  <tr>
    <td>`PYSPARK_DRIVER_PYTHON`</td>
    <td>python</td>
    <td>Python binary executable to use for PySpark in driver only (default is `PYSPARK_PYTHON`).
            Property <code>spark.pyspark.driver.python</code> take precedence if it is set</td>
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
    <td>Max number of Spark SQL result to display.</td>
  </tr>
  <tr>
    <td>zeppelin.spark.printREPLOutput</td>
    <td>true</td>
    <td>Print REPL output</td>
  </tr>
  <tr>
    <td>zeppelin.spark.useHiveContext</td>
    <td>true</td>
    <td>Use HiveContext instead of SQLContext if it is true.</td>
  </tr>
  <tr>
    <td>zeppelin.spark.importImplicit</td>
    <td>true</td>
    <td>Import implicits, UDF collection, and sql if set true.</td>
  </tr>
  <tr>
    <td>zeppelin.spark.enableSupportedVersionCheck</td>
    <td>true</td>
    <td>Do not change - developer only setting, not for production use</td>
  </tr>
  <tr>
    <td>zeppelin.spark.sql.interpolation</td>
    <td>false</td>
    <td>Enable ZeppelinContext variable interpolation into paragraph text</td>
  </tr>
  <tr>
  <td>zeppelin.spark.uiWebUrl</td>
    <td></td>
    <td>Overrides Spark UI default URL. Value should be a full URL (ex: http://{hostName}/{uniquePath}</td>
  </tr>
  <td>zeppelin.spark.scala.color</td>
    <td>true</td>
    <td>Whether to enable color output of spark scala interpreter</td>
  </tr>
</table>

Without any configuration, Spark interpreter works out of box in local mode. But if you want to connect to your Spark cluster, you'll need to follow below two simple steps.

### 1. Export SPARK_HOME
In `conf/zeppelin-env.sh`, export `SPARK_HOME` environment variable with your Spark installation path.

For example,

```bash
export SPARK_HOME=/usr/lib/spark
```

You can optionally set more environment variables

```bash
# set hadoop conf dir
export HADOOP_CONF_DIR=/usr/lib/hadoop

# set options to pass spark-submit command
export SPARK_SUBMIT_OPTIONS="--packages com.databricks:spark-csv_2.10:1.2.0"

# extra classpath. e.g. set classpath for hive-site.xml
export ZEPPELIN_INTP_CLASSPATH_OVERRIDES=/etc/hive/conf
```

For Windows, ensure you have `winutils.exe` in `%HADOOP_HOME%\bin`. Please see [Problems running Hadoop on Windows](https://wiki.apache.org/hadoop/WindowsProblems) for the details.

### 2. Set master in Interpreter menu
After start Zeppelin, go to **Interpreter** menu and edit **master** property in your Spark interpreter setting. The value may vary depending on your Spark cluster deployment type.

For example,

 * **local[*]** in local mode
 * **spark://master:7077** in standalone cluster
 * **yarn-client** in Yarn client mode
 * **yarn-cluster** in Yarn cluster mode
 * **mesos://host:5050** in Mesos cluster

That's it. Zeppelin will work with any version of Spark and any deployment type without rebuilding Zeppelin in this way.
For the further information about Spark & Zeppelin version compatibility, please refer to "Available Interpreters" section in [Zeppelin download page](https://zeppelin.apache.org/download.html).

> Note that without exporting `SPARK_HOME`, it's running in local mode with included version of Spark. The included version may vary depending on the build profile.

### 3. Yarn mode
Zeppelin support both yarn client and yarn cluster mode (yarn cluster mode is supported from 0.8.0). For yarn mode, you must specify `SPARK_HOME` & `HADOOP_CONF_DIR`.
You can either specify them in `zeppelin-env.sh`, or in interpreter setting page. Specifying them in `zeppelin-env.sh` means you can use only one version of `spark` & `hadoop`. Specifying them
in interpreter setting page means you can use multiple versions of `spark` & `hadoop` in one zeppelin instance.

### 4. New Version of SparkInterpreter
Starting from 0.9, we totally removed the old spark interpreter implementation, and make the new spark interpreter as the official spark interpreter.

## SparkContext, SQLContext, SparkSession, ZeppelinContext
SparkContext, SQLContext and ZeppelinContext are automatically created and exposed as variable names `sc`, `sqlContext` and `z`, respectively, in Scala, Kotlin, Python and R environments.
Staring from 0.6.1 SparkSession is available as variable `spark` when you are using Spark 2.x.

> Note that Scala/Python/R environment shares the same SparkContext, SQLContext and ZeppelinContext instance.

<a name="dependencyloading"> </a>

### How to pass property to SparkConf

There're 2 kinds of properties that would be passed to SparkConf

 * Standard spark property (prefix with `spark.`). e.g. `spark.executor.memory` will be passed to `SparkConf`
 * Non-standard spark property (prefix with `zeppelin.spark.`).  e.g. `zeppelin.spark.property_1`, `property_1` will be passed to `SparkConf`

## Dependency Management

For spark interpreter, you should not use Zeppelin's [Dependency Management](../usage/interpreter/dependency_management.html) for managing 
third party dependencies, (`%spark.dep` also is not the recommended approach starting from Zeppelin 0.8). Instead you should set spark properties (`spark.jars`, `spark.files`, `spark.jars.packages`) in 2 ways.

<table class="table-configuration">
  <tr>
    <th>spark-defaults.conf</th>
    <th>SPARK_SUBMIT_OPTIONS</th>
    <th>Description</th>
  </tr>
  <tr>
    <td>spark.jars</td>
    <td>--jars</td>
    <td>Comma-separated list of local jars to include on the driver and executor classpaths.</td>
  </tr>
  <tr>
    <td>spark.jars.packages</td>
    <td>--packages</td>
    <td>Comma-separated list of maven coordinates of jars to include on the driver and executor classpaths. Will search the local maven repo, then maven central and any additional remote repositories given by --repositories. The format for the coordinates should be <code>groupId:artifactId:version</code>.</td>
  </tr>
  <tr>
    <td>spark.files</td>
    <td>--files</td>
    <td>Comma-separated list of files to be placed in the working directory of each executor.</td>
  </tr>
</table>

### 1. Set spark properties in zeppelin side.

In zeppelin side, you can either set them in spark interpreter setting page or via [Generic ConfInterpreter](../usage/interpreter/overview.html).
It is not recommended to set them in `SPARK_SUBMIT_OPTIONS`. Because it will be shared by all spark interpreters, you can not set different dependencies for different users.

### 2. Set spark properties in spark side.

In spark side, you can set them in `spark-defaults.conf`.

e.g.

  ```
    spark.jars        /path/mylib1.jar,/path/mylib2.jar
    spark.jars.packages   com.databricks:spark-csv_2.10:1.2.0
    spark.files       /path/mylib1.py,/path/mylib2.egg,/path/mylib3.zip
  ```


## ZeppelinContext
Zeppelin automatically injects `ZeppelinContext` as variable `z` in your Scala/Python environment. `ZeppelinContext` provides some additional functions and utilities.
See [Zeppelin-Context](../usage/other_features/zeppelin_context.html) for more details.

## Matplotlib Integration (pyspark)
Both the `python` and `pyspark` interpreters have built-in support for inline visualization using `matplotlib`,
a popular plotting library for python. More details can be found in the [python interpreter documentation](../interpreter/python.html),
since matplotlib support is identical. More advanced interactive plotting can be done with pyspark through
utilizing Zeppelin's built-in [Angular Display System](../usage/display_system/angular_backend.html), as shown below:

<img class="img-responsive" src="{{BASE_PATH}}/assets/themes/zeppelin/img/docs-img/matplotlibAngularExample.gif" />

## Running spark sql concurrently
By default, each sql statement would run sequentially in `%spark.sql`. But you can run them concurrently by following setup.

1. set `zeppelin.spark.concurrentSQL` to true to enable the sql concurrent feature, underneath zeppelin will change to use fairscheduler for spark. And also set `zeppelin.spark.concurrentSQL.max` to control the max number of sql statements running concurrently.
2. configure pools by creating `fairscheduler.xml` under your `SPARK_CONF_DIR`, check the offical spark doc [Configuring Pool Properties](http://spark.apache.org/docs/latest/job-scheduling.html#configuring-pool-properties)
3. set pool property via setting paragraph property. e.g.

```
%spark(pool=pool1)

sql statement
```

This feature is available for both all versions of scala spark, pyspark. For sparkr, it is only available starting from 2.3.0.
 
## Interpreter setting option

You can choose one of `shared`, `scoped` and `isolated` options wheh you configure Spark interpreter.
Spark interpreter creates separated Scala compiler per each notebook but share a single SparkContext in `scoped` mode (experimental).
It creates separated SparkContext per each notebook in `isolated` mode.

## IPython support

By default, zeppelin would use IPython in `pyspark` when IPython is available, Otherwise it would fall back to the original PySpark implementation.
If you don't want to use IPython, then you can set `zeppelin.pyspark.useIPython` as `false` in interpreter setting. For the IPython features, you can refer doc
[Python Interpreter](python.html)


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

2. Set `SPARK_HOME` in `[ZEPPELIN_HOME]/conf/zeppelin-env.sh` to use spark-submit
(Additionally, you might have to set `export HADOOP_CONF_DIR=/etc/hadoop/conf`)

3. Add the two properties below to Spark configuration (`[SPARK_HOME]/conf/spark-defaults.conf`):

    ```
    spark.yarn.principal
    spark.yarn.keytab
    ```

  > **NOTE:** If you do not have permission to access for the above spark-defaults.conf file, optionally, you can add the above lines to the Spark Interpreter setting through the Interpreter tab in the Zeppelin UI.

4. That's it. Play with Zeppelin!

