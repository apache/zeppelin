---
layout: page
title: "Data Source Authorization in Apache Zeppelin"
description: "Apache Zeppelin supports protected data sources. In case of a MySql database, every users can set up their own credentials to access it."
group: setup/security
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

# Data Source Authorization in Apache Zeppelin

<div id="toc"></div>

## Overview

Data source authorization involves authenticating to the data source like a Mysql database and letting it determine user permissions.
Apache Zeppelin allows users to use their own credentials to authenticate with **Data Sources**.

For example, let's assume you have an account in the Vertica databases with credentials. 
You might want to use this account to create a JDBC connection instead of a shared account with all users who are defined in `conf/shiro.ini`. 
In this case, you can add your credential information to Apache Zeppelin and use them with below simple steps.  

## How to save the credential information?
You can add new credentials in the dropdown menu for your data source which can be passed to interpreters. 

<img class="img-responsive" src="{{BASE_PATH}}/assets/themes/zeppelin/img/docs-img/credential_tab.png" width="180px"/>

**Entity** can be the key that distinguishes each credential sets.(We suggest that the convention of the **Entity** is `[Interpreter Group].[Interpreter Name]`.)
Please see [what is interpreter group](../../usage/interpreter/overview.html#what-is-interpreter-group) for the detailed information.

Type **Username & Password** for your own credentials. ex) Mysql user & password of the JDBC Interpreter.

<img class="img-responsive" src="{{BASE_PATH}}/assets/themes/zeppelin/img/docs-img/add_credential.png" />

The credentials saved as per users defined in `conf/shiro.ini`.
If you didn't activate [shiro authentication in Apache Zeppelin](./shiro_authentication.html), your credential information will be saved as `anonymous`.
All credential information also can be found in `conf/credentials.json`. 

#### JDBC interpreter
You need to maintain per-user connection pools.
The interpret method takes the user string as a parameter and executes the jdbc call using a connection in the user's connection pool.

#### Presto 
You don't need a password if the Presto DB server runs backend code using HDFS authorization for the user.

#### Vertica and Mysql 
You have to store the password information for users.

## Please note
As a first step of data source authentication feature, [ZEPPELIN-828](https://issues.apache.org/jira/browse/ZEPPELIN-828) was proposed and implemented in Pull Request [#860](https://github.com/apache/zeppelin/pull/860).
Currently, only customized 3rd party interpreters can use this feature. We are planning to apply this mechanism to [the community managed interpreters](../../usage/interpreter/installation.html#available-community-managed-interpreters) in the near future. 
Please keep track [ZEPPELIN-1070](https://issues.apache.org/jira/browse/ZEPPELIN-1070). 
