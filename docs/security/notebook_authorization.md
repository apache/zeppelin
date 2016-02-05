---
layout: page
title: "Notebook Authorization"
description: "Notebook Authorization"
group: security
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
# Zeppelin Notebook Authorization

## Overview
There are different aspects to Zeppelin security:

* Authentication: is the user who they say they are?
* Notebook authorization: does the user have permissions to read or write to a note?
* Interpreter authorization: does the user have permissions to perform interpreter operations e.g. access data source objects?


## Authentication

Authentication is company-specific. One option is to have an authentication server that can verify user credentials in an LDAP server.
If an incoming request to the Zeppelin server does not have a cookie with user information encrypted with the authentication server public key, the user
is redirected to the authentication server. Once the user is verified, the authentication server redirects the browser to a specific 
URL in the Zeppelin server which sets the authentication cookie in the browser. 
The end result is that all requests to the Zeppelin
web server have the authentication cookie which contains user and groups information.


## Notebook Authorization

We assume that there is an authentication component that associates a user string and a set of group strings with every NotebookSocket.

Each note has the following:
* set of owner entities (users or groups)
* set of reader entities (users or groups)
* set of writer entities (users or groups)

If a set is empty, it means that any user can perform that operation.

The NotebookServer classifies every Note operation into three categories: read, write, manage.
Before executing a Note operation, it checks if the user and the groups associated with the NotebookSocket have permissions. For example, before executing an read
operation, it checks if the user and the groups have at least one entity that belongs to the reader entities.

To initialize and modify note permissions, we provide UI like "Interpreter binding". The user inputs comma separated entities for owners, readers and writers.
We execute a rest api call with this information. In the backend we get the user information for the connection and allow the operation if the user and groups 
associated with the current user have at least one entity that belongs to owner entities for the note.

## Interpreter Authorization
The Interpreter authorization problem is more complex. Different interpreters require different strategies.

For the Hive interpreter, we need to maintain per-user connection pools.
The interpreter method takes the user string as parameter and executes the jdbc call using a connection in the user's connection pool.

In case of Presto, we don't need password if the Presto DB server runs backend code using HDFS authorization for the user.
For databases like Vertica and Mysql we would have to store password information for users.
