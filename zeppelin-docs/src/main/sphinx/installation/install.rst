=================
Install Zeppelin
=================

Instructions for the Impatient
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

Install Zeppelin in local mode

.. code-block:: bash

  # this scripts install hadoop and zeppelin in current directory and start zeppelin in local mode
  # download and unarchive hadoop distribution package
  curl -O http://apache.mirror.cdnetworks.com/hadoop/common/hadoop-1.2.1/hadoop-1.2.1-bin.tar.gz
  tar -xzf hadoop-1.2.1-bin.tar.gz

  # download zeppelin and unarchive
  curl -O https://s3-ap-northeast-1.amazonaws.com/zeppel.in/zeppelin-0.3.0.tar.gz
  tar -xzf zeppelin-0.3.0.tar.gz

  # set HADOOP_HOME
  echo "export HADOOP_HOME=`pwd`/hadoop-1.2.1" >> zeppelin-0.3.0/conf/zeppelin-env.sh

  # start zeppelin
  ./zeppelin-0.3.0/bin/zeppelin-daemon.sh start

You can access Zeppelin with browser http://localhost:8080

Install
^^^^^^^
Configuring Zeppelin with existing hadoop cluster, refer this section.

Prerequisites
-------------
Java 1.6 or Later
Apache Hadoop (Standalone mode)
Download
To get Zeppelin distribution, download a recent release.

Install
-------
Unpack the downloaded Zeppelin distribution.

Configure
---------
Configuration can be done by both environment variable and java properties. If both defined, environment vaiable is used.

=========================    =======================  ============================== ===========
zepplin-env.sh	             zepplin-site.xml         Default value  		     Description
=========================    =======================  ============================== ===========
ZEPPELIN_HOME	  		    		   	   		   	     Zeppelin Home directory
ZEPPELIN_PORT         	     zeppelin.server.port     8080	   		     Zeppelin server port
ZEPPELIN_JOB_DIR             zeppelin.job.dir         jobs	   		     Zeppelin persist/load session in this directory. Can be a path or a URI. location on HDFS supported
ZEPPELIN_ZAN_REPO            zeppelin.zan.repo        https://github.com/NFLabs/zan  Remote ZAN repository URL
ZEPPELIN_ZAN_LOCAL_REPO      zeppelin.zan.localrepo   zan-repo	 		     Zeppelin library local repository. Local filesystem path
ZEPPELIN_ZAN_SHARED_REPO     zeppelin.zan.sharedrepo				     Zeppelin library shared repository. Location on HDFS. Usufull when your backend (eg. hiveserver) is not running on the sam machine and want to use zeppelin library with resource file(eg. in hive 'ADD FILE 'path'). So your backend can get resource file from shared repository.
ZEPPELIN_DRIVERS             zeppelin.drivers         hive:hive2://,exec:exec://     Comma separated list of [Name]:[Connection URI]
ZEPPELIN_DRIVER_DIR          zeppelin.driver.dir      drivers			     Zeppelin driver directory.
=========================    =======================  ============================== ===========

Configuring with existing Hive
-------------------------------
If you have hive already installed in your hadoop cluster, just run hive server and make Zeppelin to connect it. There're two different version of hive servers, Hive Server1, Hive Server2. Make sure you have Hive server running.

And then, add connection uri in zeppelin.drivers at zeppelin-site.xml If you have Hive Server 1 installed and running on host hiveserver1Address on port 10000, configuration property can be

.. code-block:: bash
 
 <property>
   <name>zeppelin.drivers</name>
   <value>hive:hive://hiveserver1Address:10000/default,exec:exec://</value>
   <description>Comma separated driver configurations uri. </description>
 </property>

If Hive Server 2 installed and running on host hiveserver2Address on port 10000, configuration will be

.. code-block:: bash

  <property>
   <name>zeppelin.drivers</name>
   <value>hive:hive2://hiveserver2Address:10000/default,exec:exec://</value>
   <description>Comma separated driver configurations uri. </description>
  </property>

Start/Stop
^^^^^^^^^^

**Start Zeppelin**

.. code-block:: bash

  bin/zeppelin-daemon.sh start

After successful start, visit http://localhost:8080 with your web browser

**Stop Zeppelin**

.. code-block:: bash

  bin/zeppelin-daemon.sh stop

