# Overview
Livy interpreter for Apache Zeppelin

# Prerequisities
You can follow the instructions at [Livy Get Started](https://livy.apache.org/get-started/) to set up livy.

# Run Integration Tests
You can add integration test to [LivyInterpreter.java](https://github.com/apache/zeppelin/blob/master/livy/src/test/java/org/apache/zeppelin/livy/LivyInterpreterIT.java) and run the integration test either via the CI environment or locally. You need to download livy-0.8 and spark-2.4.8 to local, then use the following script to run the integration test.

```bash
export LIVY_HOME=<path_of_livy_0.8.0>
export SPARK_HOME=<path_of_spark-2.4.8>
./mvnw clean verify -pl livy -DfailIfNoTests=false
```
