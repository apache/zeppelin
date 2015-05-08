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

# Distribution archive of Zeppelin project #

Zeppelin is distributed as a single gzip archive with the following structure:

```
zeppelin
 ├── bin
 │   ├── zeppelin.sh
 │   └── seppelin-deamon.sh
 ├── lib
 ├── conf
 ├── zan-repo
 │    ├── txt.wordcount
 │    ├── vis.bubble
 │    ├── vis.gchart
 │    ├── ml.something
 │    └── ...
 ├── zeppelin-server-<verion>.jar
 ├── zeppelin-web-<verion>.war
 └── zeppelin-cli-<verion>.jar
 
```

We use maven-assembly-pugin to build it, see distribution.xml for details

**IMPORTANT:** _/lib_ subdirectory contains all transitive dependencies of the zeppelin-distribution module,
automatically resolved by maven, except for explicitly excluded _server_, _web_ and _cli_ zeppelin sub-modules.
