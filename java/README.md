# Overview
Java interpreter for Apache Zeppelin

# Architecture
Current interpreter implementation supports the static repl. It compiles the code in memory, execute it and redirect the output to zeppelin.

## Building the Java Interpreter
You have to first build the Java interpreter by enable the **java** profile as follows:

```
mvn clean package -Pjava -DskipTests -Pscala-2.10
```

### Technical overview

 * Upon starting an interpreter, an instance of `JavaCompiler` is created. 

 * When the user runs commands with java, the `JavaParser` go through the code to get a class that contains the main method.
 
 * Then it replaces the class name with random class name to avoid overriding while compilation. It creates new out & err stream to get the data in new stream instead of the console, to redirect output to zeppelin.
 
 * If there is any error during compilation, it can catch and redirect to zeppelin.
