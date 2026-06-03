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

# Bootstrap a native PySpark Spark Connect session. The Java interpreter
# populates SPARK_REMOTE with the connection URI (including any token /
# use_ssl / user_id params). Java/SQL and Python connect as independent
# sessions to the same Spark Connect server; cross-language sharing
# happens through catalog tables.

import os
import warnings

from pyspark.sql import SparkSession

warnings.filterwarnings(action='ignore', module='pyspark.util')

_remote = os.environ.get("SPARK_REMOTE")
if not _remote:
    raise RuntimeError(
        "SPARK_REMOTE env var not set. The Java interpreter is expected "
        "to populate it from the 'spark.remote' interpreter property.")

spark = SparkSession.builder.remote(_remote).getOrCreate()
sqlContext = sqlc = spark
