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

def handler_stop_signals(sig, frame):
  sys.exit("Got signal : " + str(sig))


signal.signal(signal.SIGINT, handler_stop_signals)

host = "127.0.0.1"
if len(sys.argv) >= 3:
  host = sys.argv[2]

_zcUserQueryNameSpace = {}
client = GatewayClient(address=host, port=int(sys.argv[1]))

gateway = JavaGateway(client)

intp = gateway.entry_point
intp.onPythonScriptInitialized(os.getpid())

java_import(gateway.jvm, "org.apache.zeppelin.display.Input")

from zeppelin_context import PyZeppelinContext

z = __zeppelin__ = PyZeppelinContext(intp.getZeppelinContext(), gateway)
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
