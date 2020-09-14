---
layout: page
title: "Apache Zeppelin SDK - Session API"
description: "This page contains Apache Zeppelin SDK - Session API."
group: usage/zeppelin_sdk 
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

# Apache Zeppelin SDK - Session API

<div id="toc"></div>

## Overview

Session api is a high level api for zeppelin. There's no zeppelin concept (note, paragraph) in this api. The most important thing is a ZSession which represent a running interpreter process.
It is pretty to create a ZSession and its api is very straightforward, we can see a concret examples below.


## How to use ZSession

It is very easy to create a ZSession, you need to provide ClientConfig, interpreter and also you can customize your ZSession by specificy its interpreter properties.

After you can create ZSession, you need to start it before running any code. 
ZSession's lifecycle  is under your control, you need to call stop method expclitly, otherwise the interpreter processs will keep running.

{% highlight java %}
ZSession session = null;
try {
  ClientConfig clientConfig = new ClientConfig("http://localhost:8080");
  Map<String, String> intpProperties = new HashMap<>();
  intpProperties.put("spark.master", "local[*]");

  session = ZSession.builder()
          .setClientConfig(clientConfig)
          .setInterpreter("spark")
          .setIntpProperties(intpProperties)
          .build();

  session.start();
  System.out.println("Spark Web UI: " + session.getWeburl());

  // scala (single result)
  ExecuteResult result = session.execute("println(sc.version)");
  System.out.println("Spark Version: " + result.getResults().get(0).getData());

  // scala (multiple result)
  result = session.execute("println(sc.version)\n" +
          "val df = spark.createDataFrame(Seq((1,\"a\"), (2,\"b\")))\n" +
          "z.show(df)");

  // The first result is text output
  System.out.println("Result 1: type: " + result.getResults().get(0).getType() +
          ", data: " + result.getResults().get(0).getData() );
  // The second result is table output
  System.out.println("Result 2: type: " + result.getResults().get(1).getType() +
          ", data: " + result.getResults().get(1).getData() );
  System.out.println("Spark Job Urls:\n" + StringUtils.join(result.getJobUrls(), "\n"));

  // error output
  result = session.execute("1/0");
  System.out.println("Result status: " + result.getStatus() +
          ", data: " + result.getResults().get(0).getData());

  // pyspark
  result = session.execute("pyspark", "df = spark.createDataFrame([(1,'a'),(2,'b')])\n" +
          "df.registerTempTable('df')\n" +
          "df.show()");
  System.out.println("PySpark dataframe: " + result.getResults().get(0).getData());

  // matplotlib
  result = session.execute("ipyspark", "%matplotlib inline\n" +
          "import matplotlib.pyplot as plt\n" +
          "plt.plot([1,2,3,4])\n" +
          "plt.ylabel('some numbers')\n" +
          "plt.show()");
  System.out.println("Matplotlib result, type: " + result.getResults().get(0).getType() +
          ", data: " + result.getResults().get(0).getData());

  // sparkr
  result = session.execute("r", "df <- as.DataFrame(faithful)\nhead(df)");
  System.out.println("Sparkr dataframe: " + result.getResults().get(0).getData());

  // spark sql
  result = session.execute("sql", "select * from df");
  System.out.println("Spark Sql dataframe: " + result.getResults().get(0).getData());

  // spark invalid sql
  result = session.execute("sql", "select * from unknown_table");
  System.out.println("Result status: " + result.getStatus() +
          ", data: " + result.getResults().get(0).getData());
} catch (Exception e) {
  e.printStackTrace();
} finally {
  if (session != null) {
    try {
      session.stop();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
{% endhighlight %}

Here's a list of apis of `ZSession`.

{% highlight java %}
public void start() throws Exception

public void start(MessageHandler messageHandler) throws Exception

public void stop() throws Exception

public ExecuteResult execute(String code) throws Exception

public ExecuteResult execute(String subInterpreter,
                             Map<String, String> localProperties,
                             String code,
                             StatementMessageHandler messageHandler) throws Exception 

public ExecuteResult submit(String code) throws Exception 

public ExecuteResult submit(String subInterpreter,
                            Map<String, String> localProperties,
                            String code,
                            StatementMessageHandler messageHandler) throws Exception
                            
public void cancel(String statementId) throws Exception
 
public ExecuteResult queryStatement(String statementId) throws Exception

public ExecuteResult waitUntilFinished(String statementId) throws Exception

{% endhighlight %}



## Examples

For more detailed usage of session api, you can check the examples in module `zeppelin-client-examples`

* [SparkExample](https://github.com/apache/zeppelin/blob/master/zeppelin-client-examples/src/main/java/org/apache/zeppelin/client/examples/SparkExample.java)
* [SparkAdvancedExample](https://github.com/apache/zeppelin/blob/master/zeppelin-client-examples/src/main/java/org/apache/zeppelin/client/examples/SparkAdvancedExample.java)
* [FlinkExample](https://github.com/apache/zeppelin/blob/master/zeppelin-client-examples/src/main/java/org/apache/zeppelin/client/examples/FlinkExample.java)
* [FlinkAdvancedExample](https://github.com/apache/zeppelin/blob/master/zeppelin-client-examples/src/main/java/org/apache/zeppelin/client/examples/FlinkAdvancedExample.java)
* [FlinkAdvancedExample2](https://github.com/apache/zeppelin/blob/master/zeppelin-client-examples/src/main/java/org/apache/zeppelin/client/examples/FlinkAdvancedExample2.java)
* [HiveExample](https://github.com/apache/zeppelin/blob/master/zeppelin-client-examples/src/main/java/org/apache/zeppelin/client/examples/Hive.java)
* [PythonExample](https://github.com/apache/zeppelin/blob/master/zeppelin-client-examples/src/main/java/org/apache/zeppelin/client/examples/PythonExample.java)
* [RExample](https://github.com/apache/zeppelin/blob/master/zeppelin-client-examples/src/main/java/org/apache/zeppelin/client/examples/RExample.java)

