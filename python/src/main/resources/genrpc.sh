#!/usr/bin/env bash
#
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
#
# Generates gRPC service stubs.
#
# Requires:
#  - protoc
#    * https://developers.google.com/protocol-buffers/docs/downloads
#    * or `brew instsall protobuff`
#  - gprc plugin for protoc is installed
#    * git checkout git clone git@github.com:grpc/grpc-java.git google-grpc-java
#    * cd google-grpc-java/compiler
#    * ../gradlew java_pluginExecutable
#    * export GRPC_PLUGIN="$(pwd)"
#  - python grpcio-tool installed
#    * http://www.grpc.io/docs/tutorials/basic/python.html#generating-client-and-server-code
#    * pip install grpcio-tools


E_BAD_CONFIG=-1
E_FAIL_COMPILE=-2
E_BAD_GRPC_JAVA=-3
E_BAD_GRPC_PY=-4

if ! $(hash 'protoc' 2>/dev/null) ; then
  echo "Please install protobuff compiler + grpc-java plugin"
  exit "${E_BAD_GRPC_JAVA}"
fi

if [[ -z "${GRPC_PLUGIN}" ]]; then
  echo "Please set env var GRPC_PLUGIN poining to grpc-java protoc compiler plugin"
  exit "${E_BAD_CONFIG}"
fi

if ! $(python -m grpc.tools.protoc 2>/dev/null) ; then
  echo "Please install 'grpcio-tools' Python package"
  exit "${E_BAD_GRPC_PY}"
fi

java_out="./gen-java"
rm -rf "${java_out}"
rm -rf ../java/org/apache/zeppelin/python2/rpc
mkdir "${java_out}"

echo "Generating Java code"
protoc --java_out="${java_out}" --grpc-java_out="${java_out}" \
       --plugin=protoc-gen-grpc-java="${GRPC_PLUGIN}/build/exe/java_plugin/protoc-gen-grpc-java" \
       python_interpreter.proto
if [[ "$?" -ne 0 ]]; then
  echo "GRPC code generation for Java failed" >&2
  exit "${E_FAIL_COMPILE}"
fi

for file in "${java_out}"/org/apache/zeppelin/python2/rpc/* ; do
  echo "Adding ASF License header to ${file}"
  cat ../../../../zeppelin-interpreter/src/main/thrift/java_license_header.txt ${file} > ${file}.tmp
  mv -f ${file}.tmp ${file}
done
mv "${java_out}"/org/apache/zeppelin/python2/rpc ../java/org/apache/zeppelin/python2/rpc
rm -rf gen-java

find . -name "*_pb2.py" -exec rm -rf {} \;
echo "Generating Python code"
python -m grpc.tools.protoc -I./ --python_out=. --grpc_python_out=. ./python_interpreter.proto
for file in $(find . -name '*_pb2.py'); do
  echo "Adding ASF License header to ${file}"
  cat python_license_header.txt ${file} > ${file}.tmp
  mv -f ${file}.tmp ${file}
done

