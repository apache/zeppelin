## Overview
Jdbc interpreter for Apache Zeppelin

## Run the interpreter with docker
You can run the jdbc interpreter as a standalone docker container.

### Step 1. Specify the configuration for the jdbc interpreter
* NOTE: Your jdbc properties should be configured using the host environment settings, such as the URL, username, and password.
```bash
    # conf/interpreter.json
    
    "jdbc": {
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

### Step 2. Build and run the jdbc interpreter
```bash
zeppelin $ ./mvnw clean install -DskipTests
 
zeppelin $ ./bin/zeppelin-daemon.sh start # start zeppelin server.
# check the port of the interpreter event server. you can find it by looking for the log that starts with "InterpreterEventServer is starting at"
   
zeppelin $ docker build -f ./jdbc/Dockerfile -t jdbc-interpreter .

zeppelin $ docker run -p {INTERPRETER_PROCESS_PORT_IN_HOST}:8082 \
  -e INTERPRETER_EVENT_SERVER_PORT={INTERPRETER_EVENT_SERVER_PORT} \
  jdbc-interpreter
```
