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
from py4j.protocol import Py4JJavaError, Py4JNetworkError
import warnings
import ast
import traceback
import warnings
import signal
import base64

from io import BytesIO
try:
    from StringIO import StringIO
except ImportError:
    from io import StringIO

# for back compatibility

class Logger(object):
  def __init__(self):
    pass

  def write(self, message):
    intp.appendOutput(message)

  def reset(self):
    pass

  def flush(self):
    pass


class PyZeppelinContext(object):
  """ A context impl that uses Py4j to communicate to JVM
  """

  def __init__(self, z):
    self.z = z
    self.paramOption = gateway.jvm.org.apache.zeppelin.display.ui.OptionInput.ParamOption
    self.javaList = gateway.jvm.java.util.ArrayList
    self.max_result = 1000
    self._displayhook = lambda *args: None
    self._setup_matplotlib()

  def getInterpreterContext(self):
    return self.z.getCurrentInterpreterContext()

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

  def show(self, p, **kwargs):
    if hasattr(p, '__name__') and p.__name__ == "matplotlib.pyplot":
      self.show_matplotlib(p, **kwargs)
    elif type(p).__name__ == "DataFrame": # does not play well with sub-classes
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

  def show_matplotlib(self, p, fmt="png", width="auto", height="auto",
                      **kwargs):
    """Matplotlib show function
    """
    if fmt == "png":
      img = BytesIO()
      p.savefig(img, format=fmt)
      img_str = b"data:image/png;base64,"
      img_str += base64.b64encode(img.getvalue().strip())
      img_tag = "<img src={img} style='width={width};height:{height}'>"
      # Decoding is necessary for Python 3 compability
      img_str = img_str.decode("ascii")
      img_str = img_tag.format(img=img_str, width=width, height=height)
    elif fmt == "svg":
      img = StringIO()
      p.savefig(img, format=fmt)
      img_str = img.getvalue()
    else:
      raise ValueError("fmt must be 'png' or 'svg'")

    html = "%html <div style='width:{width};height:{height}'>{img}<div>"
    print(html.format(width=width, height=height, img=img_str))
    img.close()

  def configure_mpl(self, **kwargs):
    import mpl_config
    mpl_config.configure(**kwargs)

  def _setup_matplotlib(self):
    # If we don't have matplotlib installed don't bother continuing
    try:
      import matplotlib
    except ImportError:
      return
    # Make sure custom backends are available in the PYTHONPATH
    rootdir = os.environ.get('ZEPPELIN_HOME', os.getcwd())
    mpl_path = os.path.join(rootdir, 'interpreter', 'lib', 'python')
    if mpl_path not in sys.path:
      sys.path.append(mpl_path)

    # Finally check if backend exists, and if so configure as appropriate
    try:
      matplotlib.use('module://backend_zinline')
      import backend_zinline

      # Everything looks good so make config assuming that we are using
      # an inline backend
      self._displayhook = backend_zinline.displayhook
      self.configure_mpl(width=600, height=400, dpi=72,
                         fontsize=10, interactive=True, format='png')
    except ImportError:
      # Fall back to Agg if no custom backend installed
      matplotlib.use('Agg')
      warnings.warn("Unable to load inline matplotlib backend, "
                    "falling back to Agg")


def handler_stop_signals(sig, frame):
  sys.exit("Got signal : " + str(sig))


signal.signal(signal.SIGINT, handler_stop_signals)

host = "127.0.0.1"
if len(sys.argv) >= 3:
  host = sys.argv[2]

_zcUserQueryNameSpace = {}
client = GatewayClient(address=host, port=int(sys.argv[1]))

#gateway = JavaGateway(client, auto_convert = True)
gateway = JavaGateway(client)

intp = gateway.entry_point
intp.onPythonScriptInitialized(os.getpid())

java_import(gateway.jvm, "org.apache.zeppelin.display.Input")
z = __zeppelin__ = PyZeppelinContext(intp)
__zeppelin__._setup_matplotlib()

_zcUserQueryNameSpace["__zeppelin__"] = __zeppelin__
_zcUserQueryNameSpace["z"] = z

output = Logger()
sys.stdout = output
#sys.stderr = output

while True :
  req = intp.getStatements()
  if req == None:
    break

  try:
    stmts = req.statements().split("\n")
    final_code = []

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
    for hook in (global_hook, user_hook):
      if hook:
        nhooks += 1

    for s in stmts:
      if s == None:
        continue

      # skip comment
      s_stripped = s.strip()
      if len(s_stripped) == 0 or s_stripped.startswith("#"):
        continue

      final_code.append(s)

    if final_code:
      # use exec mode to compile the statements except the last statement,
      # so that the last statement's evaluation will be printed to stdout
      code = compile('\n'.join(final_code), '<stdin>', 'exec', ast.PyCF_ONLY_AST, 1)

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
      except:
        raise Exception(traceback.format_exc())

    intp.setStatementsFinished("", False)
  except Py4JJavaError:
    excInnerError = traceback.format_exc() # format_tb() does not return the inner exception
    innerErrorStart = excInnerError.find("Py4JJavaError:")
    if innerErrorStart > -1:
       excInnerError = excInnerError[innerErrorStart:]
    intp.setStatementsFinished(excInnerError + str(sys.exc_info()), True)
  except Py4JNetworkError:
    # lost connection from gateway server. exit
    sys.exit(1)
  except:
    intp.setStatementsFinished(traceback.format_exc(), True)

  output.reset()
