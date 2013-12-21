# Distribution archive of Zeppelin project #

Zeppelin is distibuted as a single gzip archinve with the folowing structure:

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

**IMPORTANT:** _/lib_ subdirectory contains all transitive dependencyes of the zeppelin-distribution module,
automatialy resoved by maven, except for explicitly excludede _server_, _web_ and _cli_ zeppelin sub-modules.
