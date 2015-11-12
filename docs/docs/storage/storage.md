---
layout: page
title: "Storage"
description: "Notebook Storage option for Zeppelin"
group: storage
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
### Notebook Storage

In Zeppelin there are two option for storage Notebook, by default the notebook is storage in the notebook folder in your local File System and the second option is S3.

</br>
#### Notebook Storage in S3

For notebook storage in S3 you need the AWS credentials, for this there are three options, the enviroment variable ```AWS_ACCESS_KEY_ID``` and ```AWS_ACCESS_SECRET_KEY```,  credentials file in the folder .aws in you home and IAM role for your instance. For complete the need steps is necessary:

</br>
you need the following folder structure on S3

```
bucket_name/
  username/
    notebook/

```

set the enviroment variable in the file **zeppelin-env.sh**:

```
export ZEPPELIN_NOTEBOOK_S3_BUCKET = bucket_name
export ZEPPELIN_NOTEBOOK_S3_USER = username
```

in the file **zeppelin-site.xml** uncommet and complete the next property:

```
<!--If used S3 to storage, it is necessary the following folder structure bucket_name/username/notebook/-->
<property>
  <name>zeppelin.notebook.s3.user</name>
  <value>username</value>
  <description>user name for s3 folder structure</description>
</property>
<property>
  <name>zeppelin.notebook.s3.bucket</name>
  <value>bucket_name</value>
  <description>bucket name for notebook storage</description>
</property>
```

uncomment the next property for use S3NotebookRepo class:

```
<property>
  <name>zeppelin.notebook.storage</name>
  <value>org.apache.zeppelin.notebook.repo.S3NotebookRepo</value>
  <description>notebook persistence layer implementation</description>
</property>
```

comment the next property:

```
<property>
  <name>zeppelin.notebook.storage</name>
  <value>org.apache.zeppelin.notebook.repo.VFSNotebookRepo</value>
  <description>notebook persistence layer implementation</description>
</property>
```   
