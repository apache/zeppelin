# Flink Interpreter

This is the doc for Zeppelin developers who want to work on flink interpreter.

## Development Guide 

### Project Structure

Flink interpreter is more complex than other interpreter (such as jdbc, shell). Currently it has following 8 modules
* flink-shims
* flink1.12-shims
* flink1.13-shims
* flink1.14-shims
* flink-scala-parent
* flink-scala-2.11
* flink-scala-2.12

The first 5 modules are to adapt different flink versions because there're some api changes between different versions of flink.
`flink-shims` is parent module for other shims modules. 
At runtime Flink interpreter will load the FlinkShims based on the current flink versions (See `FlinkShims#loadShims`). 
 
The remaining 3 modules are to adapt different scala versions (Apache Flink supports 2 scala versions: 2.11 & 2.12).
`flink-scala-parent` is a parent module for `flink-scala-2.11` and `flink-scala-2.12`. It contains common code for both `flink-scala-2.11` and `flink-scala-2.12`.
There's symlink folder `flink-scala-parent` under `flink-scala-2.11` and `flink-scala-2.12`.
When you run maven command to build flink interpreter, the source code in `flink-scala-parent` won't be compiled directly, instead
they will be compiled against different scala versions when building `flink-scala-2.11` & `flink-scala-2.12`. (See `build-helper-maven-plugin` in `pom.xml`)
Both `flink-scala-2.11` and `flink-scala-2.12` build a flink interpreter jar and `FlinkInterpreterLauncher` in `zeppelin-plugins/launcher/flink` will choose the right jar based
on the scala version of flink.

### Work in IDE

Because of the complex project structure of flink interpreter, we need to do more configuration to make it work in IDE.
Here we take Intellij as an example (other IDE should be similar). 

The key point is that we can only make flink interpreter work with one scala version at the same time in IDE. 
So we have to disable the other module when working with one specific scala version module.

#### Make it work with scala-2.11

1. Exclude the source code folder (java/scala) of `flink-scala-parent` (Right click these folder -> Mark directory As -> Excluded)
2. Include the source code folder (java/scala) of `flink/flink-scala-2.11/flink-scala-parent` (Right click these folder -> Mark directory As -> Source root)

#### Make it work with scala-2.12

1. Exclude the source code folder (java/scala) of `flink-scala-parent` (Right click these folder -> Mark directory As -> Excluded)
2. Include the source code folder (java/scala) of `flink/flink-scala-2.12/flink-scala-parent` (Right click these folder -> Mark directory As -> Source root)


#### How to run unit test in IDE

Take `FlinkInterpreterTest` as an example, you need to specify environment variables `FLINK_HOME`, `FLINK_CONF_DIR`, `ZEPPELIN_HOME`. 
See `maven-surefire-plugin` in `pom.xml` for more details
