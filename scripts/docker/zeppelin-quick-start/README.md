# Apache Zeppelin Quickstart with Docker Compose

## Getting Started

### Install Docker
Please go to [install](https://www.docker.com/) to install Docker.

### Zeppelin Only
#### Run docker-compose
```bash
docker compose -f docker-compose-zeppelin-only.yml up
```

#### Stop docker-compose
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
