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

from io import BytesIO
try:
  from StringIO import StringIO
except ImportError:
  from io import StringIO

class PyZeppelinContext(object):
  """ A context impl that uses Py4j to communicate to JVM
  """

  def __init__(self, z):
    self.z = z
    self.paramOption = gateway.jvm.org.apache.zeppelin.display.ui.OptionInput.ParamOption
    self.javaList = gateway.jvm.java.util.ArrayList
    self.max_result = z.getMaxResult()

  def input(self, name, defaultValue=""):
    return self.z.input(name, defaultValue)

  def textbox(self, name, defaultValue=""):
    return self.z.textbox(name, defaultValue)

  def noteTextbox(self, name, defaultValue=""):
    return self.z.noteTextbox(name, defaultValue)

  def select(self, name, options, defaultValue=""):
    return self.z.select(name, defaultValue, self.getParamOptions(options))

  def noteSelect(self, name, options, defaultValue=""):
    return self.z.noteSelect(name, defaultValue, self.getParamOptions(options))

  def checkbox(self, name, options, defaultChecked=[]):
    return self.z.checkbox(name, self.getDefaultChecked(defaultChecked), self.getParamOptions(options))

  def noteCheckbox(self, name, options, defaultChecked=[]):
    return self.z.noteCheckbox(name, self.getDefaultChecked(defaultChecked), self.getParamOptions(options))

  def getParamOptions(self, options):
    javaOptions = gateway.new_array(self.paramOption, len(options))
    i = 0
    for tuple in options:
      javaOptions[i] = self.paramOption(tuple[0], tuple[1])
      i += 1
    return javaOptions

  def getDefaultChecked(self, defaultChecked):
    javaDefaultChecked = self.javaList()
    for check in defaultChecked:
      javaDefaultChecked.append(check)
    return javaDefaultChecked

  def show(self, p, **kwargs):
    if type(p).__name__ == "DataFrame": # does not play well with sub-classes
      # `isinstance(p, DataFrame)` would req `import pandas.core.frame.DataFrame`
      # and so a dependency on pandas
      self.show_dataframe(p, **kwargs)
    elif hasattr(p, '__call__'):
      p() #error reporting

  def show_dataframe(self, df, show_index=False, **kwargs):
    """Pretty prints DF using Table Display System
    """
    limit = len(df) > self.max_result
    header_buf = StringIO("")
    if show_index:
      idx_name = str(df.index.name) if df.index.name is not None else ""
      header_buf.write(idx_name + "\t")
    header_buf.write(str(df.columns[0]))
    for col in df.columns[1:]:
      header_buf.write("\t")
      header_buf.write(str(col))
    header_buf.write("\n")

    body_buf = StringIO("")
    rows = df.head(self.max_result).values if limit else df.values
    index = df.index.values
    for idx, row in zip(index, rows):
      if show_index:
        body_buf.write("%html <strong>{}</strong>".format(idx))
        body_buf.write("\t")
      body_buf.write(str(row[0]))
      for cell in row[1:]:
        body_buf.write("\t")
        body_buf.write(str(cell))
      body_buf.write("\n")
    body_buf.seek(0); header_buf.seek(0)
    #TODO(bzz): fix it, so it shows red notice, as in Spark
    print("%table " + header_buf.read() + body_buf.read()) # +
    #      ("\n<font color=red>Results are limited by {}.</font>" \
    #          .format(self.max_result) if limit else "")
    #)
    body_buf.close(); header_buf.close()


# start JVM gateway
client = GatewayClient(address='127.0.0.1', port=${JVM_GATEWAY_PORT})
gateway = JavaGateway(client)
java_import(gateway.jvm, "org.apache.zeppelin.display.Input")
intp = gateway.entry_point
z = __zeppelin__ = PyZeppelinContext(intp.getZeppelinContext())

