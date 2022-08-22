---
layout: page
title: "Configuration Storage for Apache Zeppelin"
description: "Configuration Storage for Apache Zeppelin"
group: setup/storage
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

# Configuration Storage for Apache Zeppelin

<div id="toc"></div>

## Overview
Zeppelin has lots of configuration which is stored in files:
- `interpreter.json` (This file contains all the interpreter setting info)
- `notebook-authorization.json` (This file contains all the note authorization info)
- `credential.json` (This file contains the credential info)

## Configuration Storage in hadoop compatible file system

Set following properties in `zeppelin-site.xml`:
```xml
<property>
    <name>zeppelin.config.storage.class</name>
    <value>org.apache.zeppelin.storage.FileSystemConfigStorage</value>
    <description>configuration persistence layer implementation</description>
</property>
<property>
    <name>zeppelin.config.fs.dir</name>
    <value></value>
    <description>path on the hadoop compatible file system</description>
</property>
```
Also specify `HADOOP_CONF_DIR` in `zeppelin-env.sh` so that Zeppelin can find the right hadoop configuration files.

If your hadoop cluster is kerberized, then you need to specify `zeppelin.server.kerberos.keytab` and `zeppelin.server.kerberos.principal`


## Configuration Storage in local file system
By default, zeppelin store configuration on local file system.
```xml
<property>
    <name>zeppelin.config.storage.class</name>
    <value>org.apache.zeppelin.storage.LocalConfigStorage</value>
    <description>configuration persistence layer implementation</description>
</property>
<property>
    <name>zeppelin.config.fs.dir</name>
    <value></value>
    <description>path on local file system</description>
</property>
```