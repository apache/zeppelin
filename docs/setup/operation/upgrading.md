---
layout: page
title: "Manual Zeppelin version upgrade procedure"
description: "This document will guide you through a procedure of manual upgrade your Apache Zeppelin instance to a newer version. Apache Zeppelin keeps backward compatibility for the notebook file format."
group: setup/operation 
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

# Manual upgrade procedure for Zeppelin

<div id="toc"></div>

Basically, newer version of Zeppelin works with previous version notebook directory and configurations.
So, copying `notebook` and `conf` directory should be enough.

## Instructions
1. Stop Zeppelin: `bin/zeppelin-daemon.sh stop`
2. Copy your `notebook` and `conf` directory into a backup directory
3. Download newer version of Zeppelin and Install. See [Install Guide](../../quickstart/install.html#install).
4. Copy backup `notebook` and `conf` directory into newer version of Zeppelin `notebook` and `conf` directory
5. Start Zeppelin:  `bin/zeppelin-daemon.sh start`

## Migration Guide

### Upgrading from Zeppelin 0.7 to 0.8

 - From 0.8, we recommend to use `PYSPARK_PYTHON` and `PYSPARK_DRIVER_PYTHON` instead of `zeppelin.pyspark.python` as `zeppelin.pyspark.python` only effects driver. You can use `PYSPARK_PYTHON` and `PYSPARK_DRIVER_PYTHON` as using them in spark.
 - From 0.8, depending on your device, the keyboard shortcut `Ctrl-L` or `Command-L` which goes to the line somewhere user wants is not supported. 

### Upgrading from Zeppelin 0.6 to 0.7

 - From 0.7, we don't use `ZEPPELIN_JAVA_OPTS` as default value of `ZEPPELIN_INTP_JAVA_OPTS` and also the same for `ZEPPELIN_MEM`/`ZEPPELIN_INTP_MEM`. If user want to configure the jvm opts of interpreter process, please set `ZEPPELIN_INTP_JAVA_OPTS` and `ZEPPELIN_INTP_MEM` explicitly. If you don't set `ZEPPELIN_INTP_MEM`, Zeppelin will set it to `-Xms1024m -Xmx1024m -XX:MaxPermSize=512m` by default.
 - Mapping from `%jdbc(prefix)` to `%prefix` is no longer available. Instead, you can use %[interpreter alias] with multiple interpreter setttings on GUI.
 - Usage of `ZEPPELIN_PORT` is not supported in ssl mode. Instead use `ZEPPELIN_SSL_PORT` to configure the ssl port. Value from `ZEPPELIN_PORT` is used only when `ZEPPELIN_SSL` is set to `false`.
 - The support on Spark 1.1.x to 1.3.x is deprecated.
 - From 0.7, we uses `pegdown` as the `markdown.parser.type` option for the `%md` interpreter. Rendered markdown might be different from what you expected
 - From 0.7 note.json format has been changed to support multiple outputs in a paragraph. Zeppelin will automatically convert old format to new format. 0.6 or lower version can read new note.json format but output will not be displayed. For the detail, see [ZEPPELIN-212](http://issues.apache.org/jira/browse/ZEPPELIN-212) and [pull request](https://github.com/apache/zeppelin/pull/1658).
 - From 0.7 note storage layer will utilize `GitNotebookRepo` by default instead of `VFSNotebookRepo` storage layer, which is an extension of latter one with versioning capabilities on top of it.
