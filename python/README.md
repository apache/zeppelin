# Overview
Python interpreter for Apache Zeppelin

# Architecture
Current interpreter implementation spawns new system python process through `ProcessBuilder` and re-directs it's stdin\strout to Zeppelin

# Details

 - **UnitTests**

  To run full suit of tests, including ones that depend on real Python interpreter AND external libraries installed (like Pandas, Pandasql, etc) do

  ```
mvn -Dpython.test.exclude='' test -pl python -am
  ```

 - **Py4j support**

  [Py4j](https://www.py4j.org/) enables Python programs to dynamically access Java objects in a JVM.
  It is required in order to use Zeppelin [dynamic forms](http://zeppelin.apache.org/docs/0.6.0-SNAPSHOT/manual/dynamicform.html) feature.

 - bootstrap process

  Interpreter environment is setup with thex [bootstrap.py](https://github.com/apache/zeppelin/blob/master/python/src/main/resources/bootstrap.py)
  It defines `help()` and `z` convenience functions


### Dev prerequisites

 * Python 2 or 3 installed with py4j (0.9.2) and matplotlib (1.31 or later) installed on each

 * Tests only checks the interpreter logic and starts any Python process! Python process is mocked with a class that simply output it input.

 * Code wrote in `bootstrap.py` and `bootstrap_input.py` should always be Python 2 and 3 compliant.

* Use PEP8 convention for python code.

### Technical overview

 * When interpreter is starting it launches a python process inside a Java ProcessBuilder. Python is started with -i (interactive mode) and -u (unbuffered stdin, stdout and stderr) options. Thus the interpreter has a "sleeping" python process.

 * Interpreter sends command to python with a Java `outputStreamWiter` and read from an `InputStreamReader`. To know when stop reading stdout, interpreter sends `print "*!?flush reader!?*"`after each command and reads stdout until he receives back the `*!?flush reader!?*`.

 * When interpreter is starting, it sends some Python code (bootstrap.py and bootstrap_input.py) to initialize default behavior and functions (`help(), z.input()...`). bootstrap_input.py is sent only if py4j library is detected inside Python process.

 * [Py4J](https://www.py4j.org/) python and java libraries is used to load Input zeppelin Java class into the python process (make java code with python code !). Therefore the interpreter can directly create Zeppelin input form inside the Python process (and eventually with some python variable already defined). JVM opens a random open port to be accessible from python process.

 * JavaBuilder can't send SIGINT signal to interrupt paragraph execution. Therefore interpreter directly  send a `kill SIGINT PID` to python process to interrupt execution. Python process catch SIGINT signal with some code defined in bootstrap.py

 * Matplotlib figures are displayed inline with the notebook automatically using a built-in backend for zeppelin in conjunction with a post-execute hook.

 * `%python.sql` support for Pandas DataFrames is optional and provided using https://github.com/yhat/pandasql if user have one installed


# IPython Overview
IPython interpreter for Apache Zeppelin

# IPython Requirements
You need to install the following python packages to make the IPython interpreter work.
 * jupyter 5.x
 * IPython
 * ipykernel
 * grpcio
 
If you have installed anaconda, then you just need to install grpc.

# IPython Architecture
Current interpreter delegate the whole work to ipython kernel via `jupyter_client`. Zeppelin would launch a python process which host the ipython kernel.
Zeppelin interpreter process will communicate with the python process via `grpc`. Ideally every feature works in IPython should work in Zeppelin as well.


