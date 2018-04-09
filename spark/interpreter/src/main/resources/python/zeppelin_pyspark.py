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

import os, sys, getopt, traceback, json, re

from py4j.java_gateway import java_import, JavaGateway, GatewayClient
from py4j.protocol import Py4JJavaError
from pyspark.conf import SparkConf
from pyspark.context import SparkContext
import ast
import warnings

# for back compatibility
from pyspark.sql import SQLContext, HiveContext, Row

class Logger(object):
  def __init__(self):
    pass

  def write(self, message):
    intp.appendOutput(message)

  def reset(self):
    pass

  def flush(self):
    pass


class SparkVersion(object):
  SPARK_1_4_0 = 10400
  SPARK_1_3_0 = 10300
  SPARK_2_0_0 = 20000

  def __init__(self, versionNumber):
    self.version = versionNumber

  def isAutoConvertEnabled(self):
    return self.version >= self.SPARK_1_4_0

  def isImportAllPackageUnderSparkSql(self):
    return self.version >= self.SPARK_1_3_0

  def isSpark2(self):
    return self.version >= self.SPARK_2_0_0

class PySparkCompletion:
  def __init__(self, interpreterObject):
    self.interpreterObject = interpreterObject

  def getGlobalCompletion(self, text_value):
    completions = [completion for completion in list(globals().keys()) if completion.startswith(text_value)]
    return completions

  def getMethodCompletion(self, objName, methodName):
    execResult = locals()
    try:
      exec("{} = dir({})".format("objectDefList", objName), globals(), execResult)
    except:
      return None
    else:
      objectDefList = execResult['objectDefList']
      return [completion for completion in execResult['objectDefList'] if completion.startswith(methodName)]

  def getCompletion(self, text_value):
    if text_value == None:
      return None

    dotPos = text_value.find(".")
    if dotPos == -1:
      objName = text_value
      completionList = self.getGlobalCompletion(objName)
    else:
      objName = text_value[:dotPos]
      methodName = text_value[dotPos + 1:]
      completionList = self.getMethodCompletion(objName, methodName)

    if len(completionList) <= 0:
      self.interpreterObject.setStatementsFinished("", False)
    else:
      result = json.dumps(list(filter(lambda x : not re.match("^__.*", x), list(completionList))))
      self.interpreterObject.setStatementsFinished(result, False)

client = GatewayClient(port=int(sys.argv[1]))
sparkVersion = SparkVersion(int(sys.argv[2]))
if sparkVersion.isSpark2():
  from pyspark.sql import SparkSession
else:
  from pyspark.sql import SchemaRDD

if sparkVersion.isAutoConvertEnabled():
  gateway = JavaGateway(client, auto_convert = True)
else:
  gateway = JavaGateway(client)

java_import(gateway.jvm, "org.apache.spark.SparkEnv")
java_import(gateway.jvm, "org.apache.spark.SparkConf")
java_import(gateway.jvm, "org.apache.spark.api.java.*")
java_import(gateway.jvm, "org.apache.spark.api.python.*")
java_import(gateway.jvm, "org.apache.spark.mllib.api.python.*")

intp = gateway.entry_point
output = Logger()
sys.stdout = output
sys.stderr = output

jsc = intp.getJavaSparkContext()

if sparkVersion.isImportAllPackageUnderSparkSql():
  java_import(gateway.jvm, "org.apache.spark.sql.*")
  java_import(gateway.jvm, "org.apache.spark.sql.hive.*")
else:
  java_import(gateway.jvm, "org.apache.spark.sql.SQLContext")
  java_import(gateway.jvm, "org.apache.spark.sql.hive.HiveContext")
  java_import(gateway.jvm, "org.apache.spark.sql.hive.LocalHiveContext")
  java_import(gateway.jvm, "org.apache.spark.sql.hive.TestHiveContext")


java_import(gateway.jvm, "scala.Tuple2")

_zcUserQueryNameSpace = {}

jconf = intp.getSparkConf()
conf = SparkConf(_jvm = gateway.jvm, _jconf = jconf)
sc = _zsc_ = SparkContext(jsc=jsc, gateway=gateway, conf=conf)
_zcUserQueryNameSpace["_zsc_"] = _zsc_
_zcUserQueryNameSpace["sc"] = sc

if sparkVersion.isSpark2():
  spark = __zSpark__ = SparkSession(sc, intp.getSparkSession())
  sqlc = __zSqlc__ = __zSpark__._wrapped
  _zcUserQueryNameSpace["sqlc"] = sqlc
  _zcUserQueryNameSpace["__zSqlc__"] = __zSqlc__
  _zcUserQueryNameSpace["spark"] = spark
  _zcUserQueryNameSpace["__zSpark__"] = __zSpark__
else:
  sqlc = __zSqlc__ = SQLContext(sparkContext=sc, sqlContext=intp.getSQLContext())
  _zcUserQueryNameSpace["sqlc"] = sqlc
  _zcUserQueryNameSpace["__zSqlc__"] = sqlc

sqlContext = __zSqlc__
_zcUserQueryNameSpace["sqlContext"] = sqlContext

completion = __zeppelin_completion__ = PySparkCompletion(intp)
_zcUserQueryNameSpace["completion"] = completion
_zcUserQueryNameSpace["__zeppelin_completion__"] = __zeppelin_completion__


from zeppelin_context import PyZeppelinContext

#TODO(zjffdu) merge it with IPySparkZeppelinContext
class PySparkZeppelinContext(PyZeppelinContext):

  def __init__(self, z, gateway):
    super(PySparkZeppelinContext, self).__init__(z, gateway)

  def show(self, obj):
    from pyspark.sql import DataFrame
    if isinstance(obj, DataFrame):
      print(self.z.showData(obj._jdf))
    else:
      super(PySparkZeppelinContext, self).show(obj)

z = __zeppelin__ = PySparkZeppelinContext(intp.getZeppelinContext(), gateway)

__zeppelin__._setup_matplotlib()
_zcUserQueryNameSpace["z"] = z
_zcUserQueryNameSpace["__zeppelin__"] = __zeppelin__

intp.onPythonScriptInitialized(os.getpid())

while True :
  req = intp.getStatements()
  try:
    stmts = req.statements().split("\n")
    jobGroup = req.jobGroup()
    jobDesc = req.jobDescription()
    isForCompletion = req.isForCompletion()

    # Get post-execute hooks
    try:
      global_hook = intp.getHook('post_exec_dev')
    except:
      global_hook = None
      
    try:
      user_hook = __zeppelin__.getHook('post_exec')
    except:
      user_hook = None
      
    nhooks = 0
    if not isForCompletion:
      for hook in (global_hook, user_hook):
        if hook:
          nhooks += 1

    if stmts:
      # use exec mode to compile the statements except the last statement,
      # so that the last statement's evaluation will be printed to stdout
      sc.setJobGroup(jobGroup, jobDesc)
      code = compile('\n'.join(stmts), '<stdin>', 'exec', ast.PyCF_ONLY_AST, 1)
      to_run_hooks = []
      if (nhooks > 0):
        to_run_hooks = code.body[-nhooks:]

      to_run_exec, to_run_single = (code.body[:-(nhooks + 1)],
                                    [code.body[-(nhooks + 1)]])
      try:
        for node in to_run_exec:
          mod = ast.Module([node])
          code = compile(mod, '<stdin>', 'exec')
          exec(code, _zcUserQueryNameSpace)

        for node in to_run_single:
          mod = ast.Interactive([node])
          code = compile(mod, '<stdin>', 'single')
          exec(code, _zcUserQueryNameSpace)
          
        for node in to_run_hooks:
          mod = ast.Module([node])
          code = compile(mod, '<stdin>', 'exec')
          exec(code, _zcUserQueryNameSpace)

        if not isForCompletion:
          # only call it when it is not for code completion. code completion will call it in
          # PySparkCompletion.getCompletion
          intp.setStatementsFinished("", False)
      except Py4JJavaError:
        # raise it to outside try except
        raise
      except:
        if not isForCompletion:
          exception = traceback.format_exc()
          m = re.search("File \"<stdin>\", line (\d+).*", exception)
          if m:
            line_no = int(m.group(1))
            intp.setStatementsFinished(
              "Fail to execute line {}: {}\n".format(line_no, stmts[line_no - 1]) + exception, True)
          else:
            intp.setStatementsFinished(exception, True)
    else:
      intp.setStatementsFinished("", False)

  except Py4JJavaError:
    excInnerError = traceback.format_exc() # format_tb() does not return the inner exception
    innerErrorStart = excInnerError.find("Py4JJavaError:")
    if innerErrorStart > -1:
       excInnerError = excInnerError[innerErrorStart:]
    intp.setStatementsFinished(excInnerError + str(sys.exc_info()), True)
  except:
    intp.setStatementsFinished(traceback.format_exc(), True)

  output.reset()
