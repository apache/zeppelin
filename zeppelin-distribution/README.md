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
