---
layout: page
title: "Manual upgrade procedure for Zeppelin"
description: ""
group: install
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
1. Stop Zeppelin

    ```
    bin/zeppelin-daemon.sh stop
    ```

1. Copy your `notebook` and `conf` directory into a backup directory

1. Download newer version of Zeppelin and Install. See [Install page](./install.html#installation).

1. Copy backup `notebook` and `conf` directory into newer version of Zeppelin `notebook` and `conf` directory

1. Start Zeppelin

   ```
   bin/zeppelin-daemon.sh start
   ```

## Migration Guide

### Upgrading from Zeppelin 0.6 to 0.7

 - From 0.7, we don't use `ZEPPELIN_JAVA_OPTS` as default value of `ZEPPELIN_INTP_JAVA_OPTS` and also the same for `ZEPPELIN_MEM`/`ZEPPELIN_INTP_MEM`. If user want to configure the jvm opts of interpreter process, please set `ZEPPELIN_INTP_JAVA_OPTS` and `ZEPPELIN_INTP_MEM` explicitly.