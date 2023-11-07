# Flink Interpreter

This is the doc for Zeppelin developers who want to work on flink interpreter.

## Development Guide 

### Project Structure

Flink interpreter is more complex than other interpreter (such as jdbc, shell). Currently it has following 8 modules
* flink-shims
* flink1.15-shims
* flink1.16-shims
* flink1.17-shims
* flink-scala-parent
* flink-scala-2.12

The first 4 modules are to adapt different flink versions because there're some api changes between different versions of flink.
`flink-shims` is parent module for other shims modules. 
At runtime Flink interpreter will load the FlinkShims based on the current flink versions (See `FlinkShims#loadShims`). 
 
The remaining 2 modules are to adapt different scala versions (Apache Flink only supports Scala 2.12).
`flink-scala-parent` is a parent module for `flink-scala-2.12`.
There's symlink folder `flink-scala-parent` under `flink-scala-2.12`.
When you run maven command to build flink interpreter, the source code in `flink-scala-parent` won't be compiled directly, instead
they will be compiled against different scala versions when building `flink-scala-2.12`. (See `build-helper-maven-plugin` in `pom.xml`)
Both `flink-scala-2.12` build a flink interpreter jar and `FlinkInterpreterLauncher` in `zeppelin-plugins/launcher/flink` will choose the right jar based
on the scala version of flink.

### Work in IDE

#### How to run unit test in IDE

Take `FlinkInterpreterTest` as an example, you need to specify environment variables `FLINK_HOME`, `FLINK_CONF_DIR`, `ZEPPELIN_HOME`. 
See `maven-surefire-plugin` in `pom.xml` for more details
