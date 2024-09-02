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

# Determine the base directory dynamically (directory where the script is located)
THRIFT_EXEC="$(dirname "$(realpath "$0")")"

# Change to THRIFT_EXEC
cd "$THRIFT_EXEC" || exit

# Remove existing generated Java files
rm -rf "$THRIFT_EXEC/gen-java"
rm -rf "$THRIFT_EXEC/../java/org/apache/zeppelin/interpreter/thrift"

# Generate Thrift files
thrift --gen java RemoteInterpreterService.thrift
thrift --gen java RemoteInterpreterEventService.thrift

# Add license header and move files
for file in "$THRIFT_EXEC/gen-java/org/apache/zeppelin/interpreter/thrift/"*; do
  cat "$THRIFT_EXEC/java_license_header.txt" "${file}" > "${file}.tmp"
  mv -f "${file}.tmp" "${file}"
done

mv "$THRIFT_EXEC/gen-java/org/apache/zeppelin/interpreter/thrift" "$THRIFT_EXEC/../java/org/apache/zeppelin/interpreter/thrift"
rm -rf "$THRIFT_EXEC/gen-java"
