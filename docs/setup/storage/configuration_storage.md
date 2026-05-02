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
- `credentials.json` (This file contains the credential info)

## Configuration Storage in S3-compatible object storage

Zeppelin can persist configuration state in S3-compatible object storage by reusing
`FileSystemConfigStorage` with Hadoop S3A. This keeps configuration storage on the
same Hadoop-compatible storage abstraction used for HDFS and other filesystems.

Set the following properties in `zeppelin-site.xml`:

```xml
<property>
    <name>zeppelin.config.storage.class</name>
    <value>org.apache.zeppelin.storage.FileSystemConfigStorage</value>
    <description>configuration persistence layer implementation</description>
</property>
<property>
    <name>zeppelin.config.fs.dir</name>
    <value>s3a://bucket_name/user/config</value>
    <description>S3A path for Zeppelin configuration files</description>
</property>
```

Also ensure the Zeppelin server classpath contains the Hadoop S3A runtime:
`hadoop-aws` built for the same Hadoop version as Zeppelin, plus its compatible
AWS SDK dependencies. `HADOOP_CONF_DIR` is still required so Zeppelin can find
the Hadoop configuration files that define S3A credentials, endpoint, encryption,
and bucket policy settings. For example, configure properties such as
`fs.s3a.aws.credentials.provider`, `fs.s3a.endpoint`,
`fs.s3a.server-side-encryption-algorithm`, and
`fs.s3a.server-side-encryption.key` in your Hadoop configuration as needed.

S3A does not enforce POSIX permissions on objects. When credentials persistence is
enabled, protect `credentials.json` with S3 bucket policy, object ownership, and
encryption settings instead of relying on owner-only file permissions.

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
