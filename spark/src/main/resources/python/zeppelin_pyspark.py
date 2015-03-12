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
java_import(gateway.jvm, "org.apache.spark.sql.SQLContext")
java_import(gateway.jvm, "org.apache.spark.sql.hive.HiveContext")
java_import(gateway.jvm, "org.apache.spark.sql.hive.LocalHiveContext")
java_import(gateway.jvm, "org.apache.spark.sql.hive.TestHiveContext")
java_import(gateway.jvm, "scala.Tuple2")


intp = gateway.entry_point

jsc = intp.getJavaSparkContext()
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

    for s in stmts:
      if s == None or len(s.strip()) == 0:
        continue

      if single == None:
        single = s
      else:
        single += "\n" + s

      try :
        sc.setJobGroup(jobGroup, "Zeppelin")
        eval(compile(single, "<String>", "single"))
        single = ""
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

    intp.setStatementsFinished(output.get(), False)
  except:
    intp.setStatementsFinished(str(sys.exc_info()), True)

  output.reset()
