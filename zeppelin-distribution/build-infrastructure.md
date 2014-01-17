Zeppelin dependency graph:
--------------
                     hive, hadoop, ...
                       | | |
                       v v v
  Zeppelin Server  <- Zengine -> Zeppelin CLI
         +               |
    zeppeli web          v
                        ZAN



Zeppelin artifacts:
------------------
Zeppelin CLI    - Commandline UI             - executable
Zeppelin Server - Web UI, server to host it  - executable
Zwppwlin Web    - Web UI, clint-side JS app  - HTML+JavaScript; war
Zengine         - Main library               - java library
ZAN             - 



Build process:
-------------
compile                => *.class, minify *.js
build modules          => *.jar, war
test                   => UnitTest reports
package -P build-distr => final .zip
integration-test       => selenium over running zeppelin-server (from package)


verify:
 pre-inegration-test   => start Zeppelin
 integration-test
 post-inegration-test  => stop Zeppelin
