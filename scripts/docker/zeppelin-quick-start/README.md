# Apache Zeppelin Quickstart with Docker Compose

## Getting Started

### Install Docker
Please go to [install](https://www.docker.com/) to install Docker.

### Zeppelin Only
#### Run docker compose
```bash
docker compose -f docker-compose-zeppelin-only.yml up
```

#### Stop docker compose
```bash
docker compose -f docker-compose-zeppelin-only.yml stop
```

### Apache Zeppelin Environment Variables
- Please check the [link](https://zeppelin.apache.org/docs/0.11.1/setup/operation/configuration.html) for more details
```dockerfile
    environment:
      ZEPPELIN_ADDR: 127.0.0.1 # Zeppelin server binding address
      ZEPPELIN_PORT: 8080 # Zeppelin server port
      ZEPPELIN_SSL_PORT: 8443 # Zeppelin Server ssl port
      ZEPPELIN_JMX_ENABLE: false # Enable JMX by defining "true"
      ZEPPELIN_JMX_PORT: 9996 # Port number which JMX uses
      ZEPPELIN_MEM: -Xmx1024m -XX:MaxMetaspaceSize=512m # JVM mem options
```

### Zeppelin With Apache Spark
#### Install Spark Binary File
```bash
cd scripts/docker/zeppelin-quick-start
wget https://archive.apache.org/dist/spark/spark-3.2.4/spark-3.2.4-bin-hadoop3.2.tgz
tar -xvf spark-3.2.4-bin-hadoop3.2.tgz
```

#### Run docker compose
```bash
docker compose -f docker-compose-with-spark.yml up
```

#### Stop docker compose
```bash
docker compose -f docker-compose-with-spark.yml stop
```

#### Example
```
%spark.conf

SPARK_HOME /opt/spark
spark.master spark://spark-master:7077
```
```
%spark

val sdf = spark.createDataFrame(Seq((0, "park", 13, 70, "Korea"), (1, "xing", 14, 80, "China"), (2, "john", 15, 90, "USA"))).toDF("id", "name", "age", "score", "country")
sdf.printSchema
sdf.show()
```
```
root
 |-- id: integer (nullable = false)
 |-- name: string (nullable = true)
 |-- age: integer (nullable = false)
 |-- score: integer (nullable = false)
 |-- country: string (nullable = true)

+---+----+---+-----+-------+
| id|name|age|score|country|
+---+----+---+-----+-------+
|  0|park| 13|   70|  Korea|
|  1|xing| 14|   80|  China|
|  2|john| 15|   90|    USA|
+---+----+---+-----+-------+
```

### Apache Spark Environment Variables
Please check the [link](https://github.com/bitnami/containers/blob/main/bitnami/spark/README.md) for more details

```dockerfile
  spark-master:
    environment:
      SPARK_MODE: master # Spark cluster mode to run (can be master or worker)	
      SPARK_RPC_AUTHENTICATION_ENABLED: no # Enable RPC authentication	
      SPARK_RPC_ENCRYPTION_ENABLED: no # Enable RPC encryption
      SPARK_LOCAL_STORAGE_ENCRYPTION_ENABLED: no # Enable local storage encryption	
      SPARK_SSL_ENABLED: no # Enable SSL configuration
      SPARK_USER: spark # Spark user
      SPARK_MASTER_PORT: 7077
      SPARK_MASTER_WEBUI_PORT: 8080

  spark-worker:
    environment:
      SPARK_MODE: worker # Spark cluster mode to run (can be master or worker)
      SPARK_MASTER_URL: spark://spark-master:7077 # Url where the worker can find the master. Only needed when spark mode is worker.
      SPARK_WORKER_MEMORY: 2G
      SPARK_WORKER_CORES: 2
      SPARK_RPC_AUTHENTICATION_ENABLED: no # Enable RPC authentication
      SPARK_RPC_ENCRYPTION_ENABLED: no # Enable RPC encryption
      SPARK_LOCAL_STORAGE_ENCRYPTION_ENABLED: no # Enable local storage encryption
      SPARK_SSL_ENABLED: no # Enable SSL configuration
      SPARK_USER: spark # Spark user
      SPARK_WORKER_WEBUI_PORT: 8081
```
