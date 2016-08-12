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

import time
import argparse

import python_interpreter_pb2

_ONE_DAY_IN_SECONDS = 60 * 60 * 24

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


class PythonInterpreterServicer(python_interpreter_pb2.BetaPythonInterpreterServicer):
  """Implementing the servicer interface generated from our service definition 
     with functions that perform the actual "work" of the service.
  """

  def __init__(self):
    pass

  def Interprete(self, code_interprete_request, context): #CodeInterpreteRequest
    print("Got \n```\n{}\n```\n to execute".format(code_interprete_request.code))
    time.sleep(5)
    print("Done!")
    return python_interpreter_pb2.InterpetedResult(output="Done!", status="success")


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
