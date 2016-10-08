---
layout: page
title: "Pig Interpreter"
description: ""
group: manual
---
{% include JB/setup %}


## Pig nterpreter for Apache Zeppelin
[Apache Pig](https://pig.apache.org/) is a platform for analyzing large data sets that consists of a high-level language for expressing data analysis programs, coupled with infrastructure for evaluating these programs. The salient property of Pig programs is that their structure is amenable to substantial parallelization, which in turns enables them to handle very large data sets.

## Supported interpreter type
  - %pig.script (default)       - All the pig script can run in the type of interpreter, and display type if plain text.
  - %pig.query                  - Almost the same as %pig.script. the only difference is that you don't need to add alias in the last statement. And the display type is table.   


## Supported runtime mode
  - Local
  - MapReduce
  - Tez  (Only Tez 0.7 is supported)

### How to setup Pig

- Local Mode
Nothing needs to be done for local mode

- MapReduce Mode
HADOOP_CONF_DIR needs to be specified in `zeppelin-env.sh`

- Tez Mode
HADOOP_CONF_DIR and TEZ_CONF_DIR needs to be specified in `zeppelin-env.sh`

### How to configure interpreter

At the Interpreters menu, you have to create a new Pig interpreter and provide next properties:


<table class="table-configuration">
    <tr>
        <th>Property</th>
        <th>Default</th>
        <th>Description</th>
    </tr>
    <tr>
        <td>zeppelin.pig.execType</td>
        <td>mapreduce</td>
        <td>Execution mode for pig runtime. Local | mapreduce | tez </td>
    </tr>
    <tr>
        <td>zeppelin.pig.includeJobStats</td>
        <td>false</td>
        <td>whether display jobStats info in %pig</td>
    </tr>
    <tr>
        <td>zeppelin.pig.maxResult</td>
        <td>20</td>
        <td>max row number displayed in %pig.query</td>
    </tr>
</table>  

### How to use

**pig**

```
%pig

raw_data = load 'dataset/sf_crime/train.csv' using PigStorage(',') as (Dates,Category,Descript,DayOfWeek,PdDistrict,Resolution,Address,X,Y);
b = group raw_data all;
c = foreach b generate COUNT($1);
dump c;
```

**pig.query**
```
b = foreach raw_data generate Category;
c = group b by Category;
foreach c generate group as category, COUNT($1) as count;
```


Data is shared between %pig and %pig.query, so that you can do some common work in %pig, and do different kinds of query based on the data of %pig.
