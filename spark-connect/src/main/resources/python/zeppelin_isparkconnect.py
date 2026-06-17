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

# IPython variant: same wrappers as zeppelin_sparkconnect.py.
#
# Guards against OOM on large collect/toPandas by enforcing configurable
# row limits and providing safe iteration helpers.

import sys
import warnings

intp = gateway.entry_point
_jspark = intp.getSparkSession()
_max_result = intp.getMaxResult()

_COLLECT_LIMIT_DEFAULT = _max_result
_COLLECT_WARN_THRESHOLD = 100000


class Row(tuple):
    """Lightweight PySpark-compatible Row that wraps values extracted from Java Row objects."""

    def __new__(cls, **kwargs):
        row = tuple.__new__(cls, kwargs.values())
        row.__dict__['_fields'] = tuple(kwargs.keys())
        return row

    def __repr__(self):
        pairs = ", ".join("%s=%r" % (k, v) for k, v in zip(self._fields, self))
        return "Row(%s)" % pairs

    def __getattr__(self, name):
        try:
            idx = self._fields.index(name)
            return self[idx]
        except ValueError:
            raise AttributeError("Row has no field '%s'" % name)

    def asDict(self):
        return dict(zip(self._fields, self))


def _convert_java_row(jrow, col_names):
    """Convert a single Java Row to a Python Row."""
    values = {}
    for i, col in enumerate(col_names):
        val = jrow.get(i)
        if hasattr(val, 'getClass'):
            val = str(val)
        values[col] = val
    return Row(**values)


def _convert_java_rows(jdf):
    """Convert a Java Dataset's collected rows to Python Row objects."""
    fields = jdf.schema().fields()
    col_names = [f.name() for f in fields]
    jrows = jdf.collectAsList()
    return [_convert_java_row(r, col_names) for r in jrows]


# ---------------------------------------------------------------------------
# Py4j / type-conversion helpers for createDataFrame and __getattr__
# ---------------------------------------------------------------------------

def _is_java_object(obj):
    """Check if obj is a Py4j proxy."""
    return hasattr(obj, '_get_object_id')


def _is_java_dataset(obj):
    """Check if a Py4j proxy represents a Spark Dataset."""
    if not _is_java_object(obj):
        return False
    try:
        return 'Dataset' in obj.getClass().getName()
    except Exception:
        return False


_PYSPARK_TO_JAVA_TYPES = {
    'StringType': 'StringType',
    'IntegerType': 'IntegerType',
    'LongType': 'LongType',
    'DoubleType': 'DoubleType',
    'FloatType': 'FloatType',
    'BooleanType': 'BooleanType',
    'ShortType': 'ShortType',
    'ByteType': 'ByteType',
    'DateType': 'DateType',
    'TimestampType': 'TimestampType',
    'BinaryType': 'BinaryType',
    'NullType': 'NullType',
}


def _pyspark_type_to_java(dt):
    """Convert a PySpark DataType instance to a Java DataType via Py4j gateway."""
    DataTypes = gateway.jvm.org.apache.spark.sql.types.DataTypes
    type_name = type(dt).__name__
    if type_name in _PYSPARK_TO_JAVA_TYPES:
        return getattr(DataTypes, _PYSPARK_TO_JAVA_TYPES[type_name])
    if type_name == 'DecimalType':
        return DataTypes.createDecimalType(dt.precision, dt.scale)
    if type_name == 'ArrayType':
        return DataTypes.createArrayType(
            _pyspark_type_to_java(dt.elementType), dt.containsNull)
    if type_name == 'MapType':
        return DataTypes.createMapType(
            _pyspark_type_to_java(dt.keyType),
            _pyspark_type_to_java(dt.valueType), dt.valueContainsNull)
    if type_name == 'StructType':
        return _pyspark_schema_to_java(dt)
    return DataTypes.StringType


def _pyspark_schema_to_java(pyspark_schema):
    """Convert a PySpark StructType to a Java StructType."""
    DataTypes = gateway.jvm.org.apache.spark.sql.types.DataTypes
    java_fields = gateway.jvm.java.util.ArrayList()
    for field in pyspark_schema.fields:
        jtype = _pyspark_type_to_java(field.dataType)
        java_fields.add(DataTypes.createStructField(
            field.name, jtype, getattr(field, 'nullable', True)))
    return DataTypes.createStructType(java_fields)


def _infer_java_type(value):
    """Infer a Java DataType from a Python value."""
    DataTypes = gateway.jvm.org.apache.spark.sql.types.DataTypes
    if value is None:
        return DataTypes.StringType
    if isinstance(value, bool):
        return DataTypes.BooleanType
    if isinstance(value, int):
        return DataTypes.LongType if abs(value) > 2147483647 else DataTypes.IntegerType
    if isinstance(value, float):
        return DataTypes.DoubleType
    return DataTypes.StringType


def _resolve_schema(schema, data):
    """Resolve any schema representation to a Java StructType."""
    if schema is None:
        return _infer_schema(data)
    if _is_java_object(schema):
        return schema
    if hasattr(schema, 'fields') and not _is_java_object(schema):
        return _pyspark_schema_to_java(schema)
    if isinstance(schema, str):
        try:
            return gateway.jvm.org.apache.spark.sql.types.StructType.fromDDL(schema)
        except Exception:
            raise ValueError("Cannot parse DDL schema: %s" % schema)
    if isinstance(schema, (list, tuple)) and schema and isinstance(schema[0], str):
        return _schema_from_names(schema, data)
    raise ValueError("Unsupported schema type: %s" % type(schema).__name__)


def _infer_schema(data):
    """Infer a Java StructType from the first element of the data."""
    if not data:
        raise ValueError("Cannot infer schema from empty data without a schema")
    DataTypes = gateway.jvm.org.apache.spark.sql.types.DataTypes
    first = data[0]
    if isinstance(first, Row):
        names, values = list(first._fields), list(first)
    elif isinstance(first, dict):
        names, values = list(first.keys()), list(first.values())
    elif isinstance(first, (list, tuple)):
        names = ["_%d" % (i + 1) for i in range(len(first))]
        values = list(first)
    else:
        names, values = ["value"], [first]
    java_fields = gateway.jvm.java.util.ArrayList()
    for i, name in enumerate(names):
        java_fields.add(DataTypes.createStructField(
            name, _infer_java_type(values[i] if i < len(values) else None), True))
    return DataTypes.createStructType(java_fields)


def _schema_from_names(col_names, data):
    """Create a Java StructType from column name list, inferring types from data."""
    DataTypes = gateway.jvm.org.apache.spark.sql.types.DataTypes
    first = data[0] if data else None
    java_fields = gateway.jvm.java.util.ArrayList()
    for i, name in enumerate(col_names):
        jtype = DataTypes.StringType
        if first is not None:
            val = None
            if isinstance(first, (list, tuple)) and i < len(first):
                val = first[i]
            elif isinstance(first, dict):
                val = first.get(name)
            elif isinstance(first, Row) and i < len(first):
                val = first[i]
            if val is not None:
                jtype = _infer_java_type(val)
        java_fields.add(DataTypes.createStructField(name, jtype, True))
    return DataTypes.createStructType(java_fields)


def _to_java_rows(data, col_names):
    """Convert Python data (list of Row/dict/tuple/list) to Java ArrayList<Row>."""
    RowFactory = gateway.jvm.org.apache.spark.sql.RowFactory
    java_rows = gateway.jvm.java.util.ArrayList()
    for item in data:
        if isinstance(item, Row):
            java_rows.add(RowFactory.create(*list(item)))
        elif isinstance(item, dict):
            java_rows.add(RowFactory.create(*[item.get(c) for c in col_names]))
        elif isinstance(item, (list, tuple)):
            java_rows.add(RowFactory.create(*list(item)))
        else:
            java_rows.add(RowFactory.create(item))
    return java_rows


class SparkConnectDataFrame(object):
    """Wrapper around a Java Dataset<Row> with production-safe data retrieval."""

    def __init__(self, jdf):
        self._jdf = jdf

    def show(self, n=20, truncate=True):
        effective_n = min(n, _max_result)
        print(intp.formatDataFrame(self._jdf, effective_n))

    def collect(self, limit=None):
        """Collect rows to the driver as Python Row objects.

        Args:
            limit: Max rows to collect. Defaults to zeppelin.spark.maxResult.
                   Pass limit=-1 to collect ALL rows (use with caution).
        """
        if limit is None:
            limit = _COLLECT_LIMIT_DEFAULT
        if limit == -1:
            row_count = self._jdf.count()
            if row_count > _COLLECT_WARN_THRESHOLD:
                warnings.warn(
                    "Collecting %d rows to driver. This may cause OOM. "
                    "Consider using .limit() or .toPandas() with a smaller subset."
                    % row_count)
            return _convert_java_rows(self._jdf)
        return _convert_java_rows(self._jdf.limit(limit))

    def take(self, n):
        return _convert_java_rows(self._jdf.limit(n))

    def head(self, n=1):
        rows = self.take(n)
        if n == 1:
            return rows[0] if rows else None
        return rows

    def first(self):
        return self.head(1)

    def toPandas(self, limit=None):
        """Convert to pandas DataFrame. Applies a safety limit.

        Args:
            limit: Max rows. Defaults to zeppelin.spark.maxResult.
                   Pass limit=-1 for all rows (use with caution on large data).
        """
        try:
            import pandas as pd
        except ImportError:
            raise ImportError(
                "pandas is required for toPandas(). "
                "Install it with: pip install pandas")

        if limit is None:
            limit = _COLLECT_LIMIT_DEFAULT
        if limit == -1:
            source_jdf = self._jdf
        else:
            source_jdf = self._jdf.limit(limit)

        fields = source_jdf.schema().fields()
        col_names = [f.name() for f in fields]
        jrows = source_jdf.collectAsList()

        if len(jrows) == 0:
            return pd.DataFrame(columns=col_names)

        rows_data = []
        for row in jrows:
            rows_data.append([row.get(i) for i in range(len(col_names))])

        return pd.DataFrame(rows_data, columns=col_names)

    def count(self):
        return self._jdf.count()

    def limit(self, n):
        return SparkConnectDataFrame(self._jdf.limit(n))

    def filter(self, condition):
        return SparkConnectDataFrame(self._jdf.filter(condition))

    def select(self, *cols):
        return SparkConnectDataFrame(self._jdf.select(*cols))

    def where(self, condition):
        return self.filter(condition)

    def groupBy(self, *cols):
        return self._jdf.groupBy(*cols)

    def orderBy(self, *cols):
        return SparkConnectDataFrame(self._jdf.orderBy(*cols))

    def sort(self, *cols):
        return self.orderBy(*cols)

    def distinct(self):
        return SparkConnectDataFrame(self._jdf.distinct())

    def drop(self, *cols):
        return SparkConnectDataFrame(self._jdf.drop(*cols))

    def dropDuplicates(self, *cols):
        if cols:
            return SparkConnectDataFrame(self._jdf.dropDuplicates(*cols))
        return SparkConnectDataFrame(self._jdf.dropDuplicates())

    def join(self, other, on=None, how="inner"):
        other_jdf = other._jdf if isinstance(other, SparkConnectDataFrame) else other
        if on is not None:
            return SparkConnectDataFrame(self._jdf.join(other_jdf, on, how))
        return SparkConnectDataFrame(self._jdf.join(other_jdf))

    def union(self, other):
        other_jdf = other._jdf if isinstance(other, SparkConnectDataFrame) else other
        return SparkConnectDataFrame(self._jdf.union(other_jdf))

    def withColumn(self, colName, col):
        return SparkConnectDataFrame(self._jdf.withColumn(colName, col))

    def withColumnRenamed(self, existing, new):
        return SparkConnectDataFrame(self._jdf.withColumnRenamed(existing, new))

    def cache(self):
        self._jdf.cache()
        return self

    def persist(self, storageLevel=None):
        if storageLevel:
            self._jdf.persist(storageLevel)
        else:
            self._jdf.persist()
        return self

    def unpersist(self, blocking=False):
        self._jdf.unpersist(blocking)
        return self

    def explain(self, extended=False):
        if extended:
            self._jdf.explain(True)
        else:
            self._jdf.explain()

    def createOrReplaceTempView(self, name):
        self._jdf.createOrReplaceTempView(name)

    def createTempView(self, name):
        self._jdf.createTempView(name)

    def schema(self):
        return self._jdf.schema()

    def dtypes(self):
        schema = self._jdf.schema()
        return [(f.name(), str(f.dataType())) for f in schema.fields()]

    def columns(self):
        schema = self._jdf.schema()
        return [f.name() for f in schema.fields()]

    def printSchema(self):
        print(self._jdf.schema().treeString())

    def describe(self, *cols):
        if cols:
            return SparkConnectDataFrame(self._jdf.describe(*cols))
        return SparkConnectDataFrame(self._jdf.describe())

    def summary(self, *statistics):
        if statistics:
            return SparkConnectDataFrame(self._jdf.summary(*statistics))
        return SparkConnectDataFrame(self._jdf.summary())

    def isEmpty(self):
        return self._jdf.isEmpty()

    def repartition(self, numPartitions, *cols):
        if cols:
            return SparkConnectDataFrame(self._jdf.repartition(numPartitions, *cols))
        return SparkConnectDataFrame(self._jdf.repartition(numPartitions))

    def coalesce(self, numPartitions):
        return SparkConnectDataFrame(self._jdf.coalesce(numPartitions))

    def toDF(self, *cols):
        return SparkConnectDataFrame(self._jdf.toDF(*cols))

    def unionByName(self, other, allowMissingColumns=False):
        other_jdf = other._jdf if isinstance(other, SparkConnectDataFrame) else other
        return SparkConnectDataFrame(
            self._jdf.unionByName(other_jdf, allowMissingColumns))

    def crossJoin(self, other):
        other_jdf = other._jdf if isinstance(other, SparkConnectDataFrame) else other
        return SparkConnectDataFrame(self._jdf.crossJoin(other_jdf))

    def subtract(self, other):
        other_jdf = other._jdf if isinstance(other, SparkConnectDataFrame) else other
        return SparkConnectDataFrame(self._jdf.subtract(other_jdf))

    def intersect(self, other):
        other_jdf = other._jdf if isinstance(other, SparkConnectDataFrame) else other
        return SparkConnectDataFrame(self._jdf.intersect(other_jdf))

    def exceptAll(self, other):
        other_jdf = other._jdf if isinstance(other, SparkConnectDataFrame) else other
        return SparkConnectDataFrame(self._jdf.exceptAll(other_jdf))

    def sample(self, withReplacement=None, fraction=None, seed=None):
        if withReplacement is None and fraction is None:
            raise ValueError("fraction must be specified")
        if isinstance(withReplacement, float) and fraction is None:
            fraction = withReplacement
            withReplacement = False
        if withReplacement is None:
            withReplacement = False
        if seed is not None:
            return SparkConnectDataFrame(
                self._jdf.sample(withReplacement, fraction, seed))
        return SparkConnectDataFrame(
            self._jdf.sample(withReplacement, fraction))

    def dropna(self, how="any", thresh=None, subset=None):
        na = self._jdf.na()
        if thresh is not None:
            if subset:
                return SparkConnectDataFrame(na.drop(thresh, subset))
            return SparkConnectDataFrame(na.drop(thresh))
        if subset:
            return SparkConnectDataFrame(na.drop(how, subset))
        return SparkConnectDataFrame(na.drop(how))

    def fillna(self, value, subset=None):
        na = self._jdf.na()
        if subset:
            return SparkConnectDataFrame(na.fill(value, subset))
        return SparkConnectDataFrame(na.fill(value))

    @property
    def write(self):
        return self._jdf.write()

    def __repr__(self):
        try:
            return "SparkConnectDataFrame[%s]" % ", ".join(
                f.name() for f in self._jdf.schema().fields())
        except Exception:
            return "SparkConnectDataFrame[schema unavailable]"

    def __getattr__(self, name):
        attr = getattr(self._jdf, name)
        if not callable(attr):
            return attr
        def _method_wrapper(*args, **kwargs):
            result = attr(*args, **kwargs)
            if _is_java_dataset(result):
                return SparkConnectDataFrame(result)
            return result
        return _method_wrapper

    def __iter__(self):
        """Safe iteration with default limit to prevent OOM."""
        return iter(_convert_java_rows(self._jdf.limit(_COLLECT_LIMIT_DEFAULT)))

    def __len__(self):
        return int(self._jdf.count())


class SparkConnectSession(object):
    """Wraps the Java SparkSession so that sql() returns a wrapped DataFrame."""

    def __init__(self, jsession):
        self._jsession = jsession

    def sql(self, query):
        return SparkConnectDataFrame(self._jsession.sql(query))

    def table(self, tableName):
        return SparkConnectDataFrame(self._jsession.table(tableName))

    def read(self):
        return self._jsession.read()

    def createDataFrame(self, data, schema=None):
        """Create a SparkConnectDataFrame from Python data.

        Supports:
            - data: list of Row, list of tuples, list of dicts, pandas DataFrame
            - schema: PySpark StructType, list of column names, DDL string,
                      Java StructType (Py4j proxy), or None (infer from data)
        """
        try:
            import pandas as pd
            if isinstance(data, pd.DataFrame):
                if schema is None:
                    schema = list(data.columns)
                data = data.values.tolist()
        except ImportError:
            pass

        if _is_java_object(data):
            if schema is None:
                return SparkConnectDataFrame(self._jsession.createDataFrame(data))
            java_schema = _resolve_schema(schema, None)
            return SparkConnectDataFrame(
                self._jsession.createDataFrame(data, java_schema))

        java_schema = _resolve_schema(schema, data)
        col_names = [f.name() for f in java_schema.fields()]
        java_rows = _to_java_rows(data, col_names)
        return SparkConnectDataFrame(
            self._jsession.createDataFrame(java_rows, java_schema))

    def range(self, start, end=None, step=1, numPartitions=None):
        if end is None:
            end = start
            start = 0
        if numPartitions:
            return SparkConnectDataFrame(
                self._jsession.range(start, end, step, numPartitions))
        return SparkConnectDataFrame(self._jsession.range(start, end, step))

    @property
    def catalog(self):
        return self._jsession.catalog()

    @property
    def version(self):
        return self._jsession.version()

    @property
    def conf(self):
        return self._jsession.conf()

    def stop(self):
        pass

    def __repr__(self):
        return "SparkConnectSession (via Py4j)"

    def __getattr__(self, name):
        return getattr(self._jsession, name)


def pip_install(*packages):
    """Install Python packages into the interpreter pod's environment.

    Usage:
        pip_install("requests")
        pip_install("requests", "pandas", "numpy==1.24.0")
        pip_install("requests>=2.28,<3.0")
    """
    import subprocess
    import importlib
    import site
    if not packages:
        print("Usage: pip_install('package1', 'package2', ...)")
        return
    cmd = [sys.executable, "-m", "pip", "install", "--quiet"] + list(packages)
    try:
        result = subprocess.run(cmd, capture_output=True, text=True, timeout=300)
        if result.returncode == 0:
            installed = ", ".join(packages)
            print("Successfully installed: %s" % installed)
            if result.stdout.strip():
                print(result.stdout.strip())
            importlib.invalidate_caches()
            for new_path in site.getsitepackages() + [site.getusersitepackages()]:
                if new_path not in sys.path:
                    sys.path.insert(0, new_path)
        else:
            print("pip install failed (exit code %d):" % result.returncode)
            if result.stderr.strip():
                print(result.stderr.strip())
            if result.stdout.strip():
                print(result.stdout.strip())
    except subprocess.TimeoutExpired:
        print("pip install timed out after 300 seconds")
    except Exception as e:
        print("pip install error: %s" % str(e))


def display(obj, n=20):
    """Databricks-compatible display function.

    For SparkConnectDataFrame, renders as Zeppelin %table format.
    For other objects, falls back to print().
    """
    if isinstance(obj, SparkConnectDataFrame):
        obj.show(n)
    else:
        print(obj)


spark = SparkConnectSession(_jspark)
sqlContext = sqlc = spark
