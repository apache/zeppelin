# Overview
Beam interpreter for Apache Zeppelin

# Architecture
Current interpreter implementation supports the static repl. It compiles the code in memory, execute it and redirect the output to zeppelin.

## Building the Beam Interpreter
You have to first build the Beam interpreter by enable the **beam** profile as follows:

```
mvn clean package -Pbeam -DskipTests
```

### Notice
- Flink runner comes with binary compiled for scala 2.10. So, currently we support only Scala 2.10

### Technical overview

 * Upon starting an interpreter, an instance of `JavaCompiler` is created. 

 * When the user runs commands with beam, the `JavaParser` go through the code to get a class that contains the main method.
 
 * Then it replaces the class name with random class name to avoid overriding while  compilation. it creates new out & err stream to get the data in new stream instead of the console, to redirect output to zeppelin.
 
 * If there is any error during compilation, it can catch and redirect to zeppelin.
