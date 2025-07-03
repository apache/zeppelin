# Overview
Java interpreter for Apache Zeppelin

# Architecture
Current interpreter implementation supports the static REPL. It compiles the code in memory, execute it and redirect the output to Zeppelin.

### Technical overview

 * Upon starting an interpreter, an instance of `JavaCompiler` is created. 

 * When the user runs commands with java, the `JavaParser` go through the code to get a class that contains the main method.
 
 * Then it replaces the class name with random class name to avoid overriding while compilation. It creates new out & err stream to get the data in new stream instead of the console, to redirect output to Zeppelin.
 
 * If there is any error during compilation, it can catch and redirect to Zeppelin.
 
 * `JavaInterpreterUtils` contains useful methods to print out Java collections and leverage Zeppelin's built in visualization. 

## Run the interpreter with docker
You can run the java interpreter as a standalone docker container.

### Step 1. Specify the configuration for the interpreter
```bash
    # conf/interpreter.json
    
    "java": {
      ...
      "option":
      } {
        "remote": true,
        "port": {INTERPRETER_PROCESS_PORT_IN_HOST},
        "isExistingProcess": true,
        "host": "localhost",
        ...
      }
````

### Step 2. Build and run the interpreter
```bash
zeppelin $ ./mvnw clean install -DskipTests
 
zeppelin $ ./bin/zeppelin-daemon.sh start # start zeppelin server.
# check the port of the interpreter event server. you can find it by looking for the log that starts with "InterpreterEventServer is starting at"
   
zeppelin $ docker build -f ./java/Dockerfile -t java-interpreter .

zeppelin $ docker run -p {INTERPRETER_PROCESS_PORT_IN_HOST}:8085 \
  -e INTERPRETER_EVENT_SERVER_PORT={INTERPRETER_EVENT_SERVER_PORT} \
  java-interpreter
```
