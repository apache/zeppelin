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
from pyspark.conf import SparkConf
from pyspark.context import SparkContext

# for back compatibility
from pyspark.sql import SQLContext

# start JVM gateway
if "PY4J_GATEWAY_SECRET" in os.environ:
    from py4j.java_gateway import GatewayParameters
    gateway_secret = os.environ["PY4J_GATEWAY_SECRET"]
    gateway = JavaGateway(gateway_parameters=GatewayParameters(address="${JVM_GATEWAY_ADDRESS}",
        port=${JVM_GATEWAY_PORT}, auth_token=gateway_secret, auto_convert=True))
else:
    gateway = JavaGateway(GatewayClient(address="${JVM_GATEWAY_ADDRESS}", port=${JVM_GATEWAY_PORT}), auto_convert=True)

java_import(gateway.jvm, "org.apache.spark.SparkEnv")
java_import(gateway.jvm, "org.apache.spark.SparkConf")
java_import(gateway.jvm, "org.apache.spark.api.java.*")
java_import(gateway.jvm, "org.apache.spark.api.python.*")
java_import(gateway.jvm, "org.apache.spark.mllib.api.python.*")

intp = gateway.entry_point

if intp.isSpark3():
    warnings.filterwarnings(action='ignore', module='pyspark.util')

jsc = intp.getJavaSparkContext()

java_import(gateway.jvm, "org.apache.spark.sql.*")
java_import(gateway.jvm, "org.apache.spark.sql.hive.*")
java_import(gateway.jvm, "scala.Tuple2")

jconf = jsc.getConf()
conf = SparkConf(_jvm=gateway.jvm, _jconf=jconf)
sc = _zsc_ = SparkContext(jsc=jsc, gateway=gateway, conf=conf)

if not intp.isSpark1():
    from pyspark.sql import SparkSession
    spark = __zSpark__ = SparkSession(sc, intp.getSparkSession())
    sqlContext = sqlc = __zSqlc__ = __zSpark__._wrapped
else:
    sqlContext = sqlc = __zSqlc__ = SQLContext(sparkContext=sc, sqlContext=intp.getSQLContext())

class IPySparkZeppelinContext(PyZeppelinContext):

    def __init__(self, z, gateway):
        super(IPySparkZeppelinContext, self).__init__(z, gateway)

    def show(self, obj, **kwargs):
        from pyspark.sql import DataFrame
        if isinstance(obj, DataFrame):
            print(self.z.showData(obj._jdf))
        else:
            super(IPySparkZeppelinContext, self).show(obj, **kwargs)

z = __zeppelin__ = IPySparkZeppelinContext(intp.getZeppelinContext(), gateway)
