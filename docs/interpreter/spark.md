---
layout: page
title: "Spark Interpreter Group"
description: ""
group: manual
---
{% include JB/setup %}


## Spark Interpreter

[Apache Spark](http://spark.apache.org) is supported in Zeppelin with 
Spark Interpreter group, which consisted of 4 interpreters.

<table class="table-configuration">
  <tr>
    <th>Name</th>
    <th>Class</th>
    <th>Description</th>
  </tr>
  <tr>
    <td>%spark</td>
    <td>SparkInterpreter</td>
    <td>Creates SparkContext and provides scala environment</td>
  </tr>
  <tr>
    <td>%pyspark</td>
    <td>PySparkInterpreter</td>
    <td>Provides python environment</td>
  </tr>
  <tr>
    <td>%sql</td>
    <td>SparkSQLInterpreter</td>
    <td>Provides SQL environment</td>
  </tr>
  <tr>
    <td>%dep</td>
    <td>DepInterpreter</td>
    <td>Dependency loader</td>
  </tr>
</table>


<br /><br />

### Configuration
<hr />

Without any configuration, Spark interpreter works out of box in local mode. But if you want to connect to your Spark cluster, you'll need following two simple steps.


#### 1. export SPARK_HOME

In **conf/zeppelin-env.sh**, export SPARK_HOME environment variable with your Spark installation path.

for example

```bash
export SPARK_HOME=/usr/lib/spark
```

You can optionally export HADOOP\_CONF\_DIR and SPARK\_SUBMIT\_OPTIONS

```bash
export HADOOP_CONF_DIR=/usr/lib/hadoop
export SPARK_SUBMIT_OPTIONS="--packages com.databricks:spark-csv_2.10:1.2.0"
```


<br />
#### 2. set master in Interpreter menu.

After start Zeppelin, go to **Interpreter** menu and edit **master** property in your Spark interpreter setting. The value may vary depending on your Spark cluster deployment type.

for example,

 * **local[*]** in local mode
 * **spark://master:7077** in standalone cluster
 * **yarn-client** in Yarn client mode
 * **mesos://host:5050** in Mesos cluster



<br />
That's it. Zeppelin will work with any version of Spark and any deployment type without rebuild Zeppelin in this way. (Zeppelin 0.5.5-incubating release works up to Spark 1.5.1)

Note that without exporting SPARK_HOME, it's running in local mode with included version of Spark. The included version may vary depending on the build profile.

<br /> <br />
### SparkContext, SQLContext, ZeppelinContext
<hr />

SparkContext, SQLContext, ZeppelinContext are automatically created and exposed as variable names 'sc', 'sqlContext' and 'z', respectively, both in scala and python environments.

Note that scala / python environment shares the same SparkContext, SQLContext, ZeppelinContext instance.


<a name="dependencyloading"> </a>
<br />
<br />
### Dependency Management
<hr />
There are two ways to load external library in spark interpreter. First is using Zeppelin's %dep interpreter and second is loading Spark properties.

#### 1. Dynamic Dependency Loading via %dep interpreter

When your code requires external library, instead of doing download/copy/restart Zeppelin, you can easily do following jobs using %dep interpreter.

 * Load libraries recursively from Maven repository
 * Load libraries from local filesystem
 * Add additional maven repository
 * Automatically add libraries to SparkCluster (You can turn off)

Dep interpreter leverages scala environment. So you can write any Scala code here.
Note that %dep interpreter should be used before %spark, %pyspark, %sql.

Here's usages.

```scala
%dep
z.reset() // clean up previously added artifact and repository

// add maven repository
z.addRepo("RepoName").url("RepoURL")

// add maven snapshot repository
z.addRepo("RepoName").url("RepoURL").snapshot()

// add credentials for private maven repository
z.addRepo("RepoName").url("RepoURL").username("username").password("password")

// add artifact from filesystem
z.load("/path/to.jar")

// add artifact from maven repository, with no dependency
z.load("groupId:artifactId:version").excludeAll()

// add artifact recursively
z.load("groupId:artifactId:version")

// add artifact recursively except comma separated GroupID:ArtifactId list
z.load("groupId:artifactId:version").exclude("groupId:artifactId,groupId:artifactId, ...")

// exclude with pattern
z.load("groupId:artifactId:version").exclude(*)
z.load("groupId:artifactId:version").exclude("groupId:artifactId:*")
z.load("groupId:artifactId:version").exclude("groupId:*")

// local() skips adding artifact to spark clusters (skipping sc.addJar())
z.load("groupId:artifactId:version").local()
```


<br />
#### 2. Loading Spark Properties
Once `SPARK_HOME` is set in `conf/zeppelin-env.sh`, Zeppelin uses `spark-submit` as spark interpreter runner. `spark-submit` supports two ways to load configurations. The first is command line options such as --master and Zeppelin can pass these options to `spark-submit` by exporting `SPARK_SUBMIT_OPTIONS` in conf/zeppelin-env.sh. Second is reading configuration options from `SPARK_HOME/conf/spark-defaults.conf`. Spark properites that user can set to distribute libraries are:

<table class="table-configuration">
  <tr>
    <th>spark-defaults.conf</th>
    <th>SPARK_SUBMIT_OPTIONS</th>
    <th>Applicable Interpreter</th>
    <th>Description</th>
  </tr>
  <tr>
    <td>spark.jars</td>
    <td>--jars</td>
    <td>%spark</td>
    <td>Comma-separated list of local jars to include on the driver and executor classpaths.</td>
  </tr>
  <tr>
    <td>spark.jars.packages</td>
    <td>--packages</td>
    <td>%spark</td>
    <td>Comma-separated list of maven coordinates of jars to include on the driver and executor classpaths. Will search the local maven repo, then maven central and any additional remote repositories given by --repositories. The format for the coordinates should be groupId:artifactId:version.</td>
  </tr>
  <tr>
    <td>spark.files</td>
    <td>--files</td>
    <td>%pyspark</td>
    <td>Comma-separated list of files to be placed in the working directory of each executor.</td>
  </tr>
</table>
Note that adding jar to pyspark is only availabe via %dep interpreter at the moment

<br/>
Here are few examples:

* SPARK\_SUBMIT\_OPTIONS in conf/zeppelin-env.sh

		export SPARK_SUBMIT_OPTIONS="--packages com.databricks:spark-csv_2.10:1.2.0 --jars /path/mylib1.jar,/path/mylib2.jar --files /path/mylib1.py,/path/mylib2.zip,/path/mylib3.egg"

* SPARK_HOME/conf/spark-defaults.conf

		spark.jars				/path/mylib1.jar,/path/mylib2.jar
		spark.jars.packages		com.databricks:spark-csv_2.10:1.2.0
		spark.files				/path/mylib1.py,/path/mylib2.egg,/path/mylib3.zip

<br />
<br />
### ZeppelinContext
<hr />

Zeppelin automatically injects ZeppelinContext as variable 'z' in your scala/python environment. ZeppelinContext provides some additional functions and utility.

<br />
#### Object exchange

ZeppelinContext extends map and it's shared between scala, python environment.
So you can put some object from scala and read it from python, vise versa.

Put object from scala

```scala
%spark
val myObject = ...
z.put("objName", myObject)
```

Get object from python

```python
%python
myObject = z.get("objName")
```

<br />
#### Form creation

ZeppelinContext provides functions for creating forms. 
In scala and python environments, you can create forms programmatically.

```scala
%spark
/* Create text input form */
z.input("formName")

/* Create text input form with default value */
z.input("formName", "defaultValue")

/* Create select form */
z.select("formName", Seq(("option1", "option1DisplayName"),
                         ("option2", "option2DisplayName")))

/* Create select form with default value*/
z.select("formName", "option1", Seq(("option1", "option1DisplayName"),
                                    ("option2", "option2DisplayName")))
```

In sql environment, you can create form in simple template.

```
%sql
select * from ${table=defaultTableName} where text like '%${search}%'
```

To learn more about dynamic form, checkout [Dynamic Form](../manual/dynamicform.html).
