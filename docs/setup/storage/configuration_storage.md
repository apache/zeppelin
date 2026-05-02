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

## Configuration Storage in S3

Set the following properties in `zeppelin-site.xml` to persist Zeppelin configuration
state in S3. This stores `interpreter.json`, `notebook-authorization.json`, and
`credentials.json` under a shared S3 prefix.

```xml
<property>
    <name>zeppelin.config.storage.class</name>
    <value>org.apache.zeppelin.storage.S3ConfigStorage</value>
    <description>configuration persistence layer implementation</description>
</property>
<property>
    <name>zeppelin.config.s3.dir</name>
    <value>s3://bucket_name/user/config</value>
    <description>S3 prefix or URI for Zeppelin configuration files</description>
</property>
```

`zeppelin.config.s3.dir` can be either an S3 URI such as
`s3://bucket_name/user/config` or a key prefix such as `user/config`. When only a
key prefix is provided, `zeppelin.notebook.s3.bucket` is used as the bucket. When
`zeppelin.config.s3.dir` is omitted, Zeppelin stores configuration under
`{zeppelin.notebook.s3.user}/config`.

`S3ConfigStorage` uses the same S3 client settings as `S3NotebookRepo`, including
`zeppelin.notebook.s3.endpoint`, `zeppelin.notebook.s3.pathStyleAccess`,
`zeppelin.notebook.s3.sse`, `zeppelin.notebook.s3.kmsKeyID`,
`zeppelin.notebook.s3.kmsKeyRegion`,
`zeppelin.notebook.s3.encryptionMaterialsProvider`,
and `zeppelin.notebook.s3.signerOverride`.

`S3ConfigStorage` does not inherit `zeppelin.notebook.s3.cannedAcl`, because
configuration files can contain credentials and other sensitive values. If your
bucket ownership policy requires a canned ACL, set `zeppelin.config.s3.cannedAcl`
explicitly and avoid public or broadly readable ACLs.

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
