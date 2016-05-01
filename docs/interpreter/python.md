---
layout: page
title: "Python Interpreter"
description: "Python Interpreter"
group: manual
---
{% include JB/setup %}

## Python 2 & 3 Interpreter for Apache Zeppelin

## Configuration
<table class="table-configuration">
  <tr>
    <th>Property</th>
    <th>Default</th>
    <th>Description</th>
  </tr>
  <tr>
    <td>python.path</td>
    <td>/usr/bin/python</td>
    <td>Path of the already installed Python binary (could be python2 or python3)</td>
  </tr>
</table>

## Enabling Python Interpreter

In a notebook, to enable the **Python** interpreter, click on the **Gear** icon and select **Python**

## Using the Python Interpreter

In a paragraph, use **_%python_** to select the **Python** interpreter and then input all commands.

The interpreter can only work if you already have python installed (the interpreter doesn't bring it own python binaries).

To access the help, type **help()**

## Python modules
The interpreter can use all modules already installed (with pip, easy_install...)

## Apply Zeppelin Dynamic Forms
You can leverage [Zeppelin Dynamic Form]({{BASE_PATH}}/manual/dynamicform.html) inside your Python code.
Example : 
```bash
%python
# Input fom
print (z.input("f1","defaultValue"))

# Select fom
print (z.select("f1",[("o1","1"),("o2","2")],"2"))

#Checkbox form
print("".join(z.checkbox("f3", [("o1","1"), ("o2","2")],["1"])))
```

## Matplotlib integration
 The python interpreter can display matplotlib graph with the function **_zeppelin_show()_**
 You need to already have matplotlib module installed to use this functionality !
 ```bash
%python
import matplotlib.pyplot as plt
plt.figure()
(.. ..)
zeppelin_show(plt)
plt.close()
```
zeppelin_show function can take optional parameters to adapt graph width and height
 ```bash
%python
zeppelin_show(plt,width='50px')
zeppelin_show(plt,height='150px')
```

[![pythonmatplotlib](/docs/interpreter/screenshots/pythonMatplotlib.png)](/docs/interpreter/screenshots/pythonMatplotlib.png)

### Use matplotlib without XServer

Matplotlib needs a X server to plot graph.

If you don't have any XServer and you don't want to install one (typically, a server environment) , you can use [Xvfb](http://www.x.org/archive/X11R7.6/doc/man/man1/Xvfb.1.xhtml) to simulate it. 

Althought Xvfb doesn't display anything, you will have any issue to use the python interpreter, matplotlib and Xvfb. The interpreter plot the graph on X and then extracts svg path (in strings) to send it to the zeppelin webapp.