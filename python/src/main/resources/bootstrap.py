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

# PYTHON 2 / 3 comptability :
# bootstrap.py must be runnable with Python 2 or 3

# Remove interactive mode displayhook
import sys
import signal

try:
    import StringIO as io
except ImportError:
    import io as io

sys.displayhook = lambda x: None

def intHandler(signum, frame):  # Set the signal handler
    print ("Paragraph interrupted")
    raise KeyboardInterrupt()

signal.signal(signal.SIGINT, intHandler)

def help():
    print ('%html')
    print ('<h2>Python Interpreter help</h2>')
    print ('<h3>Python 2 & 3 comptability</h3>')
    print ('<p>The interpreter is compatible with Python 2 & 3.<br/>')
    print ('To change Python version, ')
    print ('change in the interpreter configuration the python to the ')
    print ('desired version (example : python=/usr/bin/python3)</p>')
    print ('<h3>Python modules</h3>')
    print ('<p>The interpreter can use all modules already installed ')
    print ('(with pip, easy_install, etc)</p>')
    print ('<h3>Forms</h3>')
    print ('You must install py4j in order to use '
           'the form feature (pip install py4j)')
    print ('<h4>Input form</h4>')
    print ('<pre>print (z.input("f1","defaultValue"))</pre>')
    print ('<h4>Selection form</h4>')
    print ('<pre>print(z.select("f2", [("o1","1"), ("o2","2")],2))</pre>')
    print ('<h4>Checkbox form</h4>')
    print ('<pre> print("".join(z.checkbox("f3", [("o1","1"), '
           '("o2","2")],["1"])))</pre>')
    print ('<h3>Matplotlib graph</h3>')
    print ('<div>The interpreter can display matplotlib graph with ')
    print ('the function zeppelin_show()</div>')
    print ('<div> You need to already have matplotlib module installed ')
    print ('to use this functionality !</div><br/>')
    print ('''<pre>import matplotlib.pyplot as plt
plt.figure()
(.. ..)
zeppelin_show(plt)
plt.close()
</pre>''')
    print ('<div><br/> zeppelin_show function can take optional parameters ')
    print ('to adapt graph width and height</div>')
    print ("<div><b>example </b>:")
    print('''<pre>zeppelin_show(plt,width='50px')
zeppelin_show(plt,height='150px') </pre></div>''')

# Matplotlib show function
def zeppelin_show(p, width="0", height="0"):
    img = io.StringIO()
    p.savefig(img, format='svg')
    img.seek(0)
    style = ""
    if(width != "0"):
        style += 'width:'+width
    if(height != "0"):
        if(len(style) != 0):
            style += ","
            style += 'height:'+height
    print("%html <div style='" + style + "'>" + img.read() + "<div>")

# If py4j is detected, these class will be override
# with the implementation in bootstrap_input.py
class PyZeppelinContext():
    errorMsg = "You must install py4j Python module " \
               "(pip install py4j) to use Zeppelin dynamic forms features"
    def __init__(self, zc):
        self.z = zc
    def input(self, name, defaultValue=""):
        print (self.errorMsg)
    def select(self, name, options, defaultValue=""):
        print (self.errorMsg)
    def checkbox(self, name, options, defaultChecked=[]):
        print (self.errorMsg)

z = PyZeppelinContext("")

