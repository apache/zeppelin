<!---
 Licensed to the Apache Software Foundation (ASF) under one or more
 contributor license agreements.  See the NOTICE file distributed with
 this work for additional information regarding copyright ownership.
 The ASF licenses this file to You under the Apache License, Version 2.0
 (the "License"); you may not use this file except in compliance with
 the License.  You may obtain a copy of the License at

      http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
-->

# Apache Zeppelin Build Infrastructure

## Dependency graph

```
                     e.g. hive, hadoop, ...
                       | | |
                       v v v
  Zeppelin Server  <- Zengine
         +               |
    zeppeli web          v
                        ZAN
```


## Artifacts

 - Zeppelin Server  : Web UI, server to host it  / executable
 - Zeppelin Web     : Web UI, clint-side JS app  / HTML+JavaScript; war
 - Zeppelin Zengine : Main library               / java library
 - ZAN              



## Build process

 - compile                => *.class, minify *.js
 - build modules          => *.jar, war
 - test                   => UnitTest reports
 - package -P build-distr => final .zip
 - integration-test       => selenium over running zeppelin-server (from package)


## Verify

 - pre-inegration-test   => start Zeppelin
 - integration-test
 - post-inegration-test  => stop Zeppelin
