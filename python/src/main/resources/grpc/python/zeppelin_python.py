#
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

from py4j.java_gateway import java_import, JavaGateway, GatewayClient, CallbackServerParameters
import os

# start JVM gateway
if "PY4J_GATEWAY_SECRET" in os.environ:
    from py4j.java_gateway import GatewayParameters
    gateway_secret = os.environ["PY4J_GATEWAY_SECRET"]
    gateway = JavaGateway(gateway_parameters=GatewayParameters(address="${JVM_GATEWAY_ADDRESS}",
        port=${JVM_GATEWAY_PORT}, auth_token=gateway_secret, auto_convert=True),
        start_callback_server=True,
        callback_server_parameters=CallbackServerParameters())
    java_import(gateway.jvm, "org.apache.zeppelin.display.Input")
    intp = gateway.entry_point
else:
    gateway = JavaGateway(GatewayClient(address="${JVM_GATEWAY_ADDRESS}", port=${JVM_GATEWAY_PORT}), auto_convert=True,
        start_callback_server=True,
        callback_server_parameters=CallbackServerParameters())
    java_import(gateway.jvm, "org.apache.zeppelin.display.Input")
    intp = gateway.entry_point
