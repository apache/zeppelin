---
layout: page
title: "HBase Shell Interpreter for Apache Zeppelin"
description: "HBase Shell is a JRuby IRB client for Apache HBase. This interpreter provides all capabilities of Apache HBase shell within Apache Zeppelin."
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

# HBase Shell Interpreter for Apache Zeppelin

<div id="toc"></div>

## Overview
[HBase Shell](http://hbase.apache.org/book.html#shell) is a JRuby IRB client for Apache HBase. This interpreter provides all capabilities of Apache HBase shell within Apache Zeppelin. The interpreter assumes that Apache HBase client software has been installed and it can connect to the Apache HBase cluster from the machine on where Apache Zeppelin is installed.  
To get start with HBase, please see [HBase Quickstart](https://hbase.apache.org/book.html#quickstart).

## HBase release supported
Zeppelin is built against HBase 1.x and 2.x releases. 

## Configuration

<table class="table-configuration">
  <tr>
    <th>Property</th>
    <th>Default</th>
    <th>Description</th>
  </tr>
  <tr>
    <td>hbase.home</td>
    <td>/usr/lib/hbase</td>
    <td>Installation directory of HBase, defaults to HBASE_HOME in environment</td>
  </tr>
</table>

If you want to connect to HBase running on a cluster, you'll need to follow the next step.

### Export HBASE_HOME
In `conf/zeppelin-env.sh`, export `HBASE_HOME` environment variable with your HBase installation path. This ensures `hbase-site.xml` can be loaded.

For example

```bash
export HBASE_HOME=/usr/lib/hbase
```

or, when running with CDH

```bash
export HBASE_HOME="/opt/cloudera/parcels/CDH/lib/hbase"
```

You can optionally export `HBASE_CONF_DIR` instead of `HBASE_HOME` should you have custom HBase configurations.

## Enabling the HBase Shell Interpreter

In a notebook, to enable the **HBase Shell** interpreter, click the **Gear** icon and select **HBase Shell**.

## Using the HBase Shell Interpreter

In a paragraph, use `%hbase` to select the **HBase Shell** interpreter and then input all commands. To get the list of available commands, use `help`.

```bash
%hbase
help
```

For example, to create a table

```bash
%hbase
create 'test', 'cf'
```

And then to put data into that table

```bash
%hbase
put 'test', 'row1', 'cf:a', 'value1'
```

For more information on all commands available, refer to [HBase shell commands](https://learnhbase.wordpress.com/2013/03/02/hbase-shell-commands/).
