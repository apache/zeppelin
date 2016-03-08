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
# Interpreter and Data Source Authorization

## Interpreter Authorization

Interpreter authorization involves permissions like creating an interpreter and execution queries using it.

## Data Source Authorization

Data source authorization involves authenticating to the data source like a Mysql database and letting it determine user permissions.

For the Hive interpreter, we need to maintain per-user connection pools.
The interpret method takes the user string as parameter and executes the jdbc call using a connection in the user's connection pool.

In case of Presto, we don't need password if the Presto DB server runs backend code using HDFS authorization for the user.
For databases like Vertica and Mysql we have to store password information for users.
