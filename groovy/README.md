# Groovy Interpreter

[see groovy documentation](../docs/interpreter/groovy.md)

## Overview
Groovy interpreter for Apache Zeppelin

## Run the interpreter with docker
You can run the Groovy interpreter as a standalone docker container.

### Step 1. Specify the configuration for the Groovy interpreter
```bash
    # conf/interpreter.json

    "groovy": {
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

### Step 2. Build and run the Groovy interpreter
```bash
$ ./mvnw clean install -DskipTests
 
$ ./bin/zeppelin-daemon.sh start # start zeppelin server.
# check the port of the interpreter event server. you can find it by looking for the log that starts with "InterpreterEventServer is starting at"
   
$ docker build -f ./groovy/Dockerfile -t groovy-interpreter .

$ docker run -p {INTERPRETER_PROCESS_PORT_IN_HOST}:8180 \
  -e INTERPRETER_EVENT_SERVER_PORT={INTERPRETER_EVENT_SERVER_PORT} \
  groovy-interpreter
```