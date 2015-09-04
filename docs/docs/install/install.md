---
layout: page
title: "Install Zeppelin"
description: ""
group: install
---
{% include JB/setup %}



## Build

#### Prerequisites

 * Java 1.7
 * None root account
 * Apache Maven

Build tested on OSX, CentOS 6.

Checkout source code from [https://github.com/apache/incubator-zeppelin](https://github.com/apache/incubator-zeppelin)

#### Local mode

```
mvn install -DskipTests
```

#### Cluster mode

```
mvn install -DskipTests -Dspark.version=1.1.0 -Dhadoop.version=2.2.0
```

Change spark.version and hadoop.version to your cluster's one.

#### Custom built Spark

Note that is you uses custom build spark, you need build Zeppelin with custome built spark artifact. To do that, deploy spark artifact to local maven repository using

```
sbt/sbt publish-local
```

and then build Zeppelin with your custom built Spark

```
mvn install -DskipTests -Dspark.version=1.1.0-Custom -Dhadoop.version=2.2.0
```




## Configure

Configuration can be done by both environment variable(conf/zeppelin-env.sh) and java properties(conf/zeppelin-site.xml). If both defined, environment vaiable is used.


<table class="table-configuration">
  <tr>
    <th>zepplin-env.sh</th>
    <th>zepplin-site.xml</th>
    <th>Default value</th>
    <th>Description</th>
  </tr>
  <tr>
    <td>ZEPPELIN_PORT</td>
    <td>zeppelin.server.port</td>
    <td>8080</td>
    <td>Zeppelin server port. Note that port+1 is used for web socket</td>
  </tr>
  <tr>
    <td>ZEPPELIN_NOTEBOOK_DIR</td>
    <td>zeppelin.notebook.dir</td>
    <td>notebook</td>
    <td>Where notebook file is saved</td>
  </tr>
  <tr>
    <td>ZEPPELIN_INTERPRETERS</td>
    <td>zeppelin.interpreters</td>
  <description></description>
    <td>org.apache.zeppelin.spark.SparkInterpreter,<br />org.apache.zeppelin.spark.PySparkInterpreter,<br />org.apache.zeppelin.spark.SparkSqlInterpreter,<br />org.apache.zeppelin.spark.DepInterpreter,<br />org.apache.zeppelin.markdown.Markdown,<br />org.apache.zeppelin.shell.ShellInterpreter,<br />org.apache.zeppelin.hive.HiveInterpreter</td>
    <td>Comma separated interpreter configurations [Class]. First interpreter become a default</td>
  </tr>
  <tr>
    <td>ZEPPELIN_INTERPRETER_DIR</td>
    <td>zeppelin.interpreter.dir</td>
    <td>interpreter</td>
    <td>Zeppelin interpreter directory</td>
  </tr>
  <tr>
    <td>MASTER</td>
    <td></td>
    <td>N/A</td>
    <td>Spark master url. eg. spark://master_addr:7077. Leave empty if you want to use local mode</td>
  </tr>
  <tr>
    <td>ZEPPELIN_JAVA_OPTS</td>
    <td></td>
    <td>N/A</td>
    <td>JVM Options</td>
</table>

#### Add jars, files

spark.jars, spark.files property in *ZEPPELIN\_JAVA\_OPTS* adds jars, files into SparkContext.
for example, 

    ZEPPELIN_JAVA_OPTS="-Dspark.jars=/mylib1.jar,/mylib2.jar -Dspark.files=/myfile1.dat,/myfile2.dat"

or you can do it dynamically with [dependency loader](../interpreter/spark.html#dependencyloading)


## Start/Stop
#### Start Zeppelin

```
bin/zeppelin-daemon.sh start
```
After successful start, visit http://localhost:8080 with your web browser.
Note that port **8081** also need to be accessible for websocket connection.

#### Stop Zeppelin

```
bin/zeppelin-daemon.sh stop
```


