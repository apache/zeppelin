#Zeppelin 

**Documentation:** [User Guide](http://zeppelin.incubator.apache.org/docs/index.html)<br/>
**Mailing List:** [User and Dev mailing list](http://zeppelin.incubator.apache.org/community.html)<br/>
**Continuous Integration:** [![Build Status](https://secure.travis-ci.org/apache/incubator-zeppelin.png?branch=master)](https://travis-ci.org/apache/incubator-zeppelin) <br/>
**Contributing:** [Contribution Guide](https://github.com/apache/incubator-zeppelin/blob/master/CONTRIBUTING.md)<br/>
**License:** [Apache 2.0](https://github.com/apache/incubator-zeppelin/blob/master/LICENSE)


**Zeppelin**, a web-based notebook that enables interactive data analytics. You can make beautiful data-driven, interactive and collaborative documents with SQL, Scala and more.

Core feature:
   * Web based notebook style editor.
   * Built-in Apache Spark support


To know more about Zeppelin, visit our web site [http://zeppelin.incubator.apache.org](http://zeppelin.incubator.apache.org)

## Requirements
 * Java 1.7
 * Tested on Mac OSX, Ubuntu 14.X, CentOS 6.X
 * Maven (if you want to build from the source code)
 * Node.js Package Manager

## Getting Started

### Before Build
If you don't have requirements prepared, install it. 
(The installation method may vary according to your environment, example is for Ubuntu.)

```
sudo apt-get update
sudo apt-get install git
sudo apt-get install openjdk-7-jdk
sudo apt-get install npm
sudo apt-get install libfontconfig

# install maven
wget http://www.eu.apache.org/dist/maven/maven-3/3.3.3/binaries/apache-maven-3.3.3-bin.tar.gz
sudo tar -zxf apache-maven-3.3.3-bin.tar.gz -C /usr/local/
sudo ln -s /usr/local/apache-maven-3.3.3/bin/mvn /usr/local/bin/mvn
```

_Notes:_ 
 - Ensure node is installed by running `node --version`  
 - Ensure maven is running version 3.1.x or higher with `mvn -version`

### Build
If you want to build Zeppelin from the source, please first clone this repository, then:

```
mvn clean package -DskipTests
```

To build with a specific Spark version, Hadoop version or specific features, define one or more of the `spark`, `pyspark`, `hadoop` and `yarn` profiles, such as:

```
-Pspark-1.5   [Version to run in local spark mode]
-Ppyspark     [optional: enable PYTHON support in spark via the %pyspark interpreter]
-Pyarn        [optional: enable YARN support]
-Dhadoop.version=2.2.0  [hadoop distribution]
-Phadoop-2.2            [hadoop version]
```

Currently, final/full distributions run with:

```
mvn clean package -Pspark-1.5 -Phadoop-2.4 -Pyarn -Ppyspark
```

Spark 1.5.x

```
mvn clean package -Pspark-1.5 -Dhadoop.version=2.2.0 -Phadoop-2.2 -DskipTests
```
Spark 1.4.x

```
mvn clean package -Pspark-1.4 -Dhadoop.version=2.2.0 -Phadoop-2.2 -DskipTests
```
Spark 1.3.x

```
mvn clean package -Pspark-1.3 -Dhadoop.version=2.2.0 -Phadoop-2.2 -DskipTests
```
Spark 1.2.x

```
mvn clean package -Pspark-1.2 -Dhadoop.version=2.2.0 -Phadoop-2.2 -DskipTests 
```
Spark 1.1.x

```
mvn clean package -Pspark-1.1 -Dhadoop.version=2.2.0 -Phadoop-2.2 -DskipTests 
```
CDH 5.X

```
mvn clean package -Pspark-1.2 -Dhadoop.version=2.5.0-cdh5.3.0 -Phadoop-2.4 -DskipTests
```
Yarn (Hadoop 2.7.x)

```
mvn clean package -Pspark-1.4 -Dspark.version=1.4.1 -Dhadoop.version=2.7.0 -Phadoop-2.6 -Pyarn -DskipTests
```
Yarn (Hadoop 2.6.x)

```
mvn clean package -Pspark-1.1 -Dhadoop.version=2.6.0 -Phadoop-2.6 -Pyarn -DskipTests
```
Yarn (Hadoop 2.4.x)

```
mvn clean package -Pspark-1.1 -Dhadoop.version=2.4.0 -Phadoop-2.4 -Pyarn -DskipTests
```
Yarn (Hadoop 2.3.x)

```
mvn clean package -Pspark-1.1 -Dhadoop.version=2.3.0 -Phadoop-2.3 -Pyarn -DskipTests
```
Yarn (Hadoop 2.2.x)

```
mvn clean package -Pspark-1.1 -Dhadoop.version=2.2.0 -Phadoop-2.2 -Pyarn -DskipTests
```

Ignite (1.1.0-incubating and later)

```
mvn clean package -Dignite.version=1.1.0-incubating -DskipTests
```

### Configure
If you wish to configure Zeppelin option (like port number), configure the following files:

```
./conf/zeppelin-env.sh
./conf/zeppelin-site.xml
```
(You can copy ```./conf/zeppelin-env.sh.template``` into ```./conf/zeppelin-env.sh```. 
Same for ```zeppelin-site.xml```.)


#### Setting SPARK_HOME and HADOOP_HOME

Without SPARK_HOME and HADOOP_HOME, Zeppelin uses embedded Spark and Hadoop binaries that you have specified with mvn build option.
If you want to use system provided Spark and Hadoop, export SPARK_HOME and HADOOP_HOME in zeppelin-env.sh
You can use any supported version of spark without rebuilding Zeppelin.

```
# ./conf/zeppelin-env.sh
export SPARK_HOME=...
export HADOOP_HOME=...
```

#### External cluster configuration
Mesos

    # ./conf/zeppelin-env.sh
    export MASTER=mesos://...
    export ZEPPELIN_JAVA_OPTS="-Dspark.executor.uri=/path/to/spark-*.tgz" or SPARK_HOME="/path/to/spark_home"
    export MESOS_NATIVE_LIBRARY=/path/to/libmesos.so
    
If you set `SPARK_HOME`, you should deploy spark binary on the same location to all worker nodes. And if you set `spark.executor.uri`, every worker can read that file on its node.

Yarn

    # ./conf/zeppelin-env.sh
    export SPARK_HOME=/path/to/spark_dir

### Run
    ./bin/zeppelin-daemon.sh start

    browse localhost:8080 in your browser.


For configuration details check __./conf__ subdirectory.

### Package
To package the final distribution including the compressed archive, run:

      mvn clean package -Pbuild-distr

To build a distribution with specific profiles, run:

      mvn clean package -Pbuild-distr -Pspark-1.5 -Phadoop-2.4 -Pyarn -Ppyspark

The profiles `-Pspark-1.5 -Phadoop-2.4 -Pyarn -Ppyspark` can be adjusted if you wish to build to a specific spark versions, or omit support such as `yarn`.  

The archive is generated under _zeppelin-distribution/target_ directory

###Run end-to-end tests
Zeppelin comes with a set of end-to-end acceptance tests driving headless selenium browser

      #assumes zeppelin-server running on localhost:8080 (use -Durl=.. to override)
      mvn verify

      #or take care of starting\stoping zeppelin-server from packaged _zeppelin-distribuion/target_
      mvn verify -P using-packaged-distr



[![Analytics](https://ga-beacon.appspot.com/UA-45176241-4/apache/incubator-zeppelin/README.md?pixel)](https://github.com/igrigorik/ga-beacon)
