#!/bin/sh
wget http://apache.mesi.com.ar/spark/spark-1.1.1/spark-1.1.1-bin-hadoop2.3.tgz
tar zxvf spark-1.1.1-bin-hadoop2.3.tgz
cd spark-1.1.1-bin-hadoop2.3
export SPARK_MASTER_PORT=7071
export SPARK_MASTER_WEBUI_PORT=7072
./sbin/start-master.sh
./bin/spark-class org.apache.spark.deploy.worker.Worker spark://localhost:7071 &> worker.log &
./bin/spark-class org.apache.spark.deploy.worker.Worker spark://localhost:7071 &> worker2.log &
