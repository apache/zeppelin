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

import os, sys, traceback, json, re

from py4j.java_gateway import java_import, JavaGateway, GatewayClient, CallbackServerParameters
from py4j.protocol import Py4JJavaError

import ast

class Logger(object):
  def __init__(self):
    pass

  def write(self, message):
    intp.appendOutput(message)

  def reset(self):
    pass

  def flush(self):
    pass


class PythonCompletion:
  def __init__(self, interpreter, userNameSpace):
    self.interpreter = interpreter
    self.userNameSpace = userNameSpace

  def getObjectCompletion(self, text_value):
    completions = [completion for completion in list(self.userNameSpace.keys()) if completion.startswith(text_value)]
    builtinCompletions = [completion for completion in dir(__builtins__) if completion.startswith(text_value)]
    return completions + builtinCompletions

  def getMethodCompletion(self, objName, methodName):
    execResult = locals()
    try:
      exec("{} = dir({})".format("objectDefList", objName), _zcUserQueryNameSpace, execResult)
    except:
      self.interpreter.logPythonOutput("Fail to run dir on " + objName)
      self.interpreter.logPythonOutput(traceback.format_exc())
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
      completionList = self.getObjectCompletion(objName)
    else:
      objName = text_value[:dotPos]
      methodName = text_value[dotPos + 1:]
      completionList = self.getMethodCompletion(objName, methodName)

    if completionList is None or len(completionList) <= 0:
      self.interpreter.setStatementsFinished("", False)
    else:
      result = json.dumps(list(filter(lambda x : not re.match("^__.*", x), list(completionList))))
      self.interpreter.setStatementsFinished(result, False)

host = sys.argv[1]
port = int(sys.argv[2])

if "PY4J_GATEWAY_SECRET" in os.environ:
  from py4j.java_gateway import GatewayParameters
  gateway_secret = os.environ["PY4J_GATEWAY_SECRET"]
  gateway = JavaGateway(gateway_parameters=GatewayParameters(
    address=host, port=port, auth_token=gateway_secret, auto_convert=True),
    start_callback_server=True,
    callback_server_parameters=CallbackServerParameters())
else:
  gateway = JavaGateway(GatewayClient(address=host, port=port), auto_convert=True,
    start_callback_server=True,
    callback_server_parameters=CallbackServerParameters())

intp = gateway.entry_point
_zcUserQueryNameSpace = {}

completion = PythonCompletion(intp, _zcUserQueryNameSpace)
_zcUserQueryNameSpace["__zeppelin_completion__"] = completion
_zcUserQueryNameSpace["gateway"] = gateway

from zeppelin_context import PyZeppelinContext
if intp.getZeppelinContext():
  z = __zeppelin__ = PyZeppelinContext(intp.getZeppelinContext(), gateway)
  __zeppelin__._setup_matplotlib()
  _zcUserQueryNameSpace["z"] = z
  _zcUserQueryNameSpace["__zeppelin__"] = __zeppelin__

intp.onPythonScriptInitialized(os.getpid())
# redirect stdout/stderr to java side so that PythonInterpreter can capture the python execution result
output = Logger()
sys.stdout = output
sys.stderr = output

while True :
  req = intp.getStatements()
  try:
    stmts = req.statements().split("\n")
    isForCompletion = req.isForCompletion()

    # Get post-execute hooks
    try:
      if req.isCallHooks():
        global_hook = intp.getHook('post_exec_dev')
      else:
        global_hook = None
    except:
      global_hook = None

    try:
      if req.isCallHooks():
        user_hook = __zeppelin__.getHook('post_exec')
      else:
        user_hook = None
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
      code = compile('\n'.join(stmts), '<stdin>', 'exec', ast.PyCF_ONLY_AST, 1)
      to_run_hooks = []
      if (nhooks > 0):
        to_run_hooks = code.body[-nhooks:]
      to_run_exec, to_run_single = (code.body[:-(nhooks + 1)],
                                   [code.body[-(nhooks + 1)]] if len(code.body) > nhooks else [])
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
          # PythonCompletion.getCompletion
          intp.setStatementsFinished("", False)
      except Py4JJavaError:
        # raise it to outside try except
        raise
      except:
        if not isForCompletion:
          # extract which line incur error from error message. e.g.
          # Traceback (most recent call last):
          # File "<stdin>", line 1, in <module>
          # ZeroDivisionError: integer division or modulo by zero
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
