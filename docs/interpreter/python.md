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
 The python interpreter can display matplotlib graph with the function `zeppelin_show()`.
 You need to have matplotlib module installed and a XServer running to use this functionality !
 
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


## Technical description

For in-depth technical details on current implementation plese reffer [python/README.md](https://github.com/apache/zeppelin/blob/master/python/README.md)
