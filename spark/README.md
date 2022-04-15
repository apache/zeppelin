# Spark Interpreter

Spark interpreter is the first and most important interpreter of Zeppelin. It supports multiple versions of Spark and multiple versions of Scala.


# Module structure of Spark interpreter

* interpreter     
  - This module is the entry module of Spark interpreter. All the interpreters are defined here. SparkInterpreter is the most important one, 
  SparkContext/SparkSession is created here, other interpreters (PySparkInterpreter,IPySparkInterpreter, SparkRInterpreter and etc) are all depends on SparkInterpreter.  
  Due to incompatibility between Scala versions, there are several scala-x modules for each supported Scala version.
  Due to incompatibility between Spark versions, there are several spark-shims modules for each supported Spark version.
* spark-scala-parent   
  - Parent module for each Scala module
* scala-2.11   
  - Scala module for Scala 2.11
* scala-2.12
  - Scala module for Scala 2.12
* scala-2.13
  - Scala module for Scala 2.13
* spark-shims 
  - Parent module for each Spark module
* spark2-shims
  - Shims module for Spark2
* spark3-shims
  - Shims module for Spark3


