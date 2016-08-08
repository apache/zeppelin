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

# PYTHON 2 / 3 compatibility :
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


class PyZeppelinContext(object):
    """ If py4j is detected, these class will be override
        with the implementation in bootstrap_input.py
    """
    errorMsg = "You must install py4j Python module " \
               "(pip install py4j) to use Zeppelin dynamic forms features"
    
    def __init__(self):
        self.max_result = 1000
    
    def input(self, name, defaultValue=""):
        print(self.errorMsg)
    
    def select(self, name, options, defaultValue=""):
        print(self.errorMsg)
    
    def checkbox(self, name, options, defaultChecked=[]):
        print(self.errorMsg)
    
    def show(self, p, **kwargs):
        if hasattr(p, '__name__') and p.__name__ == "matplotlib.pyplot":
            self.show_matplotlib(p, **kwargs)
        elif type(p).__name__ == "DataFrame": # does not play well with sub-classes
            # `isinstance(p, DataFrame)` would req `import pandas.core.frame.DataFrame`
            # and so a dependency on pandas
            self.show_dataframe(p, **kwargs)
        elif hasattr(p, '__call__'):
            p() #error reporting
    
    def show_dataframe(self, df, **kwargs):
        """Pretty prints DF using Table Display System
        """
        limit = len(df) > self.max_result
        header_buf = io.StringIO("")
        header_buf.write(str(df.columns[0]))
        for col in df.columns[1:]:
            header_buf.write("\t")
            header_buf.write(str(col))
        header_buf.write("\n")
        
        body_buf = io.StringIO("")
        rows = df.head(self.max_result).values if limit else df.values
        for row in rows:
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
    
    def show_matplotlib(self, p, width="100%", height="100%", **kwargs):
        """Matplotlib show function
        """
        img = io.StringIO()
        p.savefig(img, format="svg")
        html = "%html <div style='width:{width};height:{height}'>{image}<div>"
        print(html.format(width=width, height=height, image=img.getvalue()))
        img.close()


z = PyZeppelinContext()
