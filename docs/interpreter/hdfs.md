---
layout: page
title: "HDFS File System Interpreter for Apache Zeppelin"
description: "Hadoop File System is a distributed, fault tolerant file system part of the hadoop project and is often used as storage for distributed processing engines like Hadoop MapReduce and Apache Spark or underlying file systems like Alluxio."
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

# HDFS File System Interpreter for Apache Zeppelin

<div id="toc"></div>

## Overview
[Hadoop File System](http://hadoop.apache.org/) is a distributed, fault tolerant file system part of the hadoop project and is often used as storage for distributed processing engines like [Hadoop MapReduce](http://hadoop.apache.org/) and [Apache Spark](http://spark.apache.org/) or underlying file systems like [Alluxio](http://www.alluxio.org/).

## Configuration
<table class="table-configuration">
  <tr>
    <th>Property</th>
    <th>Default</th>
    <th>Description</th>
  </tr>
  <tr>
    <td>hdfs.url</td>
    <td>http://localhost:50070/webhdfs/v1/</td>
    <td>The URL for WebHDFS</td>
  </tr>
  <tr>
    <td>hdfs.user</td>
    <td>hdfs</td>
    <td>The WebHDFS user</td>
  </tr>
  <tr>
    <td>hdfs.maxlength</td>
    <td>1000</td>
    <td>Maximum number of lines of results fetched</td>
  </tr>
</table>

<br/>
This interpreter connects to HDFS using the HTTP WebHDFS interface.
It supports the basic shell file commands applied to HDFS, it currently only supports browsing.

* You can use <i>ls [PATH]</i> and <i>ls -l [PATH]</i> to list a directory. If the path is missing, then the current directory is listed.  <i>ls </i> supports a <i>-h</i> flag for human readable file sizes.
* You can use <i>cd [PATH]</i> to change your current directory by giving a relative or an absolute path.
* You can invoke <i>pwd</i> to see your current directory.

> **Tip :** Use ( Ctrl + . ) for autocompletion.

## Create Interpreter

In a notebook, to enable the **HDFS** interpreter, click the **Gear** icon and select **HDFS**.


## WebHDFS REST API
You can confirm that you're able to access the WebHDFS API by running a curl command against the WebHDFS end point provided to the interpreter.

Here is an example:

```bash
$> curl "http://localhost:50070/webhdfs/v1/?op=LISTSTATUS"
```

