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
  stmts = intp.getStatements()
  try:
    eval(compile(stmts, "<String>", "single"))
    intp.setStatementsFinished(output.get(), False)
  except:
    intp.setStatementsFinished(sys.exc_info()[0], True)
  finally:
    output.reset()
