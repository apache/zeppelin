---
layout: page
title: "How to Build Zeppelin from source"
description: "How to build Zeppelin from source"
group: setup/basics
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

## How to Build Zeppelin from Source

<div id="toc"></div>

#### 0. Requirements

If you want to build from source, you must first install the following dependencies:

<table class="table-configuration">
  <tr>
    <th>Name</th>
    <th>Value</th>
  </tr>
  <tr>
    <td>Git</td>
    <td>(Any Version)</td>
  </tr>
  <tr>
    <td>Maven</td>
    <td>3.6.3 or higher</td>
  </tr>
  <tr>
    <td>OpenJDK or Oracle JDK</td>
    <td>1.8 (151+)<br>(set JAVA_HOME)</td>
  </tr>
</table>


If you haven't installed Git and Maven yet, check the [Build requirements](#build-requirements) section and follow the step by step instructions from there.

#### 1. Clone the Apache Zeppelin repository

```bash
git clone https://github.com/apache/zeppelin.git
```

#### 2. Build source


You can build Zeppelin with following maven command:

```bash
./mvnw clean package -DskipTests [Options]
```

Check [build-profiles](#build-profiles) section for further build options.
If you are behind proxy, follow instructions in [Proxy setting](#proxy-setting-optional) section.

If you're interested in contribution, please check [Contributing to Apache Zeppelin (Code)](../../development/contribution/how_to_contribute_code.html) and [Contributing to Apache Zeppelin (Website)](../../development/contribution/how_to_contribute_website.html).


#### 3. Done
You can directly start Zeppelin by running the following command after successful build:

```bash
./bin/zeppelin-daemon.sh start
```

### Build profiles


#### Scala profile

To be noticed, this scala profile affect the modules (e.g. cassandra) that use scala except Spark interpreter (Spark interpreter use other profiles to control its scala version, see the doc below).

Set scala version (default 2.10). Available profiles are

```
-Pscala-2.10
-Pscala-2.11
```

#### Spark Interpreter

To be noticed, the spark profiles here only affect the uni test (no need to specify `SPARK_HOME`) of spark interpreter. 
Zeppelin doesn't require you to build with different spark to make different versions of spark work in Zeppelin.
You can run different versions of Spark in Zeppelin as long as you specify `SPARK_HOME`. Actually Zeppelin supports all the versions of Spark from 1.6 to 3.0.

To build with a specific Spark version or scala versions, define one or more of the following profiles and options:

##### `-Pspark-[version]`

Set spark major version

Available profiles are

```
-Pspark-3.2
-Pspark-3.1
-Pspark-3.0
-Pspark-2.4
```

minor version can be adjusted by `-Dspark.version=x.x.x`

##### `-Pspark-scala-[version] (optional)`

To be noticed, these profiles also only affect the unit test (no need to specify `SPARK_HOME`) of Spark interpreter. 
Actually Zeppelin supports all the versions of scala (2.11, 2.12) in Spark interpreter as long as you specify `SPARK_HOME`.

Available profiles are

```
-Pspark-scala-2.11
-Pspark-scala-2.12
```
 
#### Build hadoop with Zeppelin (`-Phadoop[version]`)
 
To be noticed, hadoop profiles only affect Zeppelin server, it doesn't affect any interpreter. 
Zeppelin server use hadoop in some cases, such as using hdfs as notebook storage. You can check this [page](./hadoop_integration.html) for more details about how to configure hadoop in Zeppelin.

Set hadoop major version (default hadoop2).
Available profiles are

```
-Phadoop2
-Phadoop3
```

minor version can be adjusted by `-Dhadoop.version=x.x.x`


##### `-Pvendor-repo` (optional)

enable 3rd party vendor repository (Cloudera, Hortonworks)


#### -Pexamples (optional)

Build examples under zeppelin-examples directory


### Build command examples
Here are some examples with several options:

```bash
# build with spark-3.0, spark-scala-2.12
./mvnw clean package -Pspark-3.0 -Pspark-scala-2.12 -DskipTests

# build with spark-2.4, spark-scala-2.11
./mvnw clean package -Pspark-2.4 -Pspark-scala-2.11 -DskipTests

```

Ignite Interpreter

```bash
./mvnw clean package -Dignite.version=1.9.0 -DskipTests
```

### Optional configurations

Here are additional configurations that could be optionally tuned using the trailing `-D` option for maven commands


Spark package

```bash
spark.archive # default spark-${spark.version}
spark.src.download.url # default http://d3kbcqa49mib13.cloudfront.net/${spark.archive}.tgz
spark.bin.download.url # default http://d3kbcqa49mib13.cloudfront.net/${spark.archive}-bin-without-hadoop.tgz
```

Py4J package

```bash
python.py4j.version # default 0.9.2
pypi.repo.url # default https://pypi.python.org/packages
python.py4j.repo.folder # default /64/5c/01e13b68e8caafece40d549f232c9b5677ad1016071a48d04cc3895acaa3
```

final URL location for Py4J package will be produced as following:

`${pypi.repo.url}${python.py4j.repo.folder}py4j-${python.py4j.version}.zip`


Frontend Maven Plugin configurations

```
plugin.frontend.nodeDownloadRoot # default https://nodejs.org/dist/
plugin.frontend.npmDownloadRoot # default http://registry.npmjs.org/npm/-/
plugin.frontend.yarnDownloadRoot # default https://github.com/yarnpkg/yarn/releases/download/
```

## Build requirements

### Install requirements

If you don't have requirements prepared, install it.
(The installation method may vary according to your environment, example is for Ubuntu.)

```bash
sudo apt-get update
sudo apt-get install git
sudo apt-get install openjdk-8-jdk
sudo apt-get install npm
sudo apt-get install libfontconfig
sudo apt-get install r-base-dev
sudo apt-get install r-cran-evaluate
```

_Notes:_
 - Ensure node is installed by running `node --version`  
 - Ensure maven is running version 3.6.3 or higher with `./mvnw -version`
 - Configure maven to use more memory than usual by `export MAVEN_OPTS="-Xmx2g -XX:MaxMetaspaceSize=512m"`

## Proxy setting (optional)

If you're behind the proxy, you'll need to configure maven and npm to pass through it.

First of all, configure maven in your `~/.m2/settings.xml`.

```xml
<settings>
  <proxies>
    <proxy>
      <id>proxy-http</id>
      <active>true</active>
      <protocol>http</protocol>
      <host>localhost</host>
      <port>3128</port>
      <!-- <username>usr</username>
      <password>pwd</password> -->
      <nonProxyHosts>localhost|127.0.0.1</nonProxyHosts>
    </proxy>
    <proxy>
      <id>proxy-https</id>
      <active>true</active>
      <protocol>https</protocol>
      <host>localhost</host>
      <port>3128</port>
      <!-- <username>usr</username>
      <password>pwd</password> -->
      <nonProxyHosts>localhost|127.0.0.1</nonProxyHosts>
    </proxy>
  </proxies>
</settings>
```

Then, next commands will configure npm.

```bash
npm config set proxy http://localhost:3128
npm config set https-proxy http://localhost:3128
npm config set registry "http://registry.npmjs.org/"
npm config set strict-ssl false
```

Configure git as well

```bash
git config --global http.proxy http://localhost:3128
git config --global https.proxy http://localhost:3128
git config --global url."http://".insteadOf git://
```

To clean up, set `active false` in Maven `settings.xml` and run these commands.

```bash
npm config rm proxy
npm config rm https-proxy
git config --global --unset http.proxy
git config --global --unset https.proxy
git config --global --unset url."http://".insteadOf
```

_Notes:_
 - If you are behind NTLM proxy you can use [Cntlm Authentication Proxy](http://cntlm.sourceforge.net/).
 - Replace `localhost:3128` with the standard pattern `http://user:pwd@host:port`.


## Package
To package the final distribution including the compressed archive, run:

```sh
./mvnw clean package -Pbuild-distr
```

To build a distribution with specific profiles, run:

```sh
./mvnw clean package -Pbuild-distr -Pspark-2.4
```

The profiles `-Pspark-2.4` can be adjusted if you wish to build to a specific spark versions.  

The archive is generated under _`zeppelin-distribution/target`_ directory

## Run end-to-end tests
Zeppelin comes with a set of end-to-end acceptance tests driving headless selenium browser

```sh
# assumes zeppelin-server running on localhost:8080 (use -Durl=.. to override)
mvn verify

# or take care of starting/stoping zeppelin-server from packaged zeppelin-distribuion/target
mvn verify -P using-packaged-distr
```

[![Analytics](https://ga-beacon.appspot.com/UA-45176241-4/apache/zeppelin/README.md?pixel)](https://github.com/igrigorik/ga-beacon)
