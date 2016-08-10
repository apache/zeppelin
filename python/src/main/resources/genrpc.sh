#!/usr/bin/env bash
#/**
# * Licensed to the Apache Software Foundation (ASF) under one
# * or more contributor license agreements.  See the NOTICE file
# * distributed with this work for additional information
# * regarding copyright ownership.  The ASF licenses this file
# * to you under the Apache License, Version 2.0 (the
# * "License"); you may not use this file except in compliance
# * with the License.  You may obtain a copy of the License at
# *
# *     http://www.apache.org/licenses/LICENSE-2.0
# *
# * Unless required by applicable law or agreed to in writing, software
# * distributed under the License is distributed on an "AS IS" BASIS,
# * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# * See the License for the specific language governing permissions and
# * limitations under the License.
# */
#
# Generates gRPC service stubs.
# Requires:
#  - protoc
#    * https://developers.google.com/protocol-buffers/docs/downloads
#    * or `brew instsall protobuff`
#  - gprc plugin for protoc is installed
#    * git checkout git clone git@github.com:grpc/grpc-java.git google-grpc-java
#    * cd google-grpc-java/compiler
#    * ../gradlew java_pluginExecutable
#    * export GRPC_PLUGIN="$(pwd)"

protoc --java_out=./ --grpc-java_out=./ \
       --plugin=protoc-gen-grpc-java="${GRPC_PLUGIN}/build/exe/java_plugin/protoc-gen-grpc-java" \
       python_interpreter.proto

#TODO(alex)
# add licence headers to generated files
# move .py and .java files to appropriate locations


#rm -rf gen-java
#rm -rf ../java/org/apache/zeppelin/interpreter/thrift
#thrift --gen java RemoteInterpreterService.thrift
#for file in gen-java/org/apache/zeppelin/interpreter/thrift/* ; do
#  cat java_license_header.txt ${file} > ${file}.tmp
#  mv -f ${file}.tmp ${file}
#done
#mv gen-java/org/apache/zeppelin/interpreter/thrift ../java/org/apache/zeppelin/interpreter/thrift
#rm -rf gen-java
