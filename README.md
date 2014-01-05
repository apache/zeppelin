#Zeppelin
[![Build Status](https://secure.travis-ci.org/NFLabs/zeppelin.png?branch=master)](https://travis-ci.org/NFLabs/zeppelin)


**Zeppelin** is complete large scale data analysis environment, including

   * Web based GUI
   * With interactive visualization
   * Super easy SQL like analysis language called **ZQL**
   * Custom user routine support 
   * Central archive of library called **ZAN** (Zeppelin Archivce Network)
   * On top of Hive (or any Hive compatible system like Shark)


To know more about Zeppelin, visit our web site http://zeppelin-project.org

###Build

      mvn clean package


###Packaging

      mvn clean package -P build-distr

The package is generated under __zeppelin-distribution/target__ directory

###Run
To run Zeppelin in _local-mode_ using hive 0.9 + embedded derby metastore:

    #make sure hadoop is availavle thorugh PATH or HADOOP_HOME
    ./bin/zeppelin.sh

For configuration details check __./conf__ subdirectory.

###Mailing list

[Developers](https://groups.google.com/forum/#!forum/zeppelin-developers) : https://groups.google.com/forum/#!forum/zeppelin-developers

[Users](https://groups.google.com/forum/#!forum/zeppelin-users) : https://groups.google.com/forum/#!forum/zeppelin-users


###License
[Apache2](http://www.apache.org/licenses/LICENSE-2.0.html) : http://www.apache.org/licenses/LICENSE-2.0.html



[![githalytics.com alpha](https://cruel-carlota.pagodabox.com/10ba60fb64e53bb1ccd0bab47abbcc4a "githalytics.com")](http://githalytics.com/NFLabs/zeppelin)



