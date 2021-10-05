Scio interpreter for Apache Zeppelin
====================================

## Raison d'être:

Provide Scio Interpreter for Zeppelin.

## Build

```
./mvnw -pl zeppelin-interpreter,zeppelin-display,scio -DskipTests package
```

## Test

```
./mvnw -pl scio,zeppelin-display,zeppelin-interpreter -Dtest='org.apache.zeppelin.scio.*' -DfailIfNoTests=false test
```
