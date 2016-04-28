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
# Notebook Authorization

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
