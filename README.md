#Zeppelin
[![Build Status](https://secure.travis-ci.org/NFLabs/zeppelin.png?branch=master)](https://travis-ci.org/NFLabs/zeppelin)


**Zeppelin** is data analytics environment

   * Web based notebook style editor.
   * Built-in Apache Spark support


To know more about Zeppelin, visit our web site http://zeppelin-project.org

###Build

To build Zeppelin, Java 1.7 and none-root account is required. (Tested on OSX, CentOS 6)

      mvn clean package

with specific version

      mvn clean package -Dspark.version=1.1.0 -Dhadoop.version=2.0.0-mr1-cdh4.6.0

###Configure

Configure following configuration files

      ./conf/zeppelin-env.sh
      ./conf/zeppelin-site.xml

###Run
    ./bin/zeppelin-daemon.sh start

    browse localhost:8080 in your browser. 8081 port should be accessible for websocket connection.


For configuration details check __./conf__ subdirectory.

###Package
To package final distribution do:

      mvn clean package -P build-distr

The archive is generated under _zeppelin-distribution/target_ directory

###Run end-to-end tests
Zeppelin comes with a set of end-to-end acceptnce tests driving headless selenium browser

      #assumes zeppelin-server runing on localhost:8080 (use -Durl=.. to overide)
      mvn verify

      #or take care of starting\stoping zeppelin-server from packaged _zeppelin-distribuion/target_
      mvn verify -P using-packaged-distr


###Mailing list

[Developers](https://groups.google.com/forum/#!forum/zeppelin-developers) : https://groups.google.com/forum/#!forum/zeppelin-developers

[Users](https://groups.google.com/forum/#!forum/zeppelin-users) : https://groups.google.com/forum/#!forum/zeppelin-users


###License
[Apache2](http://www.apache.org/licenses/LICENSE-2.0.html) : http://www.apache.org/licenses/LICENSE-2.0.html

To contribute, please read [How to contribute](http://zeppelin-project.org/docs/development/howtocontribute.html) first.


[![githalytics.com alpha](https://cruel-carlota.pagodabox.com/10ba60fb64e53bb1ccd0bab47abbcc4a "githalytics.com")](http://githalytics.com/NFLabs/zeppelin)



