---
layout: page
title: "Livy Interpreter for Apache Zeppelin"
description: "Livy is an open source REST interface for interacting with Spark from anywhere. It supports executing snippets of code or programs in a Spark context that runs locally or in YARN."
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

# Livy Interpreter for Apache Zeppelin

<div id="toc"></div>

## Overview
[Livy](http://livy.io/) is an open source REST interface for interacting with Spark from anywhere. It supports executing snippets of code or programs in a Spark context that runs locally or in YARN.

* Interactive Scala, Python and R shells
* Batch submissions in Scala, Java, Python
* Multi users can share the same server (impersonation support)
* Can be used for submitting jobs from anywhere with REST
* Does not require any code change to your programs

### Requirements
Additional requirements for the Livy interpreter are:

 * Spark 1.3 or above.
 * Livy server.

## Configuration
We added some common configurations for spark, and you can set any configuration you want.
You can find all Spark configurations in [here](http://spark.apache.org/docs/latest/configuration.html#available-properties).
And instead of starting property with `spark.` it should be replaced with `livy.spark.`.
Example: `spark.driver.memory` to `livy.spark.driver.memory`

<table class="table-configuration">
  <tr>
    <th>Property</th>
    <th>Default</th>
    <th>Description</th>
  </tr>
  <tr>
    <td>zeppelin.livy.url</td>
    <td>http://localhost:8998</td>
    <td>URL where livy server is running</td>
  </tr>
  <tr>
    <td>zeppelin.livy.spark.sql.maxResult</td>
    <td>1000</td>
    <td>Max number of Spark SQL result to display.</td>
  </tr>
  <tr>
    <td>zeppelin.livy.spark.sql.field.truncate</td>
    <td>true</td>
    <td>Whether to truncate field values longer than 20 characters or not</td>
  </tr>
  <tr>
    <td>zeppelin.livy.session.create_timeout</td>
    <td>120</td>
    <td>Timeout in seconds for session creation</td>
  </tr>
  <tr>
    <td>zeppelin.livy.displayAppInfo</td>
    <td>true</td>
    <td>Whether to display app info</td>
  </tr>
  <tr>
    <td>zeppelin.livy.pull_status.interval.millis</td>
    <td>1000</td>
    <td>The interval for checking paragraph execution status</td>
  </tr>
  <tr>
    <td>livy.spark.driver.cores</td>
    <td></td>
    <td>Driver cores. ex) 1, 2.</td>
  </tr>
    <tr>
    <td>livy.spark.driver.memory</td>
    <td></td>
    <td>Driver memory. ex) 512m, 32g.</td>
  </tr>
    <tr>
    <td>livy.spark.executor.instances</td>
    <td></td>
    <td>Executor instances. ex) 1, 4.</td>
  </tr>
    <tr>
    <td>livy.spark.executor.cores</td>
    <td></td>
    <td>Num cores per executor. ex) 1, 4.</td>
  </tr>
  <tr>
    <td>livy.spark.executor.memory</td>
    <td></td>
    <td>Executor memory per worker instance. ex) 512m, 32g.</td>
  </tr>
  <tr>
    <td>livy.spark.dynamicAllocation.enabled</td>
    <td></td>
    <td>Use dynamic resource allocation. ex) True, False.</td>
  </tr>
  <tr>
    <td>livy.spark.dynamicAllocation.cachedExecutorIdleTimeout</td>
    <td></td>
    <td>Remove an executor which has cached data blocks.</td>
  </tr>
  <tr>
    <td>livy.spark.dynamicAllocation.minExecutors</td>
    <td></td>
    <td>Lower bound for the number of executors.</td>
  </tr>
  <tr>
    <td>livy.spark.dynamicAllocation.initialExecutors</td>
    <td></td>
    <td>Initial number of executors to run.</td>
  </tr>
  <tr>
    <td>livy.spark.dynamicAllocation.maxExecutors</td>
    <td></td>
    <td>Upper bound for the number of executors.</td>
  </tr>
    <tr>
      <td>livy.spark.jars.packages</td>
      <td></td>
      <td>Adding extra libraries to livy interpreter</td>
    </tr>
  <tr>
    <td>zeppelin.livy.ssl.trustStore</td>
    <td></td>
    <td>client trustStore file. Used when livy ssl is enabled</td>
  </tr>
  <tr>
    <td>zeppelin.livy.ssl.trustStorePassword</td>
    <td></td>
    <td>password for trustStore file. Used when livy ssl is enabled</td>
  </tr>
  <tr>
    <td>zeppelin.livy.http.headers</td>
    <td>key_1: value_1; key_2: value_2</td>
    <td>custom http headers when calling livy rest api. Each http header is separated by `;`, and each header is one key value pair where key value is separated by `:`</td>
  </tr>
</table>

**We remove livy.spark.master in zeppelin-0.7. Because we sugguest user to use livy 0.3 in zeppelin-0.7. And livy 0.3 don't allow to specify livy.spark.master, it enfornce yarn-cluster mode.**

## Adding External libraries
You can load dynamic library to livy interpreter by set `livy.spark.jars.packages` property to comma-separated list of maven coordinates of jars to include on the driver and executor classpaths. The format for the coordinates should be groupId:artifactId:version.

Example

<table class="table-configuration">
  <tr>
    <th>Property</th>
    <th>Example</th>
    <th>Description</th>
  </tr>
  <tr>
      <td>livy.spark.jars.packages</td>
      <td>io.spray:spray-json_2.10:1.3.1</td>
      <td>Adding extra libraries to livy interpreter</td>
    </tr>
  </table>

## How to use
Basically, you can use

**spark**

```
%livy.spark
sc.version
```


**pyspark**

```
%livy.pyspark
print "1"
```

**sparkR**

```
%livy.sparkr
hello <- function( name ) {
    sprintf( "Hello, %s", name );
}

hello("livy")
```

## Impersonation
When Zeppelin server is running with authentication enabled,
then this interpreter utilizes Livy’s user impersonation feature
i.e. sends extra parameter for creating and running a session ("proxyUser": "${loggedInUser}").
This is particularly useful when multi users are sharing a Notebook server.

## Apply Zeppelin Dynamic Forms
You can leverage [Zeppelin Dynamic Form](../usage/dynamic_form/intro.html). Form templates is only avalible for livy sql interpreter.
```
%livy.sql
select * from products where ${product_id=1}
```

And creating dynamic formst programmatically is not feasible in livy interpreter, because ZeppelinContext is not available in livy interpreter.

## FAQ

Livy debugging: If you see any of these in error console

> Connect to livyhost:8998 [livyhost/127.0.0.1, livyhost/0:0:0:0:0:0:0:1] failed: Connection refused

Looks like the livy server is not up yet or the config is wrong

> Exception: Session not found, Livy server would have restarted, or lost session.

The session would have timed out, you may need to restart the interpreter.


> Blacklisted configuration values in session config: spark.master

Edit `conf/spark-blacklist.conf` file in livy server and comment out `#spark.master` line.

If you choose to work on livy in `apps/spark/java` directory in [https://github.com/cloudera/hue](https://github.com/cloudera/hue),
copy `spark-user-configurable-options.template` to `spark-user-configurable-options.conf` file in livy server and comment out `#spark.master`.
