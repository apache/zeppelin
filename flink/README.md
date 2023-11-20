# Flink Interpreter

This is the doc for Zeppelin developers who want to work on flink interpreter.

## Development Guide 

### Project Structure

Flink interpreter is more complex than other interpreter (such as jdbc, shell).
Currently, it has the following modules clustered into two groups:

* flink-shims
* flink1.15-shims
* flink1.16-shims
* flink1.17-shims

* flink-scala-2.12

The modules in the first group are to adapt different flink versions because there're some api changes between different versions of flink.
`flink-shims` is parent module for other shims modules. 
At runtime Flink interpreter will load the FlinkShims based on the current flink versions (See `FlinkShims#loadShims`). 
 
The modules in the second group are to adapt different scala versions. But since flink 1.15, it only supports Scala 2.12, thus there is only one module `flink-scala-2.12`

### Work in IDE

#### How to run unit test in IDE

Take `FlinkInterpreterTest` as an example, you need to specify environment variables `FLINK_HOME`, `FLINK_CONF_DIR`, `ZEPPELIN_HOME`. 
See `maven-surefire-plugin` in `pom.xml` for more details
