# Overview
Livy interpreter for Apache Zeppelin

# Prerequisities
You can follow the instructions at [Livy Quick Start](http://livy.io/quickstart.html) to set up livy.

# Run Integration Tests
You can add integration test to [LivyInterpreter.java](https://github.com/apache/zeppelin/blob/master/livy/src/test/java/org/apache/zeppelin/livy/LivyInterpreterIT.java)
Either you can run the integration test on travis where enviroment will be setup or you can run it in local. You need to download livy-0.2 and spark-1.5.2 to local, then use the following
script to run the integration test.

```bash
#!/usr/bin/env bash
export LIVY_HOME=<path_of_livy_0.2.0>
export SPARK_HOME=<path_of_spark-1.5.2>
mvn clean verify -pl livy -DfailIfNoTests=false -DskipRat
```