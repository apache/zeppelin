---
layout: page
title: "Notebook Storage for Apache Zeppelin"
description: Apache Zeppelin has a pluggable notebook storage mechanism controlled by zeppelin.notebook.storage configuration option with multiple implementations."
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

# Notebook storage options for Apache Zeppelin

<div id="toc"></div>

## Overview

Apache Zeppelin has a pluggable notebook storage mechanism controlled by `zeppelin.notebook.storage` configuration option with multiple implementations.
There are few notebook storage systems available for a use out of the box:

  * (default) use local file system and version it using local Git repository - `GitNotebookRepo`
  * all notes are saved in the notebook folder in your local File System - `VFSNotebookRepo`
  * all notes are saved in the notebook folder in hadoop compatible file system - `FileSystemNotebookRepo`
  * storage using Amazon S3 service - `S3NotebookRepo`
  * storage using Azure service - `AzureNotebookRepo`
  * storage using MongoDB - `MongoNotebookRepo`

Multiple storage systems can be used at the same time by providing a comma-separated list of the class-names in the configuration.
By default, only first two of them will be automatically kept in sync by Zeppelin.

</br>

## Notebook Storage in local Git repository <a name="Git"></a>

To enable versioning for all your local notebooks though a standard Git repository - uncomment the next property in `zeppelin-site.xml` in order to use GitNotebookRepo class:

```
<property>
  <name>zeppelin.notebook.storage</name>
  <value>org.apache.zeppelin.notebook.repo.GitNotebookRepo</value>
  <description>notebook persistence layer implementation</description>
</property>
```

</br>

## Notebook Storage in hadoop compatible file system repository <a name="Hdfs"></a>

Notes may be stored in hadoop compatible file system such as hdfs, so that multiple Zeppelin instances can share the same notes. It supports all the versions of hadoop 2.x. If you use `FileSystemNotebookRepo`, then `zeppelin.notebook.dir` is the path on the hadoop compatible file system. And you need to specify `HADOOP_CONF_DIR` in `zeppelin-env.sh` so that zeppelin can find the right hadoop configuration files.
If your hadoop cluster is kerberized, then you need to specify `zeppelin.server.kerberos.keytab` and `zeppelin.server.kerberos.principal`

```
<property>
  <name>zeppelin.notebook.storage</name>
  <value>org.apache.zeppelin.notebook.repo.FileSystemNotebookRepo</value>
  <description>hadoop compatible file system notebook persistence layer implementation</description>
</property>
```


</br>

## Notebook Storage in S3 <a name="S3"></a>

Notebooks may be stored in S3, and optionally encrypted.  The [``DefaultAWSCredentialsProviderChain``](https://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/auth/DefaultAWSCredentialsProviderChain.html) credentials provider is used for credentials and checks the following:

- The ``AWS_ACCESS_KEY_ID`` and ``AWS_SECRET_ACCESS_KEY`` environment variables
- The ``aws.accessKeyId`` and ``aws.secretKey`` Java System properties
- Credential profiles file at the default location (````~/.aws/credentials````) used by the AWS CLI
- Instance profile credentials delivered through the Amazon EC2 metadata service

</br>
The following folder structure will be created in S3:

```
s3://bucket_name/username/notebook-id/
```

Configure by setting environment variables in the file **zeppelin-env.sh**:

```
export ZEPPELIN_NOTEBOOK_S3_BUCKET = bucket_name
export ZEPPELIN_NOTEBOOK_S3_USER = username
```

Or using the file **zeppelin-site.xml** uncomment and complete the S3 settings:

```
<property>
  <name>zeppelin.notebook.s3.bucket</name>
  <value>bucket_name</value>
  <description>bucket name for notebook storage</description>
</property>
<property>
  <name>zeppelin.notebook.s3.user</name>
  <value>username</value>
  <description>user name for s3 folder structure</description>
</property>
```

Uncomment the next property for use S3NotebookRepo class:

```
<property>
  <name>zeppelin.notebook.storage</name>
  <value>org.apache.zeppelin.notebook.repo.S3NotebookRepo</value>
  <description>notebook persistence layer implementation</description>
</property>
```

Comment out the next property to disable local git notebook storage (the default):

```
<property>
  <name>zeppelin.notebook.storage</name>
  <value>org.apache.zeppelin.notebook.repo.GitNotebookRepo</value>
  <description>versioned notebook persistence layer implementation</description>
</property>
```

### Data Encryption in S3

#### AWS KMS encryption keys

To use an [AWS KMS](https://aws.amazon.com/kms/) encryption key to encrypt notebooks, set the following environment variable in the file **zeppelin-env.sh**:

```
export ZEPPELIN_NOTEBOOK_S3_KMS_KEY_ID = kms-key-id
```

Or using the following setting in **zeppelin-site.xml**:

```
<property>
  <name>zeppelin.notebook.s3.kmsKeyID</name>
  <value>AWS-KMS-Key-UUID</value>
  <description>AWS KMS key ID used to encrypt notebook data in S3</description>
</property>
```

In order to set custom KMS key region, set the following environment variable in the file **zeppelin-env.sh**:

```
export ZEPPELIN_NOTEBOOK_S3_KMS_KEY_REGION = kms-key-region
```

Or using the following setting in **zeppelin-site.xml**:

```
<property>
  <name>zeppelin.notebook.s3.kmsKeyRegion</name>
  <value>target-region</value>
  <description>AWS KMS key region in your AWS account</description>
</property>
```
Format of `target-region` is described in more details [here](http://docs.aws.amazon.com/general/latest/gr/rande.html#kms_region) in second `Region` column (e.g. `us-east-1`).

#### Custom Encryption Materials Provider class

You may use a custom [``EncryptionMaterialsProvider``](https://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/services/s3/model/EncryptionMaterialsProvider.html) class as long as it is available in the classpath and able to initialize itself from system properties or another mechanism.  To use this, set the following environment variable in the file **zeppelin-env.sh**:


```
export ZEPPELIN_NOTEBOOK_S3_EMP = class-name
```

Or using the following setting in **zeppelin-site.xml**:

```
<property>
  <name>zeppelin.notebook.s3.encryptionMaterialsProvider</name>
  <value>provider implementation class name</value>
  <description>Custom encryption materials provider used to encrypt notebook data in S3</description>
```   

#### Enable server-side encryption

To request server-side encryption of notebooks, set the following environment variable in the file **zeppelin-env.sh**:

```
export ZEPPELIN_NOTEBOOK_S3_SSE = true
```

Or using the following setting in **zeppelin-site.xml**:

```
<property>
  <name>zeppelin.notebook.s3.sse</name>
  <value>true</value>
  <description>Server-side encryption enabled for notebooks</description>
</property>
```

</br>
## Notebook Storage in Azure <a name="Azure"></a>

Using `AzureNotebookRepo` you can connect your Zeppelin with your Azure account for notebook storage.

First of all, input your `AccountName`, `AccountKey`, and `Share Name` in the file **zeppelin-site.xml** by commenting out and completing the next properties:

```
<property>
  <name>zeppelin.notebook.azure.connectionString</name>
  <value>DefaultEndpointsProtocol=https;AccountName=<accountName>;AccountKey=<accountKey></value>
  <description>Azure account credentials</description>
</property>

<property>
  <name>zeppelin.notebook.azure.share</name>
  <value>zeppelin</value>
  <description>share name for notebook storage</description>
</property>
```

Secondly, you can initialize `AzureNotebookRepo` class in the file **zeppelin-site.xml** by commenting the next property:

```
<property>
  <name>zeppelin.notebook.storage</name>
  <value>org.apache.zeppelin.notebook.repo.GitNotebookRepo</value>
  <description>versioned notebook persistence layer implementation</description>
</property>
```

and commenting out:

```
<property>
  <name>zeppelin.notebook.storage</name>
  <value>org.apache.zeppelin.notebook.repo.AzureNotebookRepo</value>
  <description>notebook persistence layer implementation</description>
</property>
```

In case you want to use simultaneously your local git storage with Azure storage use the following property instead:

 ```
<property>
  <name>zeppelin.notebook.storage</name>
  <value>org.apache.zeppelin.notebook.repo.GitNotebookRepo, apache.zeppelin.notebook.repo.AzureNotebookRepo</value>
  <description>notebook persistence layer implementation</description>
</property>
```

Optionally, you can specify Azure folder structure name in the file **zeppelin-site.xml** by commenting out the next property:

 ```
 <property>
  <name>zeppelin.notebook.azure.user</name>
  <value>user</value>
  <description>optional user name for Azure folder structure</description>
</property>
```

</br>
## Notebook Storage in ZeppelinHub  <a name="ZeppelinHub"></a>

ZeppelinHub storage layer allows out of the box connection of Zeppelin instance with your ZeppelinHub account. First of all, you need to either comment out the following  property in **zeppelin-site.xml**:

```
<!-- For connecting your Zeppelin with ZeppelinHub -->
<!--
<property>
  <name>zeppelin.notebook.storage</name>
  <value>org.apache.zeppelin.notebook.repo.GitNotebookRepo, org.apache.zeppelin.notebook.repo.zeppelinhub.ZeppelinHubRepo</value>
  <description>two notebook persistence layers (local + ZeppelinHub)</description>
</property>
-->
```

or set the environment variable in the file **zeppelin-env.sh**:

```
export ZEPPELIN_NOTEBOOK_STORAGE="org.apache.zeppelin.notebook.repo.GitNotebookRepo, org.apache.zeppelin.notebook.repo.zeppelinhub.ZeppelinHubRepo"
```

Secondly, you need to set the environment variables in the file **zeppelin-env.sh**:

```
export ZEPPELINHUB_API_TOKEN = ZeppelinHub token
export ZEPPELINHUB_API_ADDRESS = address of ZeppelinHub service (e.g. https://www.zeppelinhub.com)
```

You can get more information on generating `token` and using authentication on the corresponding [help page](http://help.zeppelinhub.com/zeppelin_integration/#add-a-new-zeppelin-instance-and-generate-a-token).


## Notebook Storage in MongoDB <a name="MongoDB"></a>
Using `MongoNotebookRepo`, you can store your notebook in [MongoDB](https://www.mongodb.com/).

### Why MongoDB?
* **[High Availability (HA)](https://en.wikipedia.org/wiki/High_availability)** by a [replica set](https://docs.mongodb.com/manual/reference/glossary/#term-replica-set)
* Seperation of storage from server

### How to use
You can use MongoDB as notebook storage by editting `zeppelin-env.sh` or `zeppelin-site.xml`.

#### (Method 1) by editting `zeppelin-env.sh`
Add a line below to `$ZEPPELIN_HOME/conf/zeppelin-env.sh`:

```sh
export ZEPPELIN_NOTEBOOK_STORAGE=org.apache.zeppelin.notebook.repo.MongoNotebookRepo
```

> *NOTE:* The default MongoDB connection URI is `mongodb://localhost`

#### (Method 2) by editting `zeppelin-site.xml`
Or, **uncomment** lines below at `$ZEPPELIN_HOME/conf/zeppelin-site.xml`:

```xml
<property>
  <name>zeppelin.notebook.storage</name>
  <value>org.apache.zeppelin.notebook.repo.MongoNotebookRepo</value>
  <description>notebook persistence layer implementation</description>
</property>
```

And **comment** lines below:

```xml
<property>
  <name>zeppelin.notebook.storage</name>
  <value>org.apache.zeppelin.notebook.repo.GitNotebookRepo</value>
  <description>versioned notebook persistence layer implementation</description>
</property>
```

### Configurable Options

You can configure options below in `zeppelin-env.sh`.

* `ZEPPELIN_NOTEBOOK_MONGO_URI` [MongoDB connection URI](https://docs.mongodb.com/manual/reference/connection-string/) used to connect to a MongoDB database server
* `ZEPPELIN_NOTEBOOK_MONGO_DATABASE` Database name
* `ZEPPELIN_NOTEBOOK_MONGO_COLLECTION` Collection name
* `ZEPPELIN_NOTEBOOK_MONGO_AUTOIMPORT` If `true`, import local notes (refer to description below for details)

Or, you can configure them in `zeppelin-site.xml`. Corresponding option names as follows:

* `zeppelin.notebook.mongo.uri`
* `zeppelin.notebook.mongo.database`
* `zeppelin.notebook.mongo.collection`
* `zeppelin.notebook.mongo.autoimport`

#### Example configurations in `zeppelin-env.sh`

```sh
export ZEPPELIN_NOTEBOOK_MONGO_URI=mongodb://db1.example.com:27017
export ZEPPELIN_NOTEBOOK_MONGO_DATABASE=myfancy
export ZEPPELIN_NOTEBOOK_MONGO_COLLECTION=notebook
export ZEPPELIN_NOTEBOOK_MONGO_AUTOIMPORT=true
```

#### Import your local notes automatically
By setting `ZEPPELIN_NOTEBOOK_MONGO_AUTOIMPORT` as `true` (default `false`), you can import your local notes automatically when Zeppelin daemon starts up. This feature is for easy migration from local file system storage to MongoDB storage. A note with ID already existing in the collection will not be imported.
