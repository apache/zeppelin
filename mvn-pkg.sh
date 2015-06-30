#!/usr/bin/env bash

MAVEN_OPTS="-Xmx2g -XX:MaxPermSize=512M -XX:ReservedCodeCacheSize=512m" mvn clean package -DskipTests=true -Pbuild-distr -Phadoop-2.4 -Pspark-1.4