FROM alpine:3.3
MAINTAINER Mahmoud Elgamal <mahmoudf.elgamal@gmail.com>

RUN apk add --update bash curl openjdk7-jre wget ca-certificates openssl && rm -rf /var/cache/apk/*

RUN wget -O /usr/local/bin/dumb-init https://github.com/Yelp/dumb-init/releases/download/v1.1.3/dumb-init_1.1.3_amd64
RUN chmod +x /usr/local/bin/dumb-init

# set jave environment variable
ENV JAVA_HOME /usr/lib/jvm/java-1.7-openjdk
ENV PATH $PATH:$JAVA_HOME/bin

# ports for zeppelin
EXPOSE 8080 8081

ENTRYPOINT ["/usr/local/bin/dumb-init"]
