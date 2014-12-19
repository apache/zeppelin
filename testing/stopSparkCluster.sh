#!/bin/sh
cd spark-1.1.1-bin-hadoop2.3
./sbin/stop-master.sh
kill $(ps -ef | grep 'org.apache.spark.deploy.worker.Worker' | awk '{print $2}')
cd ..
rm -rf spark-1.1.1-bin-hadoop2.3*
