---
layout: page
title: "Livy Interpreter"
description: ""
group: manual
---
{% include JB/setup %}

## Livy Interpreter for Apache Zeppelin
Livy is an open source REST interface for interacting with Spark from anywhere. It supports executing snippets of code or programs in a Spark context that runs locally or in YARN.

* Interactive Scala, Python and R shells
* Batch submissions in Scala, Java, Python
* Multi users can share the same server (impersonation support)
* Can be used for submitting jobs from anywhere with REST
* Does not require any code change to your programs

### Requirements

Additional requirements for the Livy interpreter are:

 * Spark 1.3 or above.
 * Livy server.

### Configuration
<table class="table-configuration">
  <tr>
    <th>Property</th>
    <th>Default</th>
    <th>Description</th>
  </tr>
  <tr>
      <td>zeppelin.livy.master</td>
      <td>local[*]</td>
      <td>Spark master uri. ex) spark://masterhost:7077</td>
    </tr>
  <tr>
    <td>zeppelin.livy.url</td>
    <td>http://localhost:8998</td>
    <td>URL where livy server is running</td>
  </tr>
  <tr>
    <td>zeppelin.livy.spark.maxResult</td>
    <td>1000</td>
    <td>Max number of SparkSQL result to display.</td>
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
When Zeppelin server is running with authentication enabled, then this interpreter utilizes Livy’s user impersonation feature i.e. sends extra parameter for creating and running a session ("proxyUser": "${loggedInUser}"). This is particularly useful when multi users are sharing a Notebook server.


### Apply Zeppelin Dynamic Forms
You can leverage [Zeppelin Dynamic Form]({{BASE_PATH}}/manual/dynamicform.html). You can use both the `text input` and `select form` parameterization features.

```
%livy.pyspark
print "${group_by=product_id,product_id|product_name|customer_id|store_id}"
```

## FAQ

Livy debugging: If you see any of these in error console

> Connect to livyhost:8998 [livyhost/127.0.0.1, livyhost/0:0:0:0:0:0:0:1] failed: Connection refused

Looks like the livy server is not up yet or the config is wrong

> Exception: Session not found, Livy server would have restarted, or lost session.

The session would have timed out, you may need to restart the interpreter.


> Blacklisted configuration values in session config: spark.master

edit `conf/spark-blacklist.conf` file in livy server and comment out `#spark.master` line.
