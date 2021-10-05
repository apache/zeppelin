InfluxDB 2.0 interpreter for Apache Zeppelin
============================================

## Description:

Provide InfluxDB Interpreter for Zeppelin.

## Build

```
./mvnw -pl influxdb -am -DskipTests package
```

## Test

```
./mvnw -pl influxdb -am -Dtest='org.apache.zeppelin.influxdb.*' -DfailIfNoTests=false test
```
