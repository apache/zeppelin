# Overview
Elasticsearch interpreter for Apache Zeppelin

## Run the interpreter with docker
You can run the Elasticsearch interpreter as a standalone docker container.

### Step 1. Specify the configuration for the elasticsearch interpreter
```bash
    # conf/interpreter.json

    "elasticsearch": {
      ...
      "option":
      } {
        "remote": true,
        "port": ${INTERPRETER_PROCESS_PORT_IN_HOST},
        "isExistingProcess": true,
        "host": "localhost",
        ...
      }
````

### Step 2. Build and run the elasticsearch interpreter
```bash
./mvnw clean install -DskipTests
 
./bin/zeppelin-daemon.sh start
# check the port of the interpreter event server. you can find it by looking for the log that starts with "InterpreterEventServer is starting at"
   
docker build -f ./elasticsearch/Dockerfile -t elasticsearch-interpreter .
docker run \
--name elasticsearch-interpreter \
-p 8087:8087 \
-e INTERPRETER_EVENT_SERVER_PORT=${INTERPRETER_EVENT_SERVER_PORT} \
elasticsearch-interpreter
```
