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
  * storage using Google Cloud Storage - `GCSNotebookRepo`
  * storage using Aliyun OSS - `OSSNotebookRepo`
  * storage using MongoDB - `MongoNotebookRepo`
  * storage using GitHub - `GitHubNotebookRepo`

Multiple storage systems can be used at the same time by providing a comma-separated list of the class-names in the configuration.
By default, only first two of them will be automatically kept in sync by Zeppelin.

</br>

## Notebook Storage in local Git repository <a name="Git"></a>

To enable versioning for all your local notebooks though a standard Git repository - uncomment the next property in `zeppelin-site.xml` in order to use GitNotebookRepo class:

```xml
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

```xml
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

```bash
export ZEPPELIN_NOTEBOOK_S3_BUCKET=bucket_name
export ZEPPELIN_NOTEBOOK_S3_USER=username
```

Or using the file **zeppelin-site.xml** uncomment and complete the S3 settings:

```xml
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

```xml
<property>
  <name>zeppelin.notebook.storage</name>
  <value>org.apache.zeppelin.notebook.repo.S3NotebookRepo</value>
  <description>notebook persistence layer implementation</description>
</property>
```

Comment out the next property to disable local git notebook storage (the default):

```xml
<property>
  <name>zeppelin.notebook.storage</name>
  <value>org.apache.zeppelin.notebook.repo.GitNotebookRepo</value>
  <description>versioned notebook persistence layer implementation</description>
</property>
```

### Data Encryption in S3

#### AWS KMS encryption keys

To use an [AWS KMS](https://aws.amazon.com/kms/) encryption key to encrypt notebooks, set the following environment variable in the file **zeppelin-env.sh**:

```bash
export ZEPPELIN_NOTEBOOK_S3_KMS_KEY_ID=kms-key-id
```

Or using the following setting in **zeppelin-site.xml**:

```xml
<property>
  <name>zeppelin.notebook.s3.kmsKeyID</name>
  <value>AWS-KMS-Key-UUID</value>
  <description>AWS KMS key ID used to encrypt notebook data in S3</description>
</property>
```

In order to set custom KMS key region, set the following environment variable in the file **zeppelin-env.sh**:

```bash
export ZEPPELIN_NOTEBOOK_S3_KMS_KEY_REGION=kms-key-region
```

Or using the following setting in **zeppelin-site.xml**:

```xml
<property>
  <name>zeppelin.notebook.s3.kmsKeyRegion</name>
  <value>target-region</value>
  <description>AWS KMS key region in your AWS account</description>
</property>
```
Format of `target-region` is described in more details [here](http://docs.aws.amazon.com/general/latest/gr/rande.html#kms_region) in second `Region` column (e.g. `us-east-1`).

#### Custom Encryption Materials Provider class

You may use a custom [``EncryptionMaterialsProvider``](https://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/services/s3/model/EncryptionMaterialsProvider.html) class as long as it is available in the classpath and able to initialize itself from system properties or another mechanism.  To use this, set the following environment variable in the file **zeppelin-env.sh**:


```bash
export ZEPPELIN_NOTEBOOK_S3_EMP=class-name
```

Or using the following setting in **zeppelin-site.xml**:

```xml
<property>
  <name>zeppelin.notebook.s3.encryptionMaterialsProvider</name>
  <value>provider implementation class name</value>
  <description>Custom encryption materials provider used to encrypt notebook data in S3</description>
```   

#### Enable server-side encryption

To request server-side encryption of notebooks, set the following environment variable in the file **zeppelin-env.sh**:

```bash
export ZEPPELIN_NOTEBOOK_S3_SSE=true
```

Or using the following setting in **zeppelin-site.xml**:

```xml
<property>
  <name>zeppelin.notebook.s3.sse</name>
  <value>true</value>
  <description>Server-side encryption enabled for notebooks</description>
</property>
```

</br>

### S3 Object Permissions

S3 allows writing objects into buckets owned by a different account than the requestor, when this happens S3 by default does not grant the bucket owner permissions to the written object. Setting the Canned ACL when communicating with S3 determines the permissions of notebooks saved in S3. Allowed values for Canned ACL are found [here](https://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/services/s3/model/CannedAccessControlList.html), the most frequent value is "BucketOwnerFullControl". Set the following environment variable in the file **zeppelin-env.sh**:


```bash
export ZEPPELIN_NOTEBOOK_S3_CANNED_ACL=BucketOwnerFullControl
```

Or using the following setting in **zeppelin-site.xml**:

```xml
<property>
  <name>zeppelin.notebook.s3.cannedAcl</name>
  <value>BucketOwnerFullControl</value>
  <description>Saves notebooks in S3 with the given Canned Access Control List.</description>
</property>
```

</br>

#### S3 Enable Path Style Access

To request path style s3 bucket access, set the following environment variable in the file **zeppelin-env.sh**:

```bash
export ZEPPELIN_NOTEBOOK_S3_PATH_STYLE_ACCESS=true
```

Or using the following setting in **zeppelin-site.xml**:

```xml
<property>
  <name>zeppelin.notebook.s3.pathStyleAccess</name>
  <value>true</value>
  <description>Path Style S3 bucket access enabled for notebook repo</description>
</property>
```

</br>

## Notebook Storage in Azure <a name="Azure"></a>

Using `AzureNotebookRepo` you can connect your Zeppelin with your Azure account for notebook storage.

First of all, input your `AccountName`, `AccountKey`, and `Share Name` in the file **zeppelin-site.xml** by commenting out and completing the next properties:

```xml
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

```xml
<property>
  <name>zeppelin.notebook.storage</name>
  <value>org.apache.zeppelin.notebook.repo.GitNotebookRepo</value>
  <description>versioned notebook persistence layer implementation</description>
</property>
```

and commenting out:

```xml
<property>
  <name>zeppelin.notebook.storage</name>
  <value>org.apache.zeppelin.notebook.repo.AzureNotebookRepo</value>
  <description>notebook persistence layer implementation</description>
</property>
```

In case you want to use simultaneously your local git storage with Azure storage use the following property instead:

 ```xml
<property>
  <name>zeppelin.notebook.storage</name>
  <value>org.apache.zeppelin.notebook.repo.GitNotebookRepo, apache.zeppelin.notebook.repo.AzureNotebookRepo</value>
  <description>notebook persistence layer implementation</description>
</property>
```

Optionally, you can specify Azure folder structure name in the file **zeppelin-site.xml** by commenting out the next property:

 ```xml
 <property>
  <name>zeppelin.notebook.azure.user</name>
  <value>user</value>
  <description>optional user name for Azure folder structure</description>
</property>
```

</br>

## Notebook Storage in Google Cloud Storage <a name="GCS"></a>

Using `GCSNotebookRepo` you can connect Zeppelin with Google Cloud Storage using [Application Default Credentials](https://cloud.google.com/docs/authentication/production).

First, choose a GCS path under which to store notebooks.

```xml
<property>
  <name>zeppelin.notebook.gcs.dir</name>
  <value></value>
  <description>
    A GCS path in the form gs://bucketname/path/to/dir.
    Notes are stored at {zeppelin.notebook.gcs.dir}/{notebook-name}_{notebook-id}.zpln
 </description>
</property>
```

Then, initialize the `GCSNotebookRepo` class in the file **zeppelin-site.xml** by uncommenting:

```xml
<property>
  <name>zeppelin.notebook.storage</name>
  <value>org.apache.zeppelin.notebook.repo.GCSNotebookRepo</value>
  <description>notebook persistence layer implementation</description>
</property>
```

and commenting out:

```xml
<property>
  <name>zeppelin.notebook.storage</name>
  <value>org.apache.zeppelin.notebook.repo.GitNotebookRepo</value>
  <description>versioned notebook persistence layer implementation</description>
</property>
```

Or, if you want to simultaneously use your local git storage with GCS, use the following property instead:

```xml
<property>
  <name>zeppelin.notebook.storage</name>
  <value>org.apache.zeppelin.notebook.repo.GitNotebookRepo,org.apache.zeppelin.notebook.repo.GCSNotebookRepo</value>
  <description>notebook persistence layer implementation</description>
</property>
```

### Google Cloud API Authentication

Note: On Google App Engine, Google Cloud Shell, and Google Compute Engine, these
steps are not necessary if you are using the default built in service account.

For more information, see [Application Default Credentials](https://cloud.google.com/docs/authentication/production)

#### Using gcloud auth application-default login

See the [gcloud docs](https://cloud.google.com/sdk/gcloud/reference/auth/application-default/login)

As the user running the zeppelin daemon, run:

```bash
gcloud auth application-default login
```

You can also use `--scopes` to restrict access to specific Google APIs, such as
Cloud Storage and BigQuery.

#### Using service account key files

Alternatively, to use a [service account](https://cloud.google.com/compute/docs/access/service-accounts)
for authentication with GCS, you will need a JSON service account key file.

1. Navigate to the [service accounts page](https://console.cloud.google.com/iam-admin/serviceaccounts/project)
2. Click `CREATE SERVICE ACCOUNT`
3. Select at least `Storage -> Storage Object Admin`. Note that this is
   **different** than `Storage Admin`.
4. If you are also using the BigQuery Interpreter, add the appropriate
   permissions (e.g. `Bigquery -> Bigquery Data Viewer and BigQuery User`)
5. Name your service account, and select "Furnish a new private key" to download
   a `.json` file. Click "Create".
6. Move the downloaded file to a location of your choice (e.g.
   `/path/to/my/key.json`), and give it appropriate permissions. Ensure at
   least the user running the zeppelin daemon can read it.

 If you wish to set this as your default credential file to access Google Services,
 point `GOOGLE_APPLICATION_CREDENTIALS` at your new key file in **zeppelin-env.sh**. For example:

```bash
export GOOGLE_APPLICATION_CREDENTIALS=/path/to/my/key.json
```
If you do not want to use this key file as default credential file and want to specify a custom key
file for authentication with GCS, update the following property :

```xml
<property>
  <name>zeppelin.notebook.google.credentialsJsonFilePath</name>
  <value>path/to/key.json</value>
  <description>
    Path to GCS credential key file for authentication with Google Storage.
 </description>
</property>
```


</br>

## Notebook Storage in OSS <a name="OSS"></a>

Notebooks may be stored in Aliyun OSS.

</br>
The following folder structure will be created in OSS:

```
oss://bucket_name/{noteboo_dir}/note_path
```


And you should configure oss related properties in file **zeppelin-site.xml**.

```xml
<property>
  <name>zeppelin.notebook.oss.bucket</name>
  <value>zeppelin</value>
  <description>bucket name for notebook storage</description>
</property>

<property>
  <name>zeppelin.notebook.oss.endpoint</name>
  <value>http://oss-cn-hangzhou.aliyuncs.com</value>
  <description>endpoint for oss bucket</description>
</property>

<property>
  <name>zeppelin.notebook.oss.accesskeyid</name>
  <value></value>
  <description>Access key id for your OSS account</description>
</property>

<property>
  <name>zeppelin.notebook.oss.accesskeysecret</name>
  <value></value>
  <description>Access key secret for your OSS account</description>
</property>

```

Uncomment the next property for use OSSNotebookRepo class:

```xml
<property>
  <name>zeppelin.notebook.storage</name>
  <value>org.apache.zeppelin.notebook.repo.OSSNotebookRepo</value>
  <description>notebook persistence layer implementation</description>
</property>
```

## Notebook Storage in MongoDB <a name="MongoDB"></a>
Using `MongoNotebookRepo`, you can store your notebook in [MongoDB](https://www.mongodb.com/).

### Why MongoDB?
* **[High Availability (HA)](https://en.wikipedia.org/wiki/High_availability)** by a [replica set](https://docs.mongodb.com/manual/reference/glossary/#term-replica-set)
* Seperation of storage from server

### How to use
You can use MongoDB as notebook storage by editting `zeppelin-env.sh` or `zeppelin-site.xml`.

#### (Method 1) by editting `zeppelin-env.sh`
Add a line below to `$ZEPPELIN_HOME/conf/zeppelin-env.sh`:

```bash
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

## Notebook Storage in GitHub

To enable GitHub tracking, uncomment the following properties in `zeppelin-site.xml`

```xml
<property>
  <name>zeppelin.notebook.git.remote.url</name>
  <value></value>
  <description>remote Git repository URL</description>
</property>

<property>
  <name>zeppelin.notebook.git.remote.username</name>
  <value>token</value>
  <description>remote Git repository username</description>
</property>

<property>
  <name>zeppelin.notebook.git.remote.access-token</name>
  <value></value>
  <description>remote Git repository password</description>
</property>

<property>
  <name>zeppelin.notebook.git.remote.origin</name>
  <value>origin</value>
  <description>Git repository remote</description>
</property>
```

And set the `zeppelin.notebook.storage` propery to `org.apache.zeppelin.notebook.repo.GitHubNotebookRepo`

```xml
<property>
  <name>zeppelin.notebook.storage</name>
  <value>org.apache.zeppelin.notebook.repo.GitHubNotebookRepo</value>
</property>
```

The access token could be obtained by following the steps on this link https://github.com/settings/tokens.
