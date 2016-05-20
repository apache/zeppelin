---
layout: page
title: "Shiro Security for Apache Zeppelin"
description: ""
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
{% include JB/setup %}

# Shiro authentication for Apache Zeppelin
[Apache Shiro](http://shiro.apache.org/) is a powerful and easy-to-use Java security framework that performs authentication, authorization, cryptography, and session management. In this documentation, we will explain step by step how Shiro works for Zeppelin notebook authentication.

When you connect to Apache Zeppelin, you will be asked to enter your credentials. Once you logged in, then you have access to all notes including other user's notes.

## Security Setup
You can setup **Zeppelin notebook authentication** in some simple steps.

####1. Secure the HTTP channel
To secure the HTTP channel, you have to change both **anon** and **authcBasic** settings in `conf/shiro.ini`. In here, **anon** means "the access is anonymous" and **authcBasic** means "basic auth security".

The default status of them is

```
/** = anon
#/** = authcBasic
```
Deactivate the line "/** = anon" and activate the line "/** = authcBasic" in `conf/shiro.ini` file.

```
#/** = anon
/** = authcBasic
```

For the further information about  `shiro.ini` file format, please refer to [Shiro Configuration](http://shiro.apache.org/configuration.html#Configuration-INISections).

####2. Secure the Websocket channel
Set to property **zeppelin.anonymous.allowed** to **false** in `conf/zeppelin-site.xml`. If you don't have this file yet, just copy `conf/zeppelin-site.xml.template` to `conf/zeppelin-site.xml`.

####3. Start Zeppelin

```
bin/zeppelin-daemon.sh start (or restart)
```

Then you can browse Zeppelin at [http://localhost:8080](http://localhost:8080).

####4. Login
Finally, you can login using one of the below **username/password** combinations.

<center><img src="../assets/themes/zeppelin/img/docs-img/zeppelin-login.png"></center>

```
admin = password1
user1 = password2
user2 = password3
```

Those combinations are defined in the `conf/shiro.ini` file.

> **NOTE :** This documentation is originally from [SECURITY-README.md](https://github.com/apache/incubator-zeppelin/blob/master/SECURITY-README.md).
