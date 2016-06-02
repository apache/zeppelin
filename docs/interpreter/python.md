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
    <td>python</td>
    <td>python</td>
    <td>Path of the already installed Python binary (could be python2 or python3).
    If python is not in your $PATH you can set the absolute directory (example : /usr/bin/python)
    </td>
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

**Zeppelin Dynamic Form can only be used if py4j Python library is installed in your system. If not, you can install it with `pip install py4j`.**

Example : 

```python
%python
### Input form
print (z.input("f1","defaultValue"))

### Select form
print (z.select("f1",[("o1","1"),("o2","2")],"2"))

### Checkbox form
print("".join(z.checkbox("f3", [("o1","1"), ("o2","2")],["1"])))
```




## Zeppelin features not fully supported by the Python Interpreter

* Interrupt a paragraph execution (`cancel()` method) is currently only supported in Linux and MacOs. If interpreter runs in another operating system (for instance MS Windows) , interrupt a paragraph will close the whole interpreter. A JIRA ticket ([ZEPPELIN-893](https://issues.apache.org/jira/browse/ZEPPELIN-893)) is opened to implement this feature in a next release of the interpreter.
* Progression bar in webUI  (`getProgress()` method) is currently not implemented.
* Code-completion is currently not implemented.

## Matplotlib integration
 The python interpreter can display matplotlib graph with the function **_zeppelin_show()_**
 You need to already have matplotlib module installed  and a running XServer to use this functionality !
 
 ```python
%python
import matplotlib.pyplot as plt
plt.figure()
(.. ..)
zeppelin_show(plt)
plt.close()
```
zeppelin_show function can take optional parameters to adapt graph width and height

 ```python
%python
zeppelin_show(plt,width='50px')
zeppelin_show(plt,height='150px')
```

[![pythonmatplotlib](../interpreter/screenshots/pythonMatplotlib.png)](/docs/interpreter/screenshots/pythonMatplotlib.png)


## Technical description - Interpreter architecture

### Dev prerequisites

* Python 2 and 3 installed with py4j (0.9.2) and matplotlib (1.31 or later) installed on each

* Tests only checks the interpreter logic and starts any Python process ! Python process is mocked with a class that simply output it input.

* Make sure the code wrote in bootstrap.py and bootstrap_input.py is Python2 and 3 compliant.

* Use PEP8 convention for python code.

### Technical overview

 * When interpreter is starting it launches a python process inside a Java ProcessBuilder. Python is started with -i (interactive mode) and -u (unbuffered stdin, stdout and stderr) options. Thus the interpreter has a "sleeping" python process.

 * Interpreter sends command to python with a Java `outputStreamWiter` and read from an `InputStreamReader`. To know when stop reading stdout, interpreter sends `print "*!?flush reader!?*"`after each command and reads stdout until he receives back the `*!?flush reader!?*`.

 * When interpreter is starting, it sends some Python code (bootstrap.py and bootstrap_input.py) to initialize default behavior and functions (`help(), z.input()...`). bootstrap_input.py is sent only if py4j library is detected inside Python process.

 * [Py4J](https://www.py4j.org/) python and java libraries is used to load Input zeppelin Java class into the python process (make java code with python code !). Therefore the interpreter can directly create Zeppelin input form inside the Python process (and eventually with some python variable already defined). JVM opens a random open port to be accessible from python process.

 * JavaBuilder can't send SIGINT signal to interrupt paragraph execution. Therefore interpreter directly  send a `kill SIGINT PID` to python process to interrupt execution. Python process catch SIGINT signal with some code defined in bootstrap.py

 * Matplotlib display feature is made with SVG export (in string) and then displays it with html code.
