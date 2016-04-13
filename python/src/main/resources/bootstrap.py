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

#PYTHON 2 / 3 comptability :
# bootstrap.py must be runnable with Python 2 and 3

#Remove interactive mode displayhook
import sys
sys.displayhook = lambda x: None


# Set the signal handler
import signal
def intHandler(signum, frame):
 print ("Paragraph interrupted")
 raise KeyboardInterrupt()

signal.signal(signal.SIGINT, intHandler)



def help():
 print ('%html')
 print ('<h2>Python Interpreter help</h2>')
 print ('<h3>Python 2 & 3 comptability</h3>')
 print ('<div>The interpreter is compatible with Python 2 & 3.<br/> To change Python version, change in the interpreter configuration the python.path to the desired version (example : python.path=/usr/bin/python3)</div>')
 print ('<h3>Python modules</h3>')
 print ('<div>The interpreter can use all modules already installed (with pip, easy_install etc)  </div>')
 print ('<h3>Forms</h3>')
 print ('<h4>Input form</h4>')
 print ('<pre> print "&#36{input_form(name)=defaultValue}"</pre>')
 print ('<h4>Selection form</h4>')
 print ('<pre> print "&#36{select_form(Selection Form)=op1,op1|op2(Option 2)|op3}"</pre>')
 print ('<h4>Checkbox form</h4>')
 print ('<pre> print "&#36{checkbox:checkbox_form=op1,op1|op2|op3}"</pre>')
 print ('<h3>Matplotlib graph</h3>')
 print ('<div>The interpreter can display matplotlib graph with the function zeppelin_show()</div>')
 print ('<div> You need to already have matplotlib module installed to use this functionality !</div><br/>')
 print ('''<pre>import matplotlib.pyplot as plt
plt.figure()
(.. ..)
zeppelin_show(plt)
plt.close()
</pre>''')
 print ('<div><br/> zeppelin_show function can take optional parameters to adapt graph width and height</div>')
 print ("<div><b>example </b>:")
 print('''<pre>zeppelin_show(plt,width='50px')
 zeppelin_show(plt,height='150px') </pre></div>''')


#Matplotlib show function
try:
 import StringIO as io
except ImportError:
 import io as io

def zeppelin_show(p,width="0",height="0"):
 img = io.StringIO()
 p.savefig(img, format='svg')
 img.seek(0)
 style=""
 if(width!="0"):
  style+='width:'+width
 if(height!="0"):
  if(len(style)!=0):
   style+=","
  style+='height:'+height
 print("%html <div style='"+ style +"'>" + img.read() +"<div>")

