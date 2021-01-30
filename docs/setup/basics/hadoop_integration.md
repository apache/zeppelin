---
layout: page
title: "How to integrate with hadoop"
description: "How to integrate with hadoop"
group: setup/basics
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

# Integrate with hadoop

<div id="toc"></div>

Hadoop is an optional component of zeppelin unless you need the following features

* Use hdfs to store notes. 
* Use hdfs to store interpreter configuration
* Use hdfs to store recovery data
* Launch interpreter in yarn mode

## Requirements

Zeppelin 0.9 doesn't ship with hadoop dependencies, you need to include hadoop jars by yourself via the following steps

* Hadoop client (both 2.x and 3.x are supported) is installed.
* `$HADOOP_HOME/bin` is put in `PATH`. Because internally zeppelin will run command `hadoop classpath` to get all the hadoop jars and put them in the classpath of Zeppelin.
* Set `USE_HADOOP` as `true` in `zeppelin-env.sh`.
