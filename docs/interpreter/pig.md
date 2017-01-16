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
[Apache Pig](https://pig.apache.org/) is a platform for analyzing large data sets that consists of a high-level language for expressing data analysis programs, coupled with infrastructure for evaluating these programs. The salient property of Pig programs is that their structure is amenable to substantial parallelization, which in turns enables them to handle very large data sets.

## Supported interpreter type
  - `%pig.script` (default)
    
    All the pig script can run in this type of interpreter, and display type is plain text.
  
  - `%pig.query`
 
    Almost the same as `%pig.script`. The only difference is that you don't need to add alias in the last statement. And the display type is table.   

## Supported runtime mode
  - Local
  - MapReduce
  - Tez_Local (Only Tez 0.7 is supported)
  - Tez  (Only Tez 0.7 is supported)

## How to use

### How to setup Pig

- Local Mode

    Nothing needs to be done for local mode

- MapReduce Mode

    HADOOP\_CONF\_DIR needs to be specified in `ZEPPELIN_HOME/conf/zeppelin-env.sh`.

- Tez Local Mode
    
    Nothing needs to be done for tez local mode
    
- Tez Mode

    HADOOP\_CONF\_DIR and TEZ\_CONF\_DIR needs to be specified in `ZEPPELIN_HOME/conf/zeppelin-env.sh`.

### How to configure interpreter

At the Interpreters menu, you have to create a new Pig interpreter. Pig interpreter has below properties by default.
And you can set any pig properties here which will be passed to pig engine. (like tez.queue.name & mapred.job.queue.name).
Besides, we use paragraph title as job name if it exists, else use the last line of pig script. So you can use that to find app running in YARN RM UI.

<table class="table-configuration">
    <tr>
        <th>Property</th>
        <th>Default</th>
        <th>Description</th>
    </tr>
    <tr>
        <td>zeppelin.pig.execType</td>
        <td>mapreduce</td>
        <td>Execution mode for pig runtime. local | mapreduce | tez_local | tez </td>
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
</table>  

### Example

##### pig

```
%pig

raw_data = load 'dataset/sf_crime/train.csv' using PigStorage(',') as (Dates,Category,Descript,DayOfWeek,PdDistrict,Resolution,Address,X,Y);
b = group raw_data all;
c = foreach b generate COUNT($1);
dump c;
```

##### pig.query

```
%pig.query

b = foreach raw_data generate Category;
c = group b by Category;
foreach c generate group as category, COUNT($1) as count;
```

Data is shared between `%pig` and `%pig.query`, so that you can do some common work in `%pig`, and do different kinds of query based on the data of `%pig`. 
Besides, we recommend you to specify alias explicitly so that the visualization can display the column name correctly. Here, we name `COUNT($1)` as `count`, if you don't do this,
then we will name it using position, here we will use `col_1` to represent `COUNT($1)` if you don't specify alias for it. There's one pig tutorial note in zeppelin for your reference.
