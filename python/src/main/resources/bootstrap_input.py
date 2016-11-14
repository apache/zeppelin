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

from py4j.java_gateway import JavaGateway
from py4j.java_gateway import java_import, JavaGateway, GatewayClient


client = GatewayClient(port=%PORT%)
gateway = JavaGateway(client)
java_import(gateway.jvm, "org.apache.zeppelin.display.Input")


class Py4jZeppelinContext(PyZeppelinContext):
    """A context impl that uses Py4j to communicate to JVM
    """
    def __init__(self, z):
        PyZeppelinContext.__init__(self)
        self.z = z
        self.paramOption = gateway.jvm.org.apache.zeppelin.display.Input.ParamOption
        self.javaList = gateway.jvm.java.util.ArrayList
        self.max_result = self.z.getMaxResult()
    
    def input(self, name, defaultValue=""):
        return self.z.getGui().input(name, defaultValue)
    
    def select(self, name, options, defaultValue=""):
        javaOptions = gateway.new_array(self.paramOption, len(options))
        i = 0
        for tuple in options:
            javaOptions[i] = self.paramOption(tuple[0], tuple[1])
            i += 1
        return self.z.getGui().select(name, defaultValue, javaOptions)
    
    def checkbox(self, name, options, defaultChecked=[]):
        javaOptions = gateway.new_array(self.paramOption, len(options))
        i = 0
        for tuple in options:
            javaOptions[i] = self.paramOption(tuple[0], tuple[1])
            i += 1
        javaDefaultCheck = self.javaList()
        for check in defaultChecked:
            javaDefaultCheck.append(check)
        return self.z.getGui().checkbox(name, javaDefaultCheck, javaOptions)


z = Py4jZeppelinContext(gateway.entry_point)
