# Apache Zeppelin

**Documentation:** [User Guide](http://zeppelin.apache.org/docs/latest/index.html)<br/>
**Mailing Lists:** [User and Dev mailing list](http://zeppelin.apache.org/community.html)<br/>
**Continuous Integration:** [![Build Status](https://travis-ci.org/apache/zeppelin.svg?branch=master)](https://travis-ci.org/apache/zeppelin) <br/>
**Contributing:** [Contribution Guide](https://zeppelin.apache.org/contribution/contributions.html)<br/>
**Issue Tracker:** [Jira](https://issues.apache.org/jira/browse/ZEPPELIN)<br/>
**License:** [Apache 2.0](https://github.com/apache/zeppelin/blob/master/LICENSE)


**Zeppelin**, a web-based notebook that enables interactive data analytics. You can make beautiful data-driven, interactive and collaborative documents with SQL, Scala and more.

Core feature:
   * Web based notebook style editor.
   * Built-in Apache Spark support


To know more about Zeppelin, visit our web site [http://zeppelin.apache.org](http://zeppelin.apache.org)


## Getting Started

### Install binary package
Please refet to [Zeppelin installation guide](http://zeppelin.apache.org/docs/snapshot/install/install.html) to install Apache Zeppelin from binary package.

<<<<<<< 380fd31b57436f6526880a6bd74e29b8a1531adf
### Build from source
Please check [How to build Zeppelin from source](http://zeppelin.apache.org/docs/snapshot/install/build.html) to build Zeppelin.
=======
#### Install maven
```
wget http://www.eu.apache.org/dist/maven/maven-3/3.3.9/binaries/apache-maven-3.3.9-bin.tar.gz
sudo tar -zxf apache-maven-3.3.9-bin.tar.gz -C /usr/local/
sudo ln -s /usr/local/apache-maven-3.3.9/bin/mvn /usr/local/bin/mvn
```

_Notes:_
 - Ensure node is installed by running `node --version`  
 - Ensure maven is running version 3.1.x or higher with `mvn -version`
 - Configure maven to use more memory than usual by `export MAVEN_OPTS="-Xmx2g -XX:MaxPermSize=1024m"`

#### Proxy settings (optional)
First of all, set your proxy configuration on Maven `settings.xml`.
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

Then, run these commands from shell.
```
npm config set proxy http://localhost:3128
npm config set https-proxy http://localhost:3128
npm config set registry "http://registry.npmjs.org/"
npm config set strict-ssl false
git config --global http.proxy http://localhost:3128
git config --global https.proxy http://localhost:3128
git config --global url."http://".insteadOf git://
```

Cleanup: set `active false` in Maven `settings.xml` and run these commands.
```
npm config rm proxy
npm config rm https-proxy
git config --global --unset http.proxy
git config --global --unset https.proxy
git config --global --unset url."http://".insteadOf
```

_Notes:_
 - If you are behind NTLM proxy you can use [Cntlm Authentication Proxy](http://cntlm.sourceforge.net/).
 - Replace `localhost:3128` with the standard pattern `http://user:pwd@host:port`.

## Build
If you want to build Zeppelin from the source, please first clone this repository, then:

```
mvn clean package -DskipTests [Options]
```

Each Interpreter requires different Options.


### Spark Interpreter

To build with a specific Spark version, Hadoop version or specific features, define one or more of the following profiles and options:

##### `-Pspark-[version]`

Set spark major version

Available profiles are

```
-Pspark-2.0
-Pspark-1.6
-Pspark-1.5
-Pspark-1.4
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

##### `-Psparkr` (optional)

another [R](https://www.r-project.org/) support with [SparkR](https://spark.apache.org/docs/latest/sparkr.html) integration as well as local mode support.

##### `-Pvendor-repo` (optional)

enable 3rd party vendor repository (cloudera)


### Ignite Interpreter

```sh
mvn clean package -Dignite.version=1.7.0 -DskipTests
```

### Scalding Interpreter

```sh
mvn clean package -Pscalding -DskipTests
```
### -Pexamples (optional)

Bulid examples under zeppelin-examples directory


### Build command examples


Here're some examples:

```sh
# build with spark-2.0, scala-2.11
./dev/change_scala_version.sh 2.11
mvn clean package -Pspark-2.0 -Phadoop-2.4 -Psparkr -Pscala-2.11

# build with spark-1.6, scala-2.10
mvn clean package -Pspark-1.6 -Phadoop-2.4 -Psparkr

# with CDH
mvn clean package -Pspark-1.5 -Dhadoop.version=2.6.0-cdh5.5.0 -Phadoop-2.6 -Pvendor-repo -DskipTests

# with zeppelin-examples directory
mvn clean package -Pexamples -DskipTests

```

## Run

```sh
./bin/zeppelin-daemon.sh start
```
And browse localhost:8080 in your browser.

## Get the latest version of local Spark
You can download the latest version of [Apache Spark](http://spark.apache.org/) and use it without any configuration for Spark interpreter.

```sh
./bin/zeppelin-daemon.sh get-spark
```

or 

```
./bin/zeppelin.sh get-spark
```

Then the Spark will be saved under `ZEPPELIN_HOME/local-spark`.

## Configure
If you wish to configure Zeppelin option (like port number), configure the following files:

```
./conf/zeppelin-env.sh
./conf/zeppelin-site.xml
```

(You can copy `./conf/zeppelin-env.sh.template` into `./conf/zeppelin-env.sh`.
Same for `zeppelin-site.xml`.)

For configuration details check __`./conf`__ subdirectory.

### Setting SPARK_HOME and HADOOP_HOME

There are two Spark modes to use Spark interpreter in Zeppelin. 
One is using **[local-spark](#get-the-latest-version-of-local-spark)**. 
If you want to use this local Spark, then you don't need to set `SPARK_HOME` or `HADOOP_HOME` by yourself.

Or maybe you need to use other external Spark and Hadoop for your own usage. 
Then you should set `SPARK_HOME` and `HADOOP_HOME` in `zeppelin-env.sh`.
You can use any supported version of Spark without rebuilding Zeppelin.

```sh
# ./conf/zeppelin-env.sh
export SPARK_HOME=...
export HADOOP_HOME=...
```

### External cluster configuration

#### Mesos

```sh
# ./conf/zeppelin-env.sh
export MASTER=mesos://...
export ZEPPELIN_JAVA_OPTS="-Dspark.executor.uri=/path/to/spark-*.tgz" or SPARK_HOME="/path/to/spark_home"
export MESOS_NATIVE_LIBRARY=/path/to/libmesos.so
```

If you set `SPARK_HOME`, you should deploy Spark binary on the same location to all worker nodes. And if you set `spark.executor.uri`, every worker can read that file on its node.

#### Yarn

```sh
# ./conf/zeppelin-env.sh
export SPARK_HOME=/path/to/spark_dir
```

## Building for Scala 2.11
To produce a Zeppelin package compiled with Scala 2.11, use the `-Pscala-2.11` profile:

```sh
./dev/change_scala_version.sh 2.11
mvn clean package -Pspark-1.6 -Phadoop-2.4 -Pyarn -Ppyspark -Pscala-2.11 -DskipTests clean install
```

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
>>>>>>> Fix typos
