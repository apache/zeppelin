---
layout: page
title: "Download"
description: ""
group:
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

# Download Apache Zeppelin

The latest release of Apache Zeppelin is **0.8.1**.

  - 0.8.1 released on Jan 23, 2019 ([release notes](./releases/zeppelin-release-0.8.1.html)) ([git tag](https://git-wip-us.apache.org/repos/asf?p=zeppelin.git;h=refs/tags/v0.8.1))

    * Binary package with all interpreters ([Install guide](../../docs/0.8.1/quickstart/install.html)):
    <p><div class="btn btn-md btn-primary" onclick="ga('send', 'event', 'download', 'zeppelin-bin-all', '0.8.1'); window.location.href='http://www.apache.org/dyn/closer.cgi/zeppelin/zeppelin-0.8.1/zeppelin-0.8.1-bin-all.tgz'">zeppelin-0.8.1-bin-all.tgz</div> (947 MB,
    [pgp](https://www.apache.org/dist/zeppelin/zeppelin-0.8.1/zeppelin-0.8.1-bin-all.tgz.asc),
    [md5](https://www.apache.org/dist/zeppelin/zeppelin-0.8.1/zeppelin-0.8.1-bin-all.tgz.md5),
    [sha](https://www.apache.org/dist/zeppelin/zeppelin-0.8.1/zeppelin-0.8.1-bin-all.tgz.sha512))</p>

    * Binary package with Spark interpreter and interpreter net-install script ([interpreter installation guide](../../docs/0.8.1/usage/interpreter/installation.html)):
    <p><div class="btn btn-md btn-primary" onclick="ga('send', 'event', 'download', 'zeppelin-bin-netinst', '0.8.1'); window.location.href='http://www.apache.org/dyn/closer.cgi/zeppelin/zeppelin-0.8.1/zeppelin-0.8.1-bin-netinst.tgz'">zeppelin-0.8.1-bin-netinst.tgz</div> (313 MB,
    [pgp](https://www.apache.org/dist/zeppelin/zeppelin-0.8.1/zeppelin-0.8.1-bin-netinst.tgz.asc),
    [md5](https://www.apache.org/dist/zeppelin/zeppelin-0.8.1/zeppelin-0.8.1-bin-netinst.tgz.md5),
    [sha](https://www.apache.org/dist/zeppelin/zeppelin-0.8.1/zeppelin-0.8.1-bin-netinst.tgz.sha512))</p>

    * Source:
    <a style="cursor:pointer" onclick="ga('send', 'event', 'download', 'zeppelin-src', '0.8.1'); window.location.href='http://www.apache.org/dyn/closer.cgi/zeppelin/zeppelin-0.8.1/zeppelin-0.8.1.tgz'">zeppelin-0.8.1.tgz</a> (117 MB,
    [pgp](https://www.apache.org/dist/zeppelin/zeppelin-0.8.1/zeppelin-0.8.1.tgz.asc),
    [md5](https://www.apache.org/dist/zeppelin/zeppelin-0.8.1/zeppelin-0.8.1.tgz.md5),
    [sha](https://www.apache.org/dist/zeppelin/zeppelin-0.8.1/zeppelin-0.8.1.tgz.sha512))

# Using the official docker image

Make sure that [docker](https://www.docker.com/community-edition) is installed in your local machine.  

Use this command to launch Apache Zeppelin in a container.

```bash
docker run -p 8080:8080 --rm --name zeppelin apache/zeppelin:0.8.1

```
To persist `logs` and `notebook` directories, use the [volume](https://docs.docker.com/engine/reference/commandline/run/#mount-volume--v-read-only) option for docker container.

```bash
docker run -p 8080:8080 --rm -v $PWD/logs:/logs -v $PWD/notebook:/notebook -e ZEPPELIN_LOG_DIR='/logs' -e ZEPPELIN_NOTEBOOK_DIR='/notebook' --name zeppelin apache/zeppelin:0.8.0
```

If you have trouble accessing `localhost:8080` in the browser, Please clear browser cache.

## Verify the integrity of the files

It is essential that you [verify](https://www.apache.org/info/verification.html) the integrity of the downloaded files using the PGP or MD5 signatures. This signature should be matched against the [KEYS](https://www.apache.org/dist/zeppelin/KEYS) file.



## Build from source

For developers, to get latest *0.9.0-SNAPSHOT* check [README](https://github.com/apache/zeppelin/blob/master/README.md).



## Old releases
  
  - 0.8.0 released on June 28, 2018 ([release notes](./releases/zeppelin-release-0.8.0.html)) ([git tag](https://git-wip-us.apache.org/repos/asf?p=zeppelin.git;h=refs/tags/v0.8.0))

    * Binary package with all interpreters ([Install guide](../../docs/0.8.0/quickstart/install.html)):
    <p><div class="btn btn-md btn-primary" onclick="ga('send', 'event', 'download', 'zeppelin-bin-all', '0.8.0'); window.location.href='http://www.apache.org/dyn/closer.cgi/zeppelin/zeppelin-0.8.0/zeppelin-0.8.0-bin-all.tgz'">zeppelin-0.8.0-bin-all.tgz</div> (939 MB,
    [pgp](https://www.apache.org/dist/zeppelin/zeppelin-0.8.0/zeppelin-0.8.0-bin-all.tgz.asc),
    [md5](https://www.apache.org/dist/zeppelin/zeppelin-0.8.0/zeppelin-0.8.0-bin-all.tgz.md5),
    [sha](https://www.apache.org/dist/zeppelin/zeppelin-0.8.0/zeppelin-0.8.0-bin-all.tgz.sha512))</p>

    * Binary package with Spark interpreter and interpreter net-install script ([interpreter installation guide](../../docs/0.8.0/usage/interpreter/installation.html)):
    <p><div class="btn btn-md btn-primary" onclick="ga('send', 'event', 'download', 'zeppelin-bin-netinst', '0.8.0'); window.location.href='http://www.apache.org/dyn/closer.cgi/zeppelin/zeppelin-0.8.0/zeppelin-0.8.0-bin-netinst.tgz'">zeppelin-0.8.0-bin-netinst.tgz</div> (306 MB,
    [pgp](https://www.apache.org/dist/zeppelin/zeppelin-0.8.0/zeppelin-0.8.0-bin-netinst.tgz.asc),
    [md5](https://www.apache.org/dist/zeppelin/zeppelin-0.8.0/zeppelin-0.8.0-bin-netinst.tgz.md5),
    [sha](https://www.apache.org/dist/zeppelin/zeppelin-0.8.0/zeppelin-0.8.0-bin-netinst.tgz.sha512))</p>

    * Source:
    <a style="cursor:pointer" onclick="ga('send', 'event', 'download', 'zeppelin-src', '0.8.0'); window.location.href='http://www.apache.org/dyn/closer.cgi/zeppelin/zeppelin-0.8.0/zeppelin-0.8.0.tgz'">zeppelin-0.8.0.tgz</a> (58 MB,
    [pgp](https://www.apache.org/dist/zeppelin/zeppelin-0.8.0/zeppelin-0.8.0.tgz.asc),
    [md5](https://www.apache.org/dist/zeppelin/zeppelin-0.8.0/zeppelin-0.8.0.tgz.md5),
    [sha](https://www.apache.org/dist/zeppelin/zeppelin-0.8.0/zeppelin-0.8.0.tgz.sha512))

  - 0.7.3 released on Sep 21, 2017 ([release notes](./releases/zeppelin-release-0.7.3.html)) ([git tag](https://git-wip-us.apache.org/repos/asf?p=zeppelin.git;h=refs/tags/v0.7.3))

    * Binary package with all interpreters ([Install guide](../../docs/0.7.3/install/install.html)):
    <p><div class="btn btn-md btn-primary" onclick="ga('send', 'event', 'download', 'zeppelin-bin-all', '0.7.3'); window.location.href='http://www.apache.org/dyn/closer.cgi/zeppelin/zeppelin-0.7.3/zeppelin-0.7.3-bin-all.tgz'">zeppelin-0.7.3-bin-all.tgz</div> (796 MB,
    [pgp](https://www.apache.org/dist/zeppelin/zeppelin-0.7.3/zeppelin-0.7.3-bin-all.tgz.asc),
    [md5](https://www.apache.org/dist/zeppelin/zeppelin-0.7.3/zeppelin-0.7.3-bin-all.tgz.md5),
    [sha](https://www.apache.org/dist/zeppelin/zeppelin-0.7.3/zeppelin-0.7.3-bin-all.tgz.sha512))</p>

    * Binary package with Spark interpreter and interpreter net-install script ([interpreter installation guide](../../docs/0.7.3/manual/interpreterinstallation.html)):
    <p><div class="btn btn-md btn-primary" onclick="ga('send', 'event', 'download', 'zeppelin-bin-netinst', '0.7.3'); window.location.href='http://www.apache.org/dyn/closer.cgi/zeppelin/zeppelin-0.7.3/zeppelin-0.7.3-bin-netinst.tgz'">zeppelin-0.7.3-bin-netinst.tgz</div> (274 MB,
    [pgp](https://www.apache.org/dist/zeppelin/zeppelin-0.7.3/zeppelin-0.7.3-bin-netinst.tgz.asc),
    [md5](https://www.apache.org/dist/zeppelin/zeppelin-0.7.3/zeppelin-0.7.3-bin-netinst.tgz.md5),
    [sha](https://www.apache.org/dist/zeppelin/zeppelin-0.7.3/zeppelin-0.7.3-bin-netinst.tgz.sha512))</p>

    * Source:
    <a style="cursor:pointer" onclick="ga('send', 'event', 'download', 'zeppelin-src', '0.7.3'); window.location.href='http://www.apache.org/dyn/closer.cgi/zeppelin/zeppelin-0.7.3/zeppelin-0.7.3.tgz'">zeppelin-0.7.3.tgz</a> (1.9 MB,
    [pgp](https://www.apache.org/dist/zeppelin/zeppelin-0.7.3/zeppelin-0.7.3.tgz.asc),
    [md5](https://www.apache.org/dist/zeppelin/zeppelin-0.7.3/zeppelin-0.7.3.tgz.md5),
    [sha](https://www.apache.org/dist/zeppelin/zeppelin-0.7.3/zeppelin-0.7.3.tgz.sha512))

  - 0.7.2 released on Jun 12, 2017 ([release notes](./releases/zeppelin-release-0.7.2.html)) ([git tag](https://git-wip-us.apache.org/repos/asf?p=zeppelin.git;h=refs/tags/v0.7.2))

    * Binary package with all interpreters:
    <a style="cursor:pointer" onclick="ga('send', 'event', 'download', 'zeppelin-bin-all', '0.7.2'); window.location.href='http://archive.apache.org/dist/zeppelin/zeppelin-0.7.2/zeppelin-0.7.2-bin-all.tgz'">zeppelin-0.7.2-bin-all.tgz</a> (715 MB,
    [pgp](https://archive.apache.org/dist/zeppelin/zeppelin-0.7.2/zeppelin-0.7.2-bin-all.tgz.asc),
    [md5](https://archive.apache.org/dist/zeppelin/zeppelin-0.7.2/zeppelin-0.7.2-bin-all.tgz.md5),
    [sha](https://archive.apache.org/dist/zeppelin/zeppelin-0.7.2/zeppelin-0.7.2-bin-all.tgz.sha512))

    * Binary package with Spark interpreter and interpreter net-install script ([interpreter installation guide](../../docs/0.7.2/manual/interpreterinstallation.html)):
    <a style="cursor:pointer" onclick="ga('send', 'event', 'download', 'zeppelin-bin-netinst', '0.7.2'); window.location.href='http://archive.apache.org/dist/zeppelin/zeppelin-0.7.2/zeppelin-0.7.2-bin-netinst.tgz'">zeppelin-0.7.2-bin-netinst.tgz</a> (274 MB,
    [pgp](https://archive.apache.org/dist/zeppelin/zeppelin-0.7.2/zeppelin-0.7.2-bin-netinst.tgz.asc),
    [md5](https://archive.apache.org/dist/zeppelin/zeppelin-0.7.2/zeppelin-0.7.2-bin-netinst.tgz.md5),
    [sha](https://archive.apache.org/dist/zeppelin/zeppelin-0.7.2/zeppelin-0.7.2-bin-netinst.tgz.sha512))

    * Source:
    <a style="cursor:pointer" onclick="ga('send', 'event', 'download', 'zeppelin-src', '0.7.2'); window.location.href='http://archive.apache.org/dist/zeppelin/zeppelin-0.7.2/zeppelin-0.7.2.tgz'">zeppelin-0.7.2.tgz</a> (1.9 MB,
    [pgp](https://archive.apache.org/dist/zeppelin/zeppelin-0.7.2/zeppelin-0.7.2.tgz.asc),
    [md5](https://archive.apache.org/dist/zeppelin/zeppelin-0.7.2/zeppelin-0.7.2.tgz.md5),
    [sha](https://archive.apache.org/dist/zeppelin/zeppelin-0.7.2/zeppelin-0.7.2.tgz.sha512))
  <p />

  - 0.7.1 released on Mar 31, 2017 ([release notes](./releases/zeppelin-release-0.7.1.html)) ([git tag](https://git-wip-us.apache.org/repos/asf?p=zeppelin.git;h=refs/tags/v0.7.1))

    * Binary package with all interpreters:
    <a style="cursor:pointer" onclick="ga('send', 'event', 'download', 'zeppelin-bin-all', '0.7.1'); window.location.href='http://archive.apache.org/dist/zeppelin/zeppelin-0.7.1/zeppelin-0.7.1-bin-all.tgz'">zeppelin-0.7.1-bin-all.tgz</a> (712 MB,
    [pgp](https://archive.apache.org/dist/zeppelin/zeppelin-0.7.1/zeppelin-0.7.1-bin-all.tgz.asc),
    [md5](https://archive.apache.org/dist/zeppelin/zeppelin-0.7.1/zeppelin-0.7.1-bin-all.tgz.md5),
    [sha](https://archive.apache.org/dist/zeppelin/zeppelin-0.7.1/zeppelin-0.7.1-bin-all.tgz.sha512))

    * Binary package with Spark interpreter and interpreter net-install script ([interpreter installation guide](../../docs/0.7.1/manual/interpreterinstallation.html)):
    <a style="cursor:pointer" onclick="ga('send', 'event', 'download', 'zeppelin-bin-netinst', '0.7.1'); window.location.href='http://archive.apache.org/dist/zeppelin/zeppelin-0.7.1/zeppelin-0.7.1-bin-netinst.tgz'">zeppelin-0.7.1-bin-netinst.tgz</a> (273 MB,
    [pgp](https://archive.apache.org/dist/zeppelin/zeppelin-0.7.1/zeppelin-0.7.1-bin-netinst.tgz.asc),
    [md5](https://archive.apache.org/dist/zeppelin/zeppelin-0.7.1/zeppelin-0.7.1-bin-netinst.tgz.md5),
    [sha](https://archive.apache.org/dist/zeppelin/zeppelin-0.7.1/zeppelin-0.7.1-bin-netinst.tgz.sha512))

    * Source:
    <a style="cursor:pointer" onclick="ga('send', 'event', 'download', 'zeppelin-src', '0.7.1'); window.location.href='http://archive.apache.org/dist/zeppelin/zeppelin-0.7.1/zeppelin-0.7.1.tgz'">zeppelin-0.7.1.tgz</a> (1.9 MB,
    [pgp](https://archive.apache.org/dist/zeppelin/zeppelin-0.7.1/zeppelin-0.7.1.tgz.asc),
    [md5](https://archive.apache.org/dist/zeppelin/zeppelin-0.7.1/zeppelin-0.7.1.tgz.md5),
    [sha](https://archive.apache.org/dist/zeppelin/zeppelin-0.7.1/zeppelin-0.7.1.tgz.sha512))
  <p />

  - 0.7.0 released on Feb 5, 2017 ([release notes](./releases/zeppelin-release-0.7.0.html)) ([git tag](https://git-wip-us.apache.org/repos/asf?p=zeppelin.git;h=refs/tags/v0.7.0))

    * Binary package with all interpreters:
    <a style="cursor:pointer" onclick="ga('send', 'event', 'download', 'zeppelin-bin-all', '0.7.0'); window.location.href='http://archive.apache.org/dist/zeppelin/zeppelin-0.7.0/zeppelin-0.7.0-bin-all.tgz'">zeppelin-0.7.0-bin-all.tgz</a> (710 MB,
    [pgp](https://archive.apache.org/dist/zeppelin/zeppelin-0.7.0/zeppelin-0.7.0-bin-all.tgz.asc),
    [md5](https://archive.apache.org/dist/zeppelin/zeppelin-0.7.0/zeppelin-0.7.0-bin-all.tgz.md5),
    [sha](https://archive.apache.org/dist/zeppelin/zeppelin-0.7.0/zeppelin-0.7.0-bin-all.tgz.sha512))

    * Binary package with Spark interpreter and interpreter net-install script ([interpreter installation guide](../../docs/0.7.0/manual/interpreterinstallation.html)):
    <a style="cursor:pointer" onclick="ga('send', 'event', 'download', 'zeppelin-bin-netinst', '0.7.0'); window.location.href='http://archive.apache.org/dist/zeppelin/zeppelin-0.7.0/zeppelin-0.7.0-bin-netinst.tgz'">zeppelin-0.7.0-bin-netinst.tgz</a> (272 MB,
    [pgp](https://archive.apache.org/dist/zeppelin/zeppelin-0.7.0/zeppelin-0.7.0-bin-netinst.tgz.asc),
    [md5](https://archive.apache.org/dist/zeppelin/zeppelin-0.7.0/zeppelin-0.7.0-bin-netinst.tgz.md5),
    [sha](https://archive.apache.org/dist/zeppelin/zeppelin-0.7.0/zeppelin-0.7.0-bin-netinst.tgz.sha512))

    * Source:
    <a style="cursor:pointer" onclick="ga('send', 'event', 'download', 'zeppelin-src', '0.7.0'); window.location.href='http://archive.apache.org/dist/zeppelin/zeppelin-0.7.0/zeppelin-0.7.0.tgz'">zeppelin-0.7.0.tgz</a> (1.9 MB,
    [pgp](https://archive.apache.org/dist/zeppelin/zeppelin-0.7.0/zeppelin-0.7.0.tgz.asc),
    [md5](https://archive.apache.org/dist/zeppelin/zeppelin-0.7.0/zeppelin-0.7.0.tgz.md5),
    [sha](https://archive.apache.org/dist/zeppelin/zeppelin-0.7.0/zeppelin-0.7.0.tgz.sha512))
  <p />

  - 0.6.2 released on Oct 15, 2016 ([release notes](./releases/zeppelin-release-0.6.2.html)) ([git tag](https://git-wip-us.apache.org/repos/asf?p=zeppelin.git;h=refs/tags/v0.6.2))

    * Binary package with all interpreters:
    <a style="cursor:pointer" onclick="ga('send', 'event', 'download', 'zeppelin-bin-all', '0.6.2'); window.location.href='http://archive.apache.org/dist/zeppelin/zeppelin-0.6.2/zeppelin-0.6.2-bin-all.tgz'">zeppelin-0.6.2-bin-all.tgz</a> (547 MB,
    [pgp](https://archive.apache.org/dist/zeppelin/zeppelin-0.6.2/zeppelin-0.6.2-bin-all.tgz.asc),
    [md5](https://archive.apache.org/dist/zeppelin/zeppelin-0.6.2/zeppelin-0.6.2-bin-all.tgz.md5),
    [sha](https://archive.apache.org/dist/zeppelin/zeppelin-0.6.2/zeppelin-0.6.2-bin-all.tgz.sha512))

    * Binary package with Spark interpreter and interpreter net-install script ([interpreter installation guide](../../docs/0.6.2/manual/interpreterinstallation.html)):
    <a style="cursor:pointer" onclick="ga('send', 'event', 'download', 'zeppelin-bin-netinst', '0.6.2'); window.location.href='http://archive.apache.org/dist/zeppelin/zeppelin-0.6.2/zeppelin-0.6.2-bin-netinst.tgz'">zeppelin-0.6.2-bin-netinst.tgz</a> (245 MB,
    [pgp](https://archive.apache.org/dist/zeppelin/zeppelin-0.6.2/zeppelin-0.6.2-bin-netinst.tgz.asc),
    [md5](https://archive.apache.org/dist/zeppelin/zeppelin-0.6.2/zeppelin-0.6.2-bin-netinst.tgz.md5),
    [sha](https://archive.apache.org/dist/zeppelin/zeppelin-0.6.2/zeppelin-0.6.2-bin-netinst.tgz.sha512))

    * Source:
    <a style="cursor:pointer" onclick="ga('send', 'event', 'download', 'zeppelin-src', '0.6.2'); window.location.href='http://archive.apache.org/dist/zeppelin/zeppelin-0.6.2/zeppelin-0.6.2.tgz'">zeppelin-0.6.2.tgz</a> (1.4 MB,
    [pgp](https://archive.apache.org/dist/zeppelin/zeppelin-0.6.2/zeppelin-0.6.2.tgz.asc),
    [md5](https://archive.apache.org/dist/zeppelin/zeppelin-0.6.2/zeppelin-0.6.2.tgz.md5),
    [sha](https://archive.apache.org/dist/zeppelin/zeppelin-0.6.2/zeppelin-0.6.2.tgz.sha512))

    <blockquote style="margin-top: 10px;">
      <p><strong>Note</strong>: From Zeppelin version 0.6.2, Spark interpreter in binary package is compatible with Spark 2.0 & Scala 2.11 and Spark 1.6(or previous) & Scala 2.10. You can use even different version of Spark at the same time if you set different SPARK_HOME in interpreter setting page.</p>
    </blockquote>
  <p />

  - 0.6.1 released on Aug 15, 2016 ([release notes](./releases/zeppelin-release-0.6.1.html)) ([git tag](https://git-wip-us.apache.org/repos/asf?p=zeppelin.git;h=refs/tags/v0.6.1))

    * Binary package with all interpreters:
    <a style="cursor:pointer" onclick="ga('send', 'event', 'download', 'zeppelin-bin-all', '0.6.1'); window.location.href='http://archive.apache.org/dist/zeppelin/zeppelin-0.6.1/zeppelin-0.6.1-bin-all.tgz'">zeppelin-0.6.1-bin-all.tgz</a>
    ([pgp](https://archive.apache.org/dist/zeppelin/zeppelin-0.6.1/zeppelin-0.6.1-bin-all.tgz.asc),
     [md5](https://archive.apache.org/dist/zeppelin/zeppelin-0.6.1/zeppelin-0.6.1-bin-all.tgz.md5),
     [sha](https://archive.apache.org/dist/zeppelin/zeppelin-0.6.1/zeppelin-0.6.1-bin-all.tgz.sha512))

    * Binary package with Spark interpreter and interpreter net-install script ([interpreter installation guide](../../docs/0.6.1/manual/interpreterinstallation.html)):
    <a style="cursor:pointer" onclick="ga('send', 'event', 'download', 'zeppelin-bin-netinst', '0.6.1'); window.location.href='http://archive.apache.org/dist/zeppelin/zeppelin-0.6.1/zeppelin-0.6.1-bin-netinst.tgz'">zeppelin-0.6.1-bin-netinst.tgz</a>
    ([pgp](https://archive.apache.org/dist/zeppelin/zeppelin-0.6.1/zeppelin-0.6.1-bin-netinst.tgz.asc),
     [md5](https://archive.apache.org/dist/zeppelin/zeppelin-0.6.1/zeppelin-0.6.1-bin-netinst.tgz.md5),
     [sha](https://archive.apache.org/dist/zeppelin/zeppelin-0.6.1/zeppelin-0.6.1-bin-netinst.tgz.sha512))

    * Source:
    <a style="cursor:pointer" onclick="ga('send', 'event', 'download', 'zeppelin-src', '0.6.1'); window.location.href='http://archive.apache.org/dist/zeppelin/zeppelin-0.6.1/zeppelin-0.6.1.tgz'">zeppelin-0.6.1.tgz</a>
    ([pgp](https://archive.apache.org/dist/zeppelin/zeppelin-0.6.1/zeppelin-0.6.1.tgz.asc),
     [md5](https://archive.apache.org/dist/zeppelin/zeppelin-0.6.1/zeppelin-0.6.1.tgz.md5),
     [sha](https://archive.apache.org/dist/zeppelin/zeppelin-0.6.1/zeppelin-0.6.1.tgz.sha512))

    <blockquote style="margin-top: 10px;">
      <p><strong>Note</strong>: Zeppelin-0.6.1 is built with Scala 2.11 by default. If you want to build Zeppelin with Scala 2.10 or install interpreter built with Scala 2.10(other than Spark interpreter), please see <a href='../../docs/0.6.1/install/install.html#2-build-source-with-options' target='_blank'>install</a> or <a href='../../docs/0.6.1/manual/interpreterinstallation.html#install-interpreter-built-with-scala-210' target='_blank'>interpreter installation</a>.</p>
    </blockquote>
<p />

  - 0.6.0 released on Jul 2, 2016 ([release notes](./releases/zeppelin-release-0.6.0.html)) ([git tag](https://git-wip-us.apache.org/repos/asf?p=zeppelin.git;h=refs/tags/v0.6.0))

    * Binary package with all interpreters:
    <a style="cursor:pointer" onclick="ga('send', 'event', 'download', 'zeppelin-bin-all', '0.6.0'); window.location.href='http://archive.apache.org/dist/zeppelin/zeppelin-0.6.0/zeppelin-0.6.0-bin-all.tgz'">zeppelin-0.6.0-bin-all.tgz</a>
    ([pgp](https://archive.apache.org/dist/zeppelin/zeppelin-0.6.0/zeppelin-0.6.0-bin-all.tgz.asc),
     [md5](https://archive.apache.org/dist/zeppelin/zeppelin-0.6.0/zeppelin-0.6.0-bin-all.tgz.md5),
     [sha](https://archive.apache.org/dist/zeppelin/zeppelin-0.6.0/zeppelin-0.6.0-bin-all.tgz.sha512))

    * Binary package with Spark interpreter and interpreter net-install script ([interpreter installation guide](../../docs/0.6.0/manual/interpreterinstallation.html)):
    <a style="cursor:pointer" onclick="ga('send', 'event', 'download', 'zeppelin-bin-netinst', '0.6.0'); window.location.href='http://archive.apache.org/dist/zeppelin/zeppelin-0.6.0/zeppelin-0.6.0-bin-netinst.tgz'">zeppelin-0.6.0-bin-netinst.tgz</a>
    ([pgp](https://archive.apache.org/dist/zeppelin/zeppelin-0.6.0/zeppelin-0.6.0-bin-netinst.tgz.asc),
     [md5](https://archive.apache.org/dist/zeppelin/zeppelin-0.6.0/zeppelin-0.6.0-bin-netinst.tgz.md5),
     [sha](https://archive.apache.org/dist/zeppelin/zeppelin-0.6.0/zeppelin-0.6.0-bin-netinst.tgz.sha512))

    * Source:
    <a style="cursor:pointer" onclick="ga('send', 'event', 'download', 'zeppelin-src', '0.6.0'); window.location.href='http://archive.apache.org/dist/zeppelin/zeppelin-0.6.0/zeppelin-0.6.0.tgz'">zeppelin-0.6.0.tgz</a>
    ([pgp](https://archive.apache.org/dist/zeppelin/zeppelin-0.6.0/zeppelin-0.6.0.tgz.asc),
     [md5](https://archive.apache.org/dist/zeppelin/zeppelin-0.6.0/zeppelin-0.6.0.tgz.md5),
     [sha](https://archive.apache.org/dist/zeppelin/zeppelin-0.6.0/zeppelin-0.6.0.tgz.sha512))

<p />

  - 0.5.6-incubating released on Jan 22, 2016 ([release notes](./releases/zeppelin-release-0.5.6-incubating.html)) ([git tag](https://git-wip-us.apache.org/repos/asf?p=zeppelin.git;a=tag;h=refs/tags/v0.5.6))

    * Binary package:
    <a style="cursor:pointer" onclick="ga('send', 'event', 'download', 'zeppelin-bin', '0.5.6-incubating'); window.location.href='http://archive.apache.org/dist/incubator/zeppelin/0.5.6-incubating/zeppelin-0.5.6-incubating-bin-all.tgz'">zeppelin-0.5.6-incubating-bin-all.tgz</a>
    ([pgp](http://archive.apache.org/dist/incubator/zeppelin/0.5.6-incubating/zeppelin-0.5.6-incubating-bin-all.tgz.asc),
     [md5](http://archive.apache.org/dist/incubator/zeppelin/0.5.6-incubating/zeppelin-0.5.6-incubating-bin-all.tgz.md5),
     [sha](http://archive.apache.org/dist/incubator/zeppelin/0.5.6-incubating/zeppelin-0.5.6-incubating-bin-all.tgz.sha512))

    * Source:
    <a style="cursor:pointer" onclick="ga('send', 'event', 'download', 'zeppelin-src', '0.5.6-incubating'); window.location.href='http://archive.apache.org/dist/incubator/zeppelin/0.5.6-incubating/zeppelin-0.5.6-incubating.tgz'">zeppelin-0.5.6-incubating.tgz</a>
    ([pgp](http://archive.apache.org/dist/incubator/zeppelin/0.5.6-incubating/zeppelin-0.5.6-incubating.tgz.asc),
     [md5](http://archive.apache.org/dist/incubator/zeppelin/0.5.6-incubating/zeppelin-0.5.6-incubating.tgz.md5),
     [sha](http://archive.apache.org/dist/incubator/zeppelin/0.5.6-incubating/zeppelin-0.5.6-incubating.tgz.sha512))

<p />

  - 0.5.5-incubating released on Nov 18, 2015 ([release notes](./releases/zeppelin-release-0.5.5-incubating.html)) ([git tag](https://git-wip-us.apache.org/repos/asf?p=zeppelin.git;a=tag;h=refs/tags/v0.5.5))

    * Binary package:
    <a style="cursor:pointer" onclick="ga('send', 'event', 'download', 'zeppelin-bin', '0.5.5-incubating'); window.location.href='http://archive.apache.org/dist/incubator/zeppelin/0.5.5-incubating/zeppelin-0.5.5-incubating-bin-all.tgz'">zeppelin-0.5.5-incubating-bin-all.tgz</a>
    ([pgp](http://archive.apache.org/dist/incubator/zeppelin/0.5.5-incubating/zeppelin-0.5.5-incubating-bin-all.tgz.asc),
     [md5](http://archive.apache.org/dist/incubator/zeppelin/0.5.5-incubating/zeppelin-0.5.5-incubating-bin-all.tgz.md5),
     [sha](http://archive.apache.org/dist/incubator/zeppelin/0.5.5-incubating/zeppelin-0.5.5-incubating-bin-all.tgz.sha512))

    * Source:
    <a style="cursor:pointer" onclick="ga('send', 'event', 'download', 'zeppelin-src', '0.5.5-incubating'); window.location.href='http://archive.apache.org/dist/incubator/zeppelin/0.5.5-incubating/zeppelin-0.5.5-incubating.tgz'">zeppelin-0.5.5-incubating.tgz</a>
    ([pgp](http://archive.apache.org/dist/incubator/zeppelin/0.5.5-incubating/zeppelin-0.5.5-incubating.tgz.asc),
     [md5](http://archive.apache.org/dist/incubator/zeppelin/0.5.5-incubating/zeppelin-0.5.5-incubating.tgz.md5),
     [sha](http://archive.apache.org/dist/incubator/zeppelin/0.5.5-incubating/zeppelin-0.5.5-incubating.tgz.sha512))

<p />

  - 0.5.0-incubating released on July 23, 2015 ([release notes](./releases/zeppelin-release-0.5.0-incubating.html)) ([git tag](https://git-wip-us.apache.org/repos/asf?p=zeppelin.git;a=tag;h=refs/tags/v0.5.0))

    * Binary built with spark-1.4.0 and hadoop-2.3:
    <a style="cursor:pointer" onclick="ga('send', 'event', 'download', 'zeppelin-bin', '0.5.0-incubating'); window.location.href='http://archive.apache.org/dist/incubator/zeppelin/0.5.0-incubating/zeppelin-0.5.0-incubating-bin-spark-1.4.0_hadoop-2.3.tgz'">zeppelin-0.5.0-incubating-bin-spark-1.4.0_hadoop-2.3.tgz</a>
    ([pgp](http://archive.apache.org/dist/incubator/zeppelin/0.5.0-incubating/zeppelin-0.5.0-incubating-bin-spark-1.4.0_hadoop-2.3.tgz.asc),
     [md5](http://archive.apache.org/dist/incubator/zeppelin/0.5.0-incubating/zeppelin-0.5.0-incubating-bin-spark-1.4.0_hadoop-2.3.tgz.md5),
     [sha](http://archive.apache.org/dist/incubator/zeppelin/0.5.0-incubating/zeppelin-0.5.0-incubating-bin-spark-1.4.0_hadoop-2.3.tgz.sha))

    * Binary built with spark-1.3.1 and hadoop-2.3:
    <a style="cursor:pointer" onclick="ga('send', 'event', 'download', 'zeppelin-bin', '0.5.0-incubating'); window.location.href='http://archive.apache.org/dist/incubator/zeppelin/0.5.0-incubating/zeppelin-0.5.0-incubating-bin-spark-1.3.1_hadoop-2.3.tgz'">zeppelin-0.5.0-incubating-bin-spark-1.3.1_hadoop-2.3.tgz</a>
    ([pgp](http://archive.apache.org/dist/incubator/zeppelin/0.5.0-incubating/zeppelin-0.5.0-incubating-bin-spark-1.3.1_hadoop-2.3.tgz.asc),
     [md5](http://archive.apache.org/dist/incubator/zeppelin/0.5.0-incubating/zeppelin-0.5.0-incubating-bin-spark-1.3.1_hadoop-2.3.tgz.md5),
     [sha](http://archive.apache.org/dist/incubator/zeppelin/0.5.0-incubating/zeppelin-0.5.0-incubating-bin-spark-1.3.1_hadoop-2.3.tgz.sha))

    * Source:
    <a style="cursor:pointer" onclick="ga('send', 'event', 'download', 'zeppelin-src', '0.5.0-incubating'); window.location.href='http://archive.apache.org/dist/incubator/zeppelin/0.5.0-incubating/zeppelin-0.5.0-incubating.tgz'">zeppelin-0.5.0-incubating.tgz</a>
    ([pgp](http://archive.apache.org/dist/incubator/zeppelin/0.5.0-incubating/zeppelin-0.5.0-incubating.tgz.asc),
     [md5](http://archive.apache.org/dist/incubator/zeppelin/0.5.0-incubating/zeppelin-0.5.0-incubating.tgz.md5),
     [sha](http://archive.apache.org/dist/incubator/zeppelin/0.5.0-incubating/zeppelin-0.5.0-incubating.tgz.sha))

## Supported interpreters

Thanks to many Zeppelin contributors, we can provide much more interpreters in every release.

Please check the [Supported Interpreters](./supported_interpreters.html) before you download Zeppelin package.


<!--
-------------
### Old release

##### Zeppelin-0.3.3 (2014.03.29)

Download <a onclick="ga('send', 'event', 'download', 'zeppelin', '0.3.3');" href="https://s3-ap-northeast-1.amazonaws.com/zeppel.in/zeppelin-0.3.3.tar.gz">zeppelin-0.3.3.tar.gz</a> ([release note](https://zeppelin-project.atlassian.net/secure/ReleaseNote.jspa?projectId=10001&version=10301))


##### Zeppelin-0.3.2 (2014.03.14)

Download <a onclick="ga('send', 'event', 'download', 'zeppelin', '0.3.2');" href="https://s3-ap-northeast-1.amazonaws.com/zeppel.in/zeppelin-0.3.2.tar.gz">zeppelin-0.3.2.tar.gz</a> ([release note](https://zeppelin-project.atlassian.net/secure/ReleaseNote.jspa?projectId=10001&version=10300))

##### Zeppelin-0.3.1 (2014.03.06)

Download <a onclick="ga('send', 'event', 'download', 'zeppelin', '0.3.1');" href="https://s3-ap-northeast-1.amazonaws.com/zeppel.in/zeppelin-0.3.1.tar.gz">zeppelin-0.3.1.tar.gz</a> ([release note](https://zeppelin-project.atlassian.net/secure/ReleaseNote.jspa?projectId=10001&version=10201))

##### Zeppelin-0.3.0 (2014.02.07)

Download <a onclick="ga('send', 'event', 'download', 'zeppelin', '0.3.0');" href="https://s3-ap-northeast-1.amazonaws.com/zeppel.in/zeppelin-0.3.0.tar.gz">zeppelin-0.3.0.tar.gz</a>, ([release note](https://zeppelin-project.atlassian.net/secure/ReleaseNote.jspa?projectId=10001&version=10200))

##### Zeppelin-0.2.0 (2014.01.22)

Download Download <a onclick="ga('send', 'event', 'download', 'zeppelin', '0.2.0');" href="https://s3-ap-northeast-1.amazonaws.com/zeppel.in/zeppelin-0.2.0.tar.gz">zeppelin-0.2.0.tar.gz</a>, ([release note](https://zeppelin-project.atlassian.net/secure/ReleaseNote.jspa?projectId=10001&version=10001))

-->
