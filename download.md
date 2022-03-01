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

The latest release of Apache Zeppelin is **0.10.1**.

  - 0.10.1 released on Feb 29, 2022 ([release notes](./releases/zeppelin-release-0.10.1.html)) ([git tag](https://gitbox.apache.org/repos/asf?p=zeppelin.git;h=refs/tags/v0.11.0))

    * Binary package with all interpreters ([Install guide](../../docs/0.10.1/quickstart/install.html)):
    <p><div class="btn btn-md btn-primary" onclick="ga('send', 'event', 'download', 'zeppelin-bin-all', '0.10.1'); window.location.href='http://www.apache.org/dyn/closer.cgi/zeppelin/zeppelin-0.10.1/zeppelin-0.10.1-bin-all.tgz'">zeppelin-0.10.1-bin-all.tgz</div> (1.5g,
    [pgp](https://www.apache.org/dist/zeppelin/zeppelin-0.10.1/zeppelin-0.10.1-bin-all.tgz.asc),
    [md5](https://www.apache.org/dist/zeppelin/zeppelin-0.10.1/zeppelin-0.10.1-bin-all.tgz.md5),
    [sha](https://www.apache.org/dist/zeppelin/zeppelin-0.10.1/zeppelin-0.10.1-bin-all.tgz.sha512))</p>

    * Binary package with Spark interpreter and interpreter net-install script ([interpreter installation guide](../../docs/0.10.1/usage/interpreter/installation.html)):
    <p><div class="btn btn-md btn-primary" onclick="ga('send', 'event', 'download', 'zeppelin-bin-netinst', '0.10.1'); window.location.href='http://www.apache.org/dyn/closer.cgi/zeppelin/zeppelin-0.10.1/zeppelin-0.10.1-bin-netinst.tgz'">zeppelin-0.10.1-bin-netinst.tgz</div> (568 MB,
    [pgp](https://www.apache.org/dist/zeppelin/zeppelin-0.10.1/zeppelin-0.10.1-bin-netinst.tgz.asc),
    [md5](https://www.apache.org/dist/zeppelin/zeppelin-0.10.1/zeppelin-0.10.1-bin-netinst.tgz.md5),
    [sha](https://www.apache.org/dist/zeppelin/zeppelin-0.10.1/zeppelin-0.10.1-bin-netinst.tgz.sha512))</p>

    * Source:
    <a style="cursor:pointer" onclick="ga('send', 'event', 'download', 'zeppelin-src', '0.10.1'); window.location.href='http://www.apache.org/dyn/closer.cgi/zeppelin/zeppelin-0.10.1/zeppelin-0.10.1.tgz'">zeppelin-0.10.1.tgz</a> (9.1 MB,
    [pgp](https://www.apache.org/dist/zeppelin/zeppelin-0.10.1/zeppelin-0.10.1.tgz.asc),
    [md5](https://www.apache.org/dist/zeppelin/zeppelin-0.10.1/zeppelin-0.10.1.tgz.md5),
    [sha](https://www.apache.org/dist/zeppelin/zeppelin-0.10.1/zeppelin-0.10.1.tgz.sha512))

# Using the official docker image

Make sure that [docker](https://www.docker.com/community-edition) is installed in your local machine.  

Use this command to launch Apache Zeppelin in a container.

```bash
docker run -p 8080:8080 --rm --name zeppelin apache/zeppelin:0.10.1

```
To persist `logs` and `notebook` directories, use the [volume](https://docs.docker.com/engine/reference/commandline/run/#mount-volume--v-read-only) option for docker container.
You can also use [volume](https://docs.docker.com/engine/reference/commandline/run/#mount-volume--v-read-only) for Spark and Flink binary distribution.

```bash
docker run -u $(id -u) -p 8080:8080 --rm -v $PWD/logs:/logs -v $PWD/notebook:/notebook \
  -v /usr/lib/spark-2.4.7:/opt/spark -v /usr/lib/flink-1.12.2:/opt/flink \
  -e FLINK_HOME=/opt/flink -e SPARK_HOME=/opt/spark \
  -e ZEPPELIN_LOG_DIR='/logs' -e ZEPPELIN_NOTEBOOK_DIR='/notebook' --name zeppelin apache/zeppelin:0.10.1
```

If you have trouble accessing `localhost:8080` in the browser, Please clear browser cache.

## Verify the integrity of the files

It is essential that you [verify](https://www.apache.org/info/verification.html) the integrity of the downloaded files using the PGP or MD5 signatures. This signature should be matched against the [KEYS](https://www.apache.org/dist/zeppelin/KEYS) file.



## Build from source

For developers, to get latest *0.10.0-SNAPSHOT* check [README](https://github.com/apache/zeppelin/blob/master/README.md).



## Old releases

  - 0.10.0 released on Aug 24, 2021 ([release notes](./releases/zeppelin-release-0.10.0.html)) ([git tag](https://gitbox.apache.org/repos/asf?p=zeppelin.git;h=refs/tags/v0.10.0))

    * Binary package with all interpreters ([Install guide](../../docs/0.10.0/quickstart/install.html)):
  <p><div class="btn btn-md btn-primary" onclick="ga('send', 'event', 'download', 'zeppelin-bin-all', '0.10.0'); window.location.href='http://www.apache.org/dyn/closer.cgi/zeppelin/zeppelin-0.10.0/zeppelin-0.10.0-bin-all.tgz'">zeppelin-0.10.0-bin-all.tgz</div> (1.5g,
  [pgp](https://www.apache.org/dist/zeppelin/zeppelin-0.10.0/zeppelin-0.10.0-bin-all.tgz.asc),
  [md5](https://www.apache.org/dist/zeppelin/zeppelin-0.10.0/zeppelin-0.10.0-bin-all.tgz.md5),
  [sha](https://www.apache.org/dist/zeppelin/zeppelin-0.10.0/zeppelin-0.10.0-bin-all.tgz.sha512))</p>

    * Binary package with Spark interpreter and interpreter net-install script ([interpreter installation guide](../../docs/0.10.0/usage/interpreter/installation.html)):
  <p><div class="btn btn-md btn-primary" onclick="ga('send', 'event', 'download', 'zeppelin-bin-netinst', '0.10.0'); window.location.href='http://www.apache.org/dyn/closer.cgi/zeppelin/zeppelin-0.10.0/zeppelin-0.10.0-bin-netinst.tgz'">zeppelin-0.10.0-bin-netinst.tgz</div> (568 MB,
  [pgp](https://www.apache.org/dist/zeppelin/zeppelin-0.10.0/zeppelin-0.10.0-bin-netinst.tgz.asc),
  [md5](https://www.apache.org/dist/zeppelin/zeppelin-0.10.0/zeppelin-0.10.0-bin-netinst.tgz.md5),
  [sha](https://www.apache.org/dist/zeppelin/zeppelin-0.10.0/zeppelin-0.10.0-bin-netinst.tgz.sha512))</p>

    * Source:
      <a style="cursor:pointer" onclick="ga('send', 'event', 'download', 'zeppelin-src', '0.10.0'); window.location.href='http://www.apache.org/dyn/closer.cgi/zeppelin/zeppelin-0.10.0/zeppelin-0.10.0.tgz'">zeppelin-0.10.0.tgz</a> (9.1 MB,
      [pgp](https://www.apache.org/dist/zeppelin/zeppelin-0.10.0/zeppelin-0.10.0.tgz.asc),
      [md5](https://www.apache.org/dist/zeppelin/zeppelin-0.10.0/zeppelin-0.10.0.tgz.md5),
      [sha](https://www.apache.org/dist/zeppelin/zeppelin-0.10.0/zeppelin-0.10.0.tgz.sha512))
    
  - 0.9.0 released on Dec 26, 2020 ([release notes](./releases/zeppelin-release-0.9.0.html)) ([git tag](https://gitbox.apache.org/repos/asf?p=zeppelin.git;h=refs/tags/v0.9.0))

    * Binary package with all interpreters ([Install guide](../../docs/0.9.0/quickstart/install.html)):
  <p><div class="btn btn-md btn-primary" onclick="ga('send', 'event', 'download', 'zeppelin-bin-all', '0.9.0'); window.location.href='http://www.apache.org/dyn/closer.cgi/zeppelin/zeppelin-0.9.0/zeppelin-0.9.0-bin-all.tgz'">zeppelin-0.9.0-bin-all.tgz</div> (1.5g,
  [pgp](https://www.apache.org/dist/zeppelin/zeppelin-0.9.0/zeppelin-0.9.0-bin-all.tgz.asc),
  [md5](https://www.apache.org/dist/zeppelin/zeppelin-0.9.0/zeppelin-0.9.0-bin-all.tgz.md5),
  [sha](https://www.apache.org/dist/zeppelin/zeppelin-0.9.0/zeppelin-0.9.0-bin-all.tgz.sha512))</p>

    * Binary package with Spark interpreter and interpreter net-install script ([interpreter installation guide](../../docs/0.9.0/usage/interpreter/installation.html)):
  <p><div class="btn btn-md btn-primary" onclick="ga('send', 'event', 'download', 'zeppelin-bin-netinst', '0.9.0'); window.location.href='http://www.apache.org/dyn/closer.cgi/zeppelin/zeppelin-0.9.0/zeppelin-0.9.0-bin-netinst.tgz'">zeppelin-0.9.0-bin-netinst.tgz</div> (568 MB,
  [pgp](https://www.apache.org/dist/zeppelin/zeppelin-0.9.0/zeppelin-0.9.0-bin-netinst.tgz.asc),
  [md5](https://www.apache.org/dist/zeppelin/zeppelin-0.9.0/zeppelin-0.9.0-bin-netinst.tgz.md5),
  [sha](https://www.apache.org/dist/zeppelin/zeppelin-0.9.0/zeppelin-0.9.0-bin-netinst.tgz.sha512))</p>

    * Source:
      <a style="cursor:pointer" onclick="ga('send', 'event', 'download', 'zeppelin-src', '0.9.0'); window.location.href='http://www.apache.org/dyn/closer.cgi/zeppelin/zeppelin-0.9.0/zeppelin-0.9.0.tgz'">zeppelin-0.9.0.tgz</a> (9.1 MB,
      [pgp](https://www.apache.org/dist/zeppelin/zeppelin-0.9.0/zeppelin-0.9.0.tgz.asc),
      [md5](https://www.apache.org/dist/zeppelin/zeppelin-0.9.0/zeppelin-0.9.0.tgz.md5),
      [sha](https://www.apache.org/dist/zeppelin/zeppelin-0.9.0/zeppelin-0.9.0.tgz.sha512))
  
  - 0.8.2 released on Sep 29, 2018 ([release notes](./releases/zeppelin-release-0.8.2.html)) ([git tag](https://git-wip-us.apache.org/repos/asf?p=zeppelin.git;h=refs/tags/v0.8.2))

    * Binary package with all interpreters ([Install guide](../../docs/0.8.2/quickstart/install.html)):
    <p><div class="btn btn-md btn-primary" onclick="ga('send', 'event', 'download', 'zeppelin-bin-all', '0.8.2'); window.location.href='http://www.apache.org/dyn/closer.cgi/zeppelin/zeppelin-0.8.2/zeppelin-0.8.2-bin-all.tgz'">zeppelin-0.8.2-bin-all.tgz</div> (952 MB,
    [pgp](https://www.apache.org/dist/zeppelin/zeppelin-0.8.2/zeppelin-0.8.2-bin-all.tgz.asc),
    [md5](https://www.apache.org/dist/zeppelin/zeppelin-0.8.2/zeppelin-0.8.2-bin-all.tgz.md5),
    [sha](https://www.apache.org/dist/zeppelin/zeppelin-0.8.2/zeppelin-0.8.2-bin-all.tgz.sha512))</p>

    * Binary package with Spark interpreter and interpreter net-install script ([interpreter installation guide](../../docs/0.8.2/usage/interpreter/installation.html)):
    <p><div class="btn btn-md btn-primary" onclick="ga('send', 'event', 'download', 'zeppelin-bin-netinst', '0.8.2'); window.location.href='http://www.apache.org/dyn/closer.cgi/zeppelin/zeppelin-0.8.2/zeppelin-0.8.2-bin-netinst.tgz'">zeppelin-0.8.2-bin-netinst.tgz</div> (318 MB,
    [pgp](https://www.apache.org/dist/zeppelin/zeppelin-0.8.2/zeppelin-0.8.2-bin-netinst.tgz.asc),
    [md5](https://www.apache.org/dist/zeppelin/zeppelin-0.8.2/zeppelin-0.8.2-bin-netinst.tgz.md5),
    [sha](https://www.apache.org/dist/zeppelin/zeppelin-0.8.2/zeppelin-0.8.2-bin-netinst.tgz.sha512))</p>

    * Source:
    <a style="cursor:pointer" onclick="ga('send', 'event', 'download', 'zeppelin-src', '0.8.2'); window.location.href='http://www.apache.org/dyn/closer.cgi/zeppelin/zeppelin-0.8.2/zeppelin-0.8.2.tgz'">zeppelin-0.8.2.tgz</a> (62 MB,
    [pgp](https://www.apache.org/dist/zeppelin/zeppelin-0.8.2/zeppelin-0.8.2.tgz.asc),
    [md5](https://www.apache.org/dist/zeppelin/zeppelin-0.8.2/zeppelin-0.8.2.tgz.md5),
    [sha](https://www.apache.org/dist/zeppelin/zeppelin-0.8.2/zeppelin-0.8.2.tgz.sha512))

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
