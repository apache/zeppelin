#!/usr/bin/env python
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

"""Python interpreter exposed though gRPC server"""

import sys
import time
import argparse
import traceback


from cStringIO import StringIO #TODO python3 compatibility! i.e `import io`

import python_interpreter_pb2

_ONE_DAY_IN_SECONDS = 60 * 60 * 24
_NOTE_GLOBALS = "{}_globals"

def help():
    print("""%html
 <h2>Python Interpreter help</h2>
 <h3>Python 2 & 3 compatibility</h3>
 <p>The interpreter is compatible with Python 2 & 3.<br/>
 To change Python version, 
 change in the interpreter configuration the python to the 
 desired version (example : python=/usr/bin/python3)</p>
 <h3>Python modules</h3>
 <p>The interpreter can use all modules already installed 
 (with pip, easy_install, etc)</p>

 <h3>Forms</h3>
 You must install py4j in order to use
 the form feature (pip install py4j)

 <h4>Input form</h4>
 <pre>print (z.input("f1","defaultValue"))</pre>
 <h4>Selection form</h4>
 <pre>print(z.select("f2", [("o1","1"), ("o2","2")],2))</pre>
 <h4>Checkbox form</h4>
 <pre> print("".join(z.checkbox("f3", [("o1","1"), ("o2","2")],["1"])))</pre>')

 <h3>Matplotlib graph</h3>
 <div>The interpreter can display matplotlib graph with 
 the function z.show()</div>
 <div> You need to already have matplotlib module installed 
 to use this functionality !</div><br/>
 <pre>import matplotlib.pyplot as plt
 plt.figure()
 (.. ..)
 z.show(plt)
 plt.close()
 </pre>
 <div><br/> z.show function can take optional parameters
 to adapt graph width and height</div>
 <div><b>example </b>:
 <pre>z.show(plt,width='50px
 z.show(plt,height='150px') </pre></div>

 <h3>Pandas DataFrame</h3>
 <div> You need to have Pandas module installed
 to use this functionality (pip install pandas) !</div><br/>
 <div>The interpreter can visualize Pandas DataFrame
 with the function z.show()
 <pre>
 import pandas as pd
 df = pd.read_csv("bank.csv", sep=";")
 z.show(df)
 </pre></div>
 
 <h3>SQL over Pandas DataFrame</h3>
 <div> You need to have Pandas&Pandasql modules installed 
 to use this functionality (pip install pandas pandasql) !</div><br/>

 <div>Python interpreter group includes %sql interpreter that can query
 Pandas DataFrames using SQL and visualize results using Zeppelin Table Display System
 
 <pre>
 %python
 import pandas as pd
 df = pd.read_csv("bank.csv", sep=";")
 </pre>
 <br />
 <pre>
 %python.sql
 %sql
 SELECT * from df LIMIT 5
 </pre>
 </div>
    """)

def _check_port_range(value):
  """Checks portnumber to be [0, 65536]"""
  ival = int(value)
  if ival < 0 or ival > 65536:
    raise argparse.ArgumentTypeError(
        "{} is an invalid port number. Use 0-65536".format(value))
  return ival

class InterpreterError(Exception):
  def __init__(self, error_class, line_number, details):
    self.error_class = error_class
    self.line_number = line_number
    self.details = details

class PythonInterpreterServicer(python_interpreter_pb2.BetaPythonInterpreterServicer):
  """Implementing the servicer interface generated from our service definition 
     with functions that perform the actual "work" of the service.
  """

  def __init__(self):
    pass

  def do_exec(self, code, note_id):
    """Executes give python string in same environment

    Uses exec() to run the code. In order to report the
    results\output, temroray replaces stdout.

    Args:
      code: string of Python code

    Returns:
        String that is stdout, a side-effect for the executed code

    Rises:
        InterpreterError: an error with specific line number
    """
    old_stdout = sys.stdout
    #TODO python3 compatibility! i.e io.BytesIO()
    redirected_output = sys.stdout = StringIO()
    try:
      #compile(code)?
      gdict = _get_globals_or_default(note_id)
      exec(code, gdict, gdict)
      globals()[_NOTE_GLOBALS.format(note_id)] = gdict
      #execfile()?
      sys.stdout = old_stdout
    except SyntaxError as err:
      sys.stdout = old_stdout
      error_class = err.__class__.__name__
      details = err.args[0]
      line_number = err.lineno
    except Exception as err:
      sys.stdout = old_stdout
      error_class = err.__class__.__name__
      details = err.args[0]
      cl, exc, tb = sys.exc_info()
      line_number = traceback.extract_tb(tb)[-1][1]
    else:
      return redirected_output
    print("{} at line {}: {}".format(error_class, line_number, details))
    raise InterpreterError(error_class, line_number, details)

  def Interprete(self, request, context): #CodeInterpreteRequest
    print("Got \n```\n{}\n```\nfrom noteID '{}'".format(request.code, request.noteId))
    out = ""
    try:
      #_debug_print_note_locals_globals("Before", request.noteId)
      out = self.do_exec(request.code, request.noteId);
      #_debug_print_note_locals_globals("After", request.noteId)
    except InterpreterError as e:
      out = "{} at line {}:\n{}".format(e.error_class, e.line_number, e.details)
      return python_interpreter_pb2.InterpetedResult(output=out, status="fail")
    print("Success!")
    output = out.getvalue()
    out.flush()
    return python_interpreter_pb2.InterpetedResult(output=output, status="success")

  def Shutdown(self, void, context):
    print("Shuting down Python process")
    #TODO(bzz): make main exit i.e \w signal.SIGINT
    #return python_interpreter_pb2.Void()

def _get_globals_or_default(note_id, default={}):
  key = _NOTE_GLOBALS.format(note_id)
  return globals()[key] if key in globals() else default

def _debug_print_note_locals_globals(when, note_id):
  print("{} globals() {}: {}".format(note_id, when, _get_globals_or_default(note_id)))


def main():
  parser = argparse.ArgumentParser(
      description='Expose python interpreter as gRPC service.')
  parser.add_argument('port', type=_check_port_range,
      help='Port number to run the sGRC server')

  args = parser.parse_args()
  print("Starting gRPC server on {}".format(args.port))

  # Run a gRPC server to listen for requests from clients and transmit responses
  server = python_interpreter_pb2.beta_create_PythonInterpreter_server(PythonInterpreterServicer())
  server.add_insecure_port('[::]:{}'.format(args.port))
  server.start()
  try:
    while True:
      time.sleep(_ONE_DAY_IN_SECONDS)
  except KeyboardInterrupt:
    server.stop(0)


if __name__ == '__main__':
    main()




"""
def serve(port, sleep_time):
  server = python_interpreter_pb2.beta_create_PythonInterpreter_server(PythonInterpreterServicer())
  server.add_insecure_port('[::]:{}'.format(port))
  server.start()
  try:
    while True:
      time.sleep(sleep_time)
  except KeyboardInterrupt:
    server.stop(0)
"""
