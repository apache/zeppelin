# Overview
MongoDB interpreter for Apache Zeppelin. Thanksgiving to [bbonnin/zeppelin-mongodb-interpreter](https://github.com/bbonnin/zeppelin-mongodb-interpreter).
I found bbonnin's mongodb interpreter was not working with newest zeppelin version, it has not been maintained for a long time.
so I forked this for those people who want to use mongodb in zeppelin.

## Technical overview
it use mongo shell to execute scripts.All you need to do is to configure mongodb interpreter,
and then study mongo aggregate functions.

## How to run the interpreter with docker
You can run the mongodb interpreter as a standalone docker container.

### Step 1. Specify the configuration for the mongodb interpreter
* NOTE: Your mongodb properties should be configured using the host environment settings, such as the URL, username, and password.
```bash
    # conf/interpreter.json
    
    "mongodb": {
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

### Step 2. Build and run the mongodb interpreter
```bash
zeppelin $ ./mvnw clean install -DskipTests
 
zeppelin $ ./bin/zeppelin-daemon.sh start # start zeppelin server.
# check the port of the interpreter event server. you can find it by looking for the log that starts with "InterpreterEventServer is starting at"
   
zeppelin $ docker build -f ./mongodb/Dockerfile -t mongodb-interpreter .

zeppelin $ docker run -p {INTERPRETER_PROCESS_PORT_IN_HOST}:8083 \
  -e INTERPRETER_EVENT_SERVER_PORT={INTERPRETER_EVENT_SERVER_PORT} \
  mongodb-interpreter
```
