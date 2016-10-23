---
layout: page
title: "Contributing to Apache Zeppelin (Website)"
description: "How can you contribute to Apache Zeppelin project website? This document covers from building Zeppelin documentation site to making a pull request on Github."
group: development
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

# Contributing to Apache Zeppelin ( Website )

<div id="toc"></div>


This page will give you an overview of how to build and contribute to the documentation of Apache Zeppelin.
The online documentation at [zeppelin.apache.org](https://zeppelin.apache.org/docs/latest/) is also generated from the files found here.

> **NOTE :** Apache Zeppelin is an [Apache2 License](http://www.apache.org/licenses/LICENSE-2.0.html) Software.
Any contributions to Zeppelin (Source code, Documents, Image, Website) means you agree with license all your contributions as Apache2 License.

## Getting the source code
First of all, you need Zeppelin source code. The official location of Zeppelin is [http://git.apache.org/zeppelin.git](http://git.apache.org/zeppelin.git).
Documentation website is hosted in 'master' branch under `/docs/` dir.

### git access

First of all, you need the website source code. The official location of mirror for Zeppelin is [http://git.apache.org/zeppelin.git](http://git.apache.org/zeppelin.git).
Get the source code on your development machine using git.

```
git clone git://git.apache.org/zeppelin.git
cd docs
```
Apache Zeppelin follows [Fork & Pull](https://github.com/sevntu-checkstyle/sevntu.checkstyle/wiki/Development-workflow-with-Git:-Fork,-Branching,-Commits,-and-Pull-Request) as a source control workflow.
If you want to not only build Zeppelin but also make any changes, then you need to fork [Zeppelin github mirror repository](https://github.com/apache/zeppelin) and make a pull request.

### Build

You'll need to install some prerequisites to build the code. Please check [Build documentation](https://github.com/apache/zeppelin/blob/master/docs/README.md#build-documentation) section in [docs/README.md](https://github.com/apache/zeppelin/blob/master/docs/README.md).

### Run website in development mode

While you're modifying website, you might want to see preview of it. Please check [Run website](https://github.com/apache/zeppelin/blob/master/docs/README.md#run-website) section in [docs/README.md](https://github.com/apache/zeppelin/blob/master/docs/README.md).
Then you'll be able to access it on [http://localhost:4000](http://localhost:4000) with your web browser.

### Making a Pull Request

When you are ready, just make a pull-request.


## Alternative way

You can directly edit `.md` files in `/docs/` directory at the web interface of github and make pull-request immediately.

## Stay involved
Contributors should join the Zeppelin mailing lists.

* [dev@zeppelin.apache.org](http://mail-archives.apache.org/mod_mbox/zeppelin-dev/) is for people who want to contribute code to Zeppelin. [subscribe](mailto:dev-subscribe@zeppelin.apache.org?subject=send this email to subscribe), [unsubscribe](mailto:dev-unsubscribe@zeppelin.apache.org?subject=send this email to unsubscribe), [archives](http://mail-archives.apache.org/mod_mbox/zeppelin-dev/)

If you have any issues, create a ticket in [JIRA](https://issues.apache.org/jira/browse/ZEPPELIN).
