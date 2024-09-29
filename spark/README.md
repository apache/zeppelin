# Spark Interpreter

Spark interpreter is the first and most important interpreter of Zeppelin.
It supports multiple versions of Spark and multiple versions of Scala.

# Module structure of Spark interpreter

* interpreter
  - This module is the entry module of Spark interpreter. All the interpreters are defined here. SparkInterpreter is the most important one,
    SparkContext/SparkSession is created here, other interpreters (PySparkInterpreter,IPySparkInterpreter, SparkRInterpreter and etc) are all depends on SparkInterpreter.  
    Due to incompatibility between Scala versions, there are several scala-x modules for each supported Scala version.
    Due to incompatibility between Spark versions, there are several spark-shims modules for each supported Spark version.
* spark-scala-parent
  - Parent module for each Scala module
* scala-2.12
  - Scala module for Scala 2.12
* scala-2.13
  - Scala module for Scala 2.13
* spark-shims
  - Parent module for each Spark module
* spark3-shims
  - Shims module for Spark3

## Run the interpreter with docker
You can run the Spark interpreter as a standalone docker container.

### Step 1. Specify the configuration for the spark interpreter
```bash
    # conf/interpreter.json
    
    "spark": {
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

### Step 2. Build and run the spark interpreter
```bash
./mvnw clean install -DskipTests
 
./bin/zeppelin-daemon.sh start
# check the port of the interpreter event server. you can find it by looking for the log that starts with "InterpreterEventServer is starting at"
   
docker build -f ./spark/Dockerfile -t spark-interpreter .

docker run \
--name spark-interpreter \
-p 8085:8085 \
-e INTERPRETER_EVENT_SERVER_PORT=${INTERPRETER_EVENT_SERVER_PORT} \
spark-interpreter
```
