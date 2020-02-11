InfluxDB 2.0 interpreter for Apache Zeppelin
============================================

## Description:

Provide InfluxDB Interpreter for Zeppelin.

## Build

```
mvn -pl zeppelin-interpreter,zeppelin-display,influxdb -DskipTests package
```

## Test

```
mvn -pl influxdb,zeppelin-display,zeppelin-interpreter -Dtest='org.apache.zeppelin.influxdb.*' -DfailIfNoTests=false test
```
