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


from py4j.java_gateway import java_import, JavaGateway, GatewayClient

from pyflink.common import *
from pyflink.dataset import *
from pyflink.datastream import *
from pyflink.table import *
from pyflink.table.catalog import *
from pyflink.table.descriptors import *
from pyflink.table.window import *

import pyflink

# start JVM gateway
if "PY4J_GATEWAY_SECRET" in os.environ:
    from py4j.java_gateway import GatewayParameters
    gateway_secret = os.environ["PY4J_GATEWAY_SECRET"]
    gateway = JavaGateway(gateway_parameters=GatewayParameters(address="${JVM_GATEWAY_ADDRESS}",
        port=${JVM_GATEWAY_PORT}, auth_token=gateway_secret, auto_convert=True))
else:
    gateway = JavaGateway(GatewayClient(address="${JVM_GATEWAY_ADDRESS}", port=${JVM_GATEWAY_PORT}), auto_convert=True)


intp = gateway.entry_point

pyflink.java_gateway._gateway = gateway
pyflink.java_gateway.import_flink_view(gateway)
pyflink.java_gateway.install_exception_handler()

b_env = pyflink.dataset.ExecutionEnvironment(intp.getJavaExecutionEnvironment())
bt_env = BatchTableEnvironment.create(b_env)
s_env = StreamExecutionEnvironment(intp.getJavaStreamExecutionEnvironment())
st_env = StreamTableEnvironment.create(s_env)

class IPyFlinkZeppelinContext(PyZeppelinContext):

    def __init__(self, z, gateway):
        super(IPyFlinkZeppelinContext, self).__init__(z, gateway)

    def show(self, obj):
        from pyflink.table import Table
        if isinstance(obj, Table):
            print(self.z.showData(obj._j_table))
        else:
            super(IPyFlinkZeppelinContext, self).show(obj)

z = __zeppelin__ = IPyFlinkZeppelinContext(intp.getZeppelinContext(), gateway)
