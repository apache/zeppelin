InfluxDB 2.0 interpreter for Apache Zeppelin
============================================

## Description:

Provide InfluxDB Interpreter for Zeppelin.

## Build

```
mvn -pl influxdb -am -DskipTests package
```

## Test

```
mvn -pl influxdb -am -Dtest='org.apache.zeppelin.influxdb.*' -DfailIfNoTests=false test
```
