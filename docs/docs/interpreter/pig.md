---
layout: page
title: "Pig Interpreter"
description: ""
group: manual
---
{% include JB/setup %}


## Pig interpreter for Apache Zeppelin
[Apache Pig](https://pig.apache.org/) is a platform for analyzing large data sets that consists of a high-level language for expressing data analysis programs, coupled with infrastructure for evaluating these programs. The salient property of Pig programs is that their structure is amenable to substantial parallelization, which in turns enables them to handle very large data sets.

- Supported operations through Zeppelin
  - Play button: Run multiple lines of pig latin (delimited by semi-colon) entered into Zeppelin cell
  - Pause button: Cancel the exection of pig
  - Output: Output of jobs are displayed in Zeppelin (for both jobs that were sucessful and those that failed)

- Unsupported operations
  - Progress bar

### How to setup Pig
Install Pig as you would normally do on the same node where Zeppelin is running - see [documentation](https://pig.apache.org/). If installing through Ambari, you just need to install the client on the Zeppelin node.

### How to configure interpreter
At the "Interpreters" menu, you have to create a new Pig interpreter and provide next properties:

property | value    | Description
---------|----------|-----
executable	 | pig    | Path to pig executable. If pig is part of PATH, then just pig will work
args	 | -useHCatalog -exectype local     | Arguments to pass pig when starting. Launch 'pig -help' for list of supported options. For example, the options for exectype: local|mapreduce|tez
timeout    | 600000      | Time (in milliseconds) to wait for pig job before timing out

### How to test it's working

Run below example taken from [this tutorial](http://hortonworks.com/hadoop-tutorial/how-to-use-basic-pig-commands/)

```
%sh
rm -f infochimps_dataset_4778_download_16677-csv.zip
wget https://s3.amazonaws.com/hw-sandbox/tutorial1/infochimps_dataset_4778_download_16677-csv.zip -O /tmp/infochimps_dataset_4778_download_16677-csv.zip
unzip /tmp/infochimps_dataset_4778_download_16677-csv.zip -d /tmp
```
```
%pig
DIV_A = LOAD 'file:///tmp/infochimps_dataset_4778_download_16677/NYSE/NYSE_dividends_A.csv' using PigStorage(',') AS (exchange:chararray, symbol:chararray, date:chararray, dividend:float);
B = FILTER DIV_A BY symbol=='AZZ'; 
C = GROUP B BY dividend; 
dump C;
```
