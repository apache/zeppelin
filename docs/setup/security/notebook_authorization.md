---
layout: page
title: "Notebook Authorization in Apache Zeppelin"
description: "This page will guide you how you can set the permission for Zeppelin notebooks. This document assumes that Apache Shiro authentication was set up."
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

# Zeppelin Notebook Authorization

<div id="toc"></div>

## Overview
We assume that there is an **Shiro Authentication** component that associates a user string and a set of group strings with every NotebookSocket.
If you don't set the authentication components yet, please check [Shiro authentication for Apache Zeppelin](./shiro_authentication.html) first.

## Authorization Setting
You can set Zeppelin notebook permissions in each notebooks. Of course only **notebook owners** can change this configuration.
Just click **Lock icon** and open the permission setting page in your notebook.

As you can see, each Zeppelin notebooks has 3 entities :

* Owners ( users or groups )
* Readers ( users or groups )
* Writers ( users or groups )
* Runners ( users or groups )

<center><img src="{{BASE_PATH}}/assets/themes/zeppelin/img/docs-img/permission_setting.png"></center>

Fill out the each forms with comma seperated **users** and **groups** configured in `conf/shiro.ini` file.
If the form is empty (*), it means that any users can perform that operation.

If someone who doesn't have **read** permission is trying to access the notebook or someone who doesn't have **write** permission is trying to edit the notebook,
or someone who doesn't have **run** permission is trying to run a paragraph Zeppelin will ask to login or block the user.

By default, owners and writers have **write** permission, owners, writers and runners have **run** permission, owners, writers, runners and readers have **read** permission

<center><img src="{{BASE_PATH}}/assets/themes/zeppelin/img/docs-img/insufficient_privileges.png"></center>

## Separate notebook workspaces (public vs. private)
By default, the authorization rights allow other users to see the newly created note, meaning the workspace is `public`. This behavior is controllable and can be set through either `ZEPPELIN_NOTEBOOK_PUBLIC` variable in `conf/zeppelin-env.sh`, or through `zeppelin.notebook.public` property in `conf/zeppelin-site.xml`. Thus, in order to make newly created note appear only in your `private` workspace by default, you can set either `ZEPPELIN_NOTEBOOK_PUBLIC` to `false` in your `conf/zeppelin-env.sh` as follows:

```
export ZEPPELIN_NOTEBOOK_PUBLIC="false"
```

or set `zeppelin.notebook.public` property to `false` in `conf/zeppelin-site.xml` as follows:

```
<property>
  <name>zeppelin.notebook.public</name>
  <value>false</value>
  <description>Make notebook public by default when created, private otherwise</description>
</property>
```

Behind the scenes, when you create a new note only the `owners` field is filled with current user, leaving `readers`, `runners` and `writers` fields empty. All the notes with at least one empty authorization field are considered to be in `public` workspace. Thus when setting `zeppelin.notebook.public` (or corresponding `ZEPPELIN_NOTEBOOK_PUBLIC`) to false, newly created notes have `readers`, `runners`, `writers` fields filled with current user, making note appear as in `private` workspace.

## How it works
In this section, we will explain the detail about how the notebook authorization works in backend side.

### NotebookServer
The [NotebookServer](https://github.com/apache/zeppelin/blob/master/zeppelin-server/src/main/java/org/apache/zeppelin/socket/NotebookServer.java) classifies every notebook operations into three categories: **Read**, **Run**, **Write**, **Manage**.
Before executing a notebook operation, it checks if the user and the groups associated with the `NotebookSocket` have permissions.
For example, before executing a **Read** operation, it checks if the user and the groups have at least one entity that belongs to the **Reader** entities.

### Notebook REST API call
Zeppelin executes a [REST API call](https://github.com/apache/zeppelin/blob/master/zeppelin-server/src/main/java/org/apache/zeppelin/rest/NotebookRestApi.java) for the notebook permission information.
In the backend side, Zeppelin gets the user information for the connection and allows the operation if the users and groups
associated with the current user have at least one entity that belongs to owner entities for the notebook.
