Scio interpreter for Apache Zeppelin
====================================

## Raison d'être:

Provide Scio Interpreter for Zeppelin.

## Build

```
mvn -pl zeppelin-interpreter,zeppelin-display,scio -DskipTests package
```

## Test

```
mvn -pl scio,zeppelin-display,zeppelin-interpreter -Dtest='org.apache.zeppelin.scio.*' -DfailIfNoTests=false test
```
