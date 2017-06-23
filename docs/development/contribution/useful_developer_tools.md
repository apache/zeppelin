---
layout: page
title: "Useful Developer Tools"
description: ""
group: development/contribution
---
<!--
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
-->
{% include JB/setup %}

# Useful Developer Tools 

<div id="toc"></div>

### Developing `zeppelin-web`

Check [zeppelin-web: Local Development](https://github.com/apache/zeppelin/tree/master/zeppelin-web#local-development).

### Tools

#### SVM: Scala Version Manager

[svm](https://github.com/yuroyoro/svm) would be useful when changing scala version frequently.

#### JDK change script: OSX
 
this script would be helpful when changing JDK version frequently.

```
function setjdk() {
  if [ $# -ne 0 ]; then
  # written based on OSX. 
  # use diffrent base path for other OS
  removeFromPath '/System/Library/Frameworks/JavaVM.framework/Home/bin'
  if [ -n "${JAVA_HOME+x}" ]; then
    removeFromPath $JAVA_HOME
  fi
  export JAVA_HOME=`/usr/libexec/java_home -v $@`
  export PATH=$JAVA_HOME/bin:$PATH
  fi
}
function removeFromPath() {
  export PATH=$(echo $PATH | sed -E -e "s;:$1;;" -e "s;$1:?;;")
}
```
    
you can use this function like `setjdk 1.8` / `setjdk 1.7`

### Building Submodules Selectively 

```
# build `zeppelin-web` only
mvn clean -pl 'zeppelin-web' package -DskipTests;

# build `zeppelin-server` and its dependencies only
mvn clean package -pl 'spark,spark-dependencies,python,markdown,zeppelin-server' --am -DskipTests

# build spark related modules with default profiles: scala 2.10 
mvn clean package -pl 'spark,spark-dependencies,zeppelin-server' --am -DskipTests

# build spark related modules with profiles: scala 2.11, spark 2.1 hadoop 2.7 
./dev/change_scala_version.sh 2.11
mvn clean package -Pspark-2.1 -Phadoop-2.7 -Pscala-2.11 -pl 'spark,spark-dependencies,zeppelin-server' --am -DskipTests

# build `zeppelin-server` and `markdown` with dependencies
mvn clean package -pl 'markdown,zeppelin-server' --am -DskipTests
```

### Running Individual Tests

```
# run the `HeliumBundleFactoryTest` test class
mvn test -pl 'zeppelin-server' --am -DfailIfNoTests=false -Dtest=HeliumBundleFactoryTest
```

### Running Selenium Tests

Make sure that Zeppelin instance is started to execute integration tests (= selenium tests).

```
# run the `SparkParagraphIT` test class
TEST_SELENIUM="true" mvn test -pl 'zeppelin-server' --am -DfailIfNoTests=false -Dtest=SparkParagraphIT

# run the `testSqlSpark` test function only in the `SparkParagraphIT` class
# but note that, some test might be dependent on the previous tests
TEST_SELENIUM="true" mvn test -pl 'zeppelin-server' --am -DfailIfNoTests=false -Dtest=SparkParagraphIT#testSqlSpark
```



