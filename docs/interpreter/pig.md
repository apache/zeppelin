---
layout: page
title: "Pig Interpreter for Apache Zeppelin"
description: "Apache Pig is a platform for analyzing large data sets that consists of a high-level language for expressing data analysis programs, coupled with infrastructure for evaluating these programs."
group: manual
---
{% include JB/setup %}


# Pig Interpreter for Apache Zeppelin

<div id="toc"></div>

## Overview
[Apache Pig](https://pig.apache.org/) is a platform for analyzing large data sets that consists of 
a high-level language for expressing data analysis programs, 
coupled with infrastructure for evaluating these programs. 
The salient property of Pig programs is that their structure is amenable to substantial parallelization, 
which in turns enables them to handle very large data sets.

## Supported interpreter type
  - `%pig.script` (default Pig interpreter, so you can use `%pig`)
    
    `%pig.script` is like the Pig grunt shell. Anything you can run in Pig grunt shell can be run in `%pig.script` interpreter, it is used for running Pig script where you don’t need to visualize the data, it is suitable for data munging. 
  
  - `%pig.query`
 
    `%pig.query` is a little different compared with `%pig.script`. It is used for exploratory data analysis via Pig latin where you can leverage Zeppelin’s visualization ability. There're 2 minor differences in the last statement between `%pig.script` and `%pig.query`
    - No pig alias in the last statement in `%pig.query` (read the examples below).
    - The last statement must be in single line in `%pig.query`
    

## How to use

### How to setup Pig execution modes.

- Local Mode

    Set `zeppelin.pig.execType` as `local`.

- MapReduce Mode

    Set `zeppelin.pig.execType` as `mapreduce`. HADOOP\_CONF\_DIR needs to be specified in `ZEPPELIN_HOME/conf/zeppelin-env.sh`.

- Tez Local Mode
    
    Only Tez 0.7 is supported. Set `zeppelin.pig.execType` as `tez_local`.
    
- Tez Mode

    Only Tez 0.7 is supported. Set `zeppelin.pig.execType` as `tez`. HADOOP\_CONF\_DIR and TEZ\_CONF\_DIR needs to be specified in `ZEPPELIN_HOME/conf/zeppelin-env.sh`.

- Spark Local Mode
    
    Only Spark 1.6.x is supported, by default it is Spark 1.6.3. Set `zeppelin.pig.execType` as `spark_local`.
    
- Spark Mode
    
    Only Spark 1.6.x is supported, by default it is Spark 1.6.3. Set `zeppelin.pig.execType` as `spark`. For now, only yarn-client mode is supported. To enable it, you need to set property `SPARK_MASTER` to yarn-client and set `SPARK_JAR` to the spark assembly jar.
        
### How to choose custom Spark Version

By default, Pig Interpreter would use Spark 1.6.3 built with scala 2.10, if you want to use another spark version or scala version, 
you need to rebuild Zeppelin by specifying the custom Spark version via -Dpig.spark.version=<custom_spark_version> and scala version via -Dpig.scala.version=<scala_version> in the maven build command.

### How to configure interpreter

At the Interpreters menu, you have to create a new Pig interpreter. Pig interpreter has below properties by default.
And you can set any Pig properties here which will be passed to Pig engine. (like tez.queue.name & mapred.job.queue.name).
Besides, we use paragraph title as job name if it exists, else use the last line of Pig script. 
So you can use that to find app running in YARN RM UI.

<table class="table-configuration">
    <tr>
        <th>Property</th>
        <th>Default</th>
        <th>Description</th>
    </tr>
    <tr>
        <td>zeppelin.pig.execType</td>
        <td>mapreduce</td>
        <td>Execution mode for pig runtime. local | mapreduce | tez_local | tez | spark_local | spark </td>
    </tr>
    <tr>
        <td>zeppelin.pig.includeJobStats</td>
        <td>false</td>
        <td>whether display jobStats info in <code>%pig.script</code></td>
    </tr>
    <tr>
        <td>zeppelin.pig.maxResult</td>
        <td>1000</td>
        <td>max row number displayed in <code>%pig.query</code></td>
    </tr>
    <tr>
        <td>tez.queue.name</td>
        <td>default</td>
        <td>queue name for tez engine</td>
    </tr>
    <tr>
        <td>mapred.job.queue.name</td>
        <td>default</td>
        <td>queue name for mapreduce engine</td>
    </tr>
    <tr>
        <td>SPARK_MASTER</td>
        <td>local</td>
        <td>local | yarn-client</td>
    </tr>
    <tr>
        <td>SPARK_JAR</td>
        <td></td>
        <td>The spark assembly jar, both jar in local or hdfs is supported. Put it on hdfs could have
        performance benefit</td>
    </tr>
</table>  

### Example

##### pig

```
%pig

bankText = load 'bank.csv' using PigStorage(';');
bank = foreach bankText generate $0 as age, $1 as job, $2 as marital, $3 as education, $5 as balance; 
bank = filter bank by age != '"age"';
bank = foreach bank generate (int)age, REPLACE(job,'"','') as job, REPLACE(marital, '"', '') as marital, (int)(REPLACE(balance, '"', '')) as balance;
store bank into 'clean_bank.csv' using PigStorage(';'); -- this statement is optional, it just show you that most of time %pig.script is used for data munging before querying the data. 
```

##### pig.query

Get the number of each age where age is less than 30

```
%pig.query
 
bank_data = filter bank by age < 30;
b = group bank_data by age;
foreach b generate group, COUNT($1);
```

The same as above, but use dynamic text form so that use can specify the variable maxAge in textbox. 
(See screenshot below). Dynamic form is a very cool feature of Zeppelin, you can refer this [link]((../usage/dynamic_form/intro.html)) for details.

```
%pig.query
 
bank_data = filter bank by age < ${maxAge=40};
b = group bank_data by age;
foreach b generate group, COUNT($1) as count;
```

Get the number of each age for specific marital type, 
also use dynamic form here. User can choose the marital type in the dropdown list (see screenshot below).

```
%pig.query
 
bank_data = filter bank by marital=='${marital=single,single|divorced|married}';
b = group bank_data by age;
foreach b generate group, COUNT($1) as count;
```

The above examples are in the Pig tutorial note in Zeppelin, you can check that for details. Here's the screenshot.

<img class="img-responsive" width="1024px" style="margin:0 auto; padding: 26px;" src="{{BASE_PATH}}/assets/themes/zeppelin/img/pig_zeppelin_tutorial.png" />


Data is shared between `%pig` and `%pig.query`, so that you can do some common work in `%pig`, 
and do different kinds of query based on the data of `%pig`. 
Besides, we recommend you to specify alias explicitly so that the visualization can display 
the column name correctly. In the above example 2 and 3 of `%pig.query`, we name `COUNT($1)` as `count`. 
If you don't do this, then we will name it using position. 
E.g. in the above first example of `%pig.query`, we will use `col_1` in chart to represent `COUNT($1)`.


