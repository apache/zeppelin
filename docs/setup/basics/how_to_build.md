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
    <td>3.1.x or higher</td>
  </tr>
  <tr>
    <td>JDK</td>
    <td>1.7</td>
  </tr>
</table>


If you haven't installed Git and Maven yet, check the [Build requirements](#build-requirements) section and follow the step by step instructions from there.

#### 1. Clone the Apache Zeppelin repository

```
git clone https://github.com/apache/zeppelin.git
```

#### 2. Build source


You can build Zeppelin with following maven command:

```
mvn clean package -DskipTests [Options]
```

If you're unsure about the options, use the same commands that creates official binary package.

```bash
# update all pom.xml to use scala 2.11
./dev/change_scala_version.sh 2.11
# build zeppelin with all interpreters and include latest version of Apache spark support for local mode.
mvn clean package -DskipTests -Pspark-2.0 -Phadoop-2.4 -Pr -Pscala-2.11
```

#### 3. Done
You can directly start Zeppelin by running after successful build:

```bash
./bin/zeppelin-daemon.sh start
```

Check [build-profiles](#build-profiles) section for further build options.
If you are behind proxy, follow instructions in [Proxy setting](#proxy-setting-optional) section.

If you're interested in contribution, please check [Contributing to Apache Zeppelin (Code)](../../development/contribution/how_to_contribute_code.html) and [Contributing to Apache Zeppelin (Website)](../../development/contribution/how_to_contribute_website.html).

### Build profiles

#### Spark Interpreter

To build with a specific Spark version, Hadoop version or specific features, define one or more of the following profiles and options:

##### `-Pspark-[version]`

Set spark major version

Available profiles are

```
-Pspark-2.1
-Pspark-2.0
-Pspark-1.6
-Pspark-1.5
-Pspark-1.4
-Pcassandra-spark-1.5
-Pcassandra-spark-1.4
-Pcassandra-spark-1.3
-Pcassandra-spark-1.2
-Pcassandra-spark-1.1
```

minor version can be adjusted by `-Dspark.version=x.x.x`


##### `-Phadoop-[version]`

set hadoop major version

Available profiles are

```
-Phadoop-0.23
-Phadoop-1
-Phadoop-2.2
-Phadoop-2.3
-Phadoop-2.4
-Phadoop-2.6
-Phadoop-2.7
```

minor version can be adjusted by `-Dhadoop.version=x.x.x`

##### `-Pscala-[version] (optional)`

set scala version (default 2.10)
Available profiles are

```
-Pscala-2.10
-Pscala-2.11
```

##### `-Pr` (optional)

enable [R](https://www.r-project.org/) support with [SparkR](https://spark.apache.org/docs/latest/sparkr.html) integration.

##### `-Pvendor-repo` (optional)

enable 3rd party vendor repository (cloudera)


##### `-Pmapr[version]` (optional)

For the MapR Hadoop Distribution, these profiles will handle the Hadoop version. As MapR allows different versions of Spark to be installed, you should specify which version of Spark is installed on the cluster by adding a Spark profile (`-Pspark-1.6`, `-Pspark-2.0`, etc.) as needed.
The correct Maven artifacts can be found for every version of MapR at http://doc.mapr.com

Available profiles are

```
-Pmapr3
-Pmapr40
-Pmapr41
-Pmapr50
-Pmapr51
```

#### -Pexamples (optional)

Bulid examples under zeppelin-examples directory


### Build command examples
Here are some examples with several options:

```bash
# build with spark-2.1, scala-2.11
./dev/change_scala_version.sh 2.11
mvn clean package -Pspark-2.1 -Phadoop-2.4 -Pscala-2.11 -DskipTests

# build with spark-2.0, scala-2.11
./dev/change_scala_version.sh 2.11
mvn clean package -Pspark-2.0 -Phadoop-2.4 -Pscala-2.11 -DskipTests

# build with spark-1.6, scala-2.10
mvn clean package -Pspark-1.6 -Phadoop-2.4 -DskipTests

# spark-cassandra integration
mvn clean package -Pcassandra-spark-1.5 -Dhadoop.version=2.6.0 -Phadoop-2.6 -DskipTests -DskipTests

# with CDH
mvn clean package -Pspark-1.5 -Dhadoop.version=2.6.0-cdh5.5.0 -Phadoop-2.6 -Pvendor-repo -DskipTests

# with MapR
mvn clean package -Pspark-1.5 -Pmapr50 -DskipTests
```

Ignite Interpreter

```bash
mvn clean package -Dignite.version=1.9.0 -DskipTests
```

Scalding Interpreter

```bash
mvn clean package -Pscalding -DskipTests
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

```
sudo apt-get update
sudo apt-get install git
sudo apt-get install openjdk-7-jdk
sudo apt-get install npm
sudo apt-get install libfontconfig
sudo apt-get install r-base-dev
sudo apt-get install r-cran-evaluate
```



### Install maven
```
wget http://www.eu.apache.org/dist/maven/maven-3/3.3.9/binaries/apache-maven-3.3.9-bin.tar.gz
sudo tar -zxf apache-maven-3.3.9-bin.tar.gz -C /usr/local/
sudo ln -s /usr/local/apache-maven-3.3.9/bin/mvn /usr/local/bin/mvn
```

_Notes:_
 - Ensure node is installed by running `node --version`  
 - Ensure maven is running version 3.1.x or higher with `mvn -version`
 - Configure maven to use more memory than usual by `export MAVEN_OPTS="-Xmx2g -XX:MaxPermSize=1024m"`



## Proxy setting (optional)

If you're behind the proxy, you'll need to configure maven and npm to pass through it.

First of all, configure maven in your `~/.m2/settings.xml`.

```
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

```
npm config set proxy http://localhost:3128
npm config set https-proxy http://localhost:3128
npm config set registry "http://registry.npmjs.org/"
npm config set strict-ssl false
```

Configure git as well

```
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
mvn clean package -Pbuild-distr
```

To build a distribution with specific profiles, run:

```sh
mvn clean package -Pbuild-distr -Pspark-1.5 -Phadoop-2.4
```

The profiles `-Pspark-1.5 -Phadoop-2.4` can be adjusted if you wish to build to a specific spark versions.  

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
