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

import sys, getopt

from py4j.java_gateway import java_import, JavaGateway, GatewayClient
from pyspark.conf import SparkConf
from pyspark.context import SparkContext
from pyspark.rdd import RDD
from pyspark.files import SparkFiles
from pyspark.storagelevel import StorageLevel
from pyspark.accumulators import Accumulator, AccumulatorParam
from pyspark.broadcast import Broadcast
from pyspark.serializers import MarshalSerializer, PickleSerializer

# for back compatibility
from pyspark.sql import SQLContext, HiveContext, SchemaRDD, Row

client = GatewayClient(port=int(sys.argv[1]))
gateway = JavaGateway(client)

java_import(gateway.jvm, "org.apache.spark.SparkEnv")
java_import(gateway.jvm, "org.apache.spark.SparkConf")
java_import(gateway.jvm, "org.apache.spark.api.java.*")
java_import(gateway.jvm, "org.apache.spark.api.python.*")
java_import(gateway.jvm, "org.apache.spark.mllib.api.python.*")

intp = gateway.entry_point
intp.onPythonScriptInitialized()

jsc = intp.getJavaSparkContext()

if jsc.version().startswith("1.2"):
  java_import(gateway.jvm, "org.apache.spark.sql.SQLContext")
  java_import(gateway.jvm, "org.apache.spark.sql.hive.HiveContext")
  java_import(gateway.jvm, "org.apache.spark.sql.hive.LocalHiveContext")
  java_import(gateway.jvm, "org.apache.spark.sql.hive.TestHiveContext")
elif jsc.version().startswith("1.3"):
  java_import(gateway.jvm, "org.apache.spark.sql.*")
  java_import(gateway.jvm, "org.apache.spark.sql.hive.*")


java_import(gateway.jvm, "scala.Tuple2")

jconf = intp.getSparkConf()
conf = SparkConf(_jvm = gateway.jvm, _jconf = jconf)
sc = SparkContext(jsc=jsc, gateway=gateway, conf=conf)
sqlc = SQLContext(sc, intp.getSQLContext())

z = intp.getZeppelinContext()

class Logger(object):
  def __init__(self):
    self.out = ""

  def write(self, message):
    self.out = self.out + message

  def get(self):
    return self.out

  def reset(self):
    self.out = ""

output = Logger()
sys.stdout = output
sys.stderr = output

while True :
  req = intp.getStatements()
  try:
    stmts = req.statements().split("\n")
    jobGroup = req.jobGroup()
    single = None
    incomplete = None
    compiledCode = None

    for s in stmts:
      if s == None or len(s.strip()) == 0:
        continue

      # skip comment
      if s.strip().startswith("#"):
        continue

      if s[0] != " " and s[0] != "\t":
        if incomplete != None:
          raise incomplete

        if compiledCode != None:
          sc.setJobGroup(jobGroup, "Zeppelin")
          eval(compiledCode)
          compiledCode = None
          single = None
          incomplete = None

      if single == None:
        single = s
      else:
        single += "\n" + s

      try :
        compiledCode = compile(single, "<string>", "single")
        incomplete = None
      except SyntaxError as e:
        if str(e).startswith("unexpected EOF while parsing") :
          # incomplete expression
          incomplete = e
          continue
        else :
          # actual error
          raise e

    if incomplete != None:
      raise incomplete

    if compiledCode != None:
      sc.setJobGroup(jobGroup, "Zeppelin")
      eval(compiledCode)

    intp.setStatementsFinished(output.get(), False)
  except:
    intp.setStatementsFinished(str(sys.exc_info()), True)

  output.reset()
    
