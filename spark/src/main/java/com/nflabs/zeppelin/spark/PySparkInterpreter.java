package com.nflabs.zeppelin.spark;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.ServerSocket;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.compress.utils.IOUtils;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteException;
import org.apache.commons.exec.ExecuteResultHandler;
import org.apache.commons.exec.ExecuteWatchdog;
import org.apache.commons.exec.PumpStreamHandler;
import org.apache.commons.exec.environment.EnvironmentUtils;
import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaSparkContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import py4j.GatewayServer;

import com.nflabs.zeppelin.interpreter.Interpreter;
import com.nflabs.zeppelin.interpreter.InterpreterContext;
import com.nflabs.zeppelin.interpreter.InterpreterException;
import com.nflabs.zeppelin.interpreter.InterpreterGroup;
import com.nflabs.zeppelin.interpreter.InterpreterPropertyBuilder;
import com.nflabs.zeppelin.interpreter.InterpreterResult;
import com.nflabs.zeppelin.interpreter.InterpreterResult.Code;
import com.nflabs.zeppelin.interpreter.LazyOpenInterpreter;
import com.nflabs.zeppelin.interpreter.WrappedInterpreter;

/**
 *
 */
public class PySparkInterpreter extends Interpreter implements ExecuteResultHandler {
  Logger logger = LoggerFactory.getLogger(PySparkInterpreter.class);
  private GatewayServer gatewayServer;
  private DefaultExecutor executor;
  private int port;
  private ByteArrayOutputStream outputStream;
  private ByteArrayOutputStream errStream;
  private BufferedWriter ins;
  private PipedInputStream in;
  private ByteArrayOutputStream input;
  private String scriptPath;
  boolean pythonscriptRunning = false;

  static {
    Interpreter.register(
        "pyspark",
        "spark",
        PySparkInterpreter.class.getName(),
        new InterpreterPropertyBuilder()
          .add("spark.home",
               SparkInterpreter.getSystemDefault("SPARK_HOME", "spark.home", ""),
               "Spark home path. Should be provided for pyspark").build());
  }

  public PySparkInterpreter(Properties property) {
    super(property);

    scriptPath = System.getProperty("java.io.tmpdir") + "/zeppelin_pyspark.py";
  }

  private String getSparkHome() {
    String sparkHome = getProperty("spark.home");
    if (sparkHome == null) {
      throw new InterpreterException("spark.home is undefined");
    } else {
      return sparkHome;
    }
  }


  private void createPythonScript() {
    ClassLoader classLoader = getClass().getClassLoader();
    File out = new File(scriptPath);

    if (out.exists() && out.isDirectory()) {
      throw new InterpreterException("Can't create python script " + out.getAbsolutePath());
    }

    try {
      FileOutputStream outStream = new FileOutputStream(out);
      IOUtils.copy(
          classLoader.getResourceAsStream("python/zeppelin_pyspark.py"),
          outStream);
      outStream.close();
    } catch (IOException e) {
      throw new InterpreterException(e);
    }

    logger.info("File {} created", scriptPath);
  }

  @Override
  public void open() {
    // create python script
    createPythonScript();

    port = findRandomOpenPortOnAllLocalInterfaces();

    gatewayServer = new GatewayServer(this, port);
    gatewayServer.start();

    // Run python shell
    CommandLine cmd = CommandLine.parse("python");
    cmd.addArgument(scriptPath, false);
    cmd.addArgument(Integer.toString(port), false);
    executor = new DefaultExecutor();
    outputStream = new ByteArrayOutputStream();
    PipedOutputStream ps = new PipedOutputStream();
    in = null;
    try {
      in = new PipedInputStream(ps);
    } catch (IOException e1) {
      throw new InterpreterException(e1);
    }
    ins = new BufferedWriter(new OutputStreamWriter(ps));

    input = new ByteArrayOutputStream();

    PumpStreamHandler streamHandler = new PumpStreamHandler(outputStream, outputStream, in);
    executor.setStreamHandler(streamHandler);
    executor.setWatchdog(new ExecuteWatchdog(ExecuteWatchdog.INFINITE_TIMEOUT));


    try {
      Map env = EnvironmentUtils.getProcEnvironment();

      String pythonPath = (String) env.get("PYTHONPATH");
      if (pythonPath == null) {
        pythonPath = "";
      } else {
        pythonPath += ":";
      }

      pythonPath += getSparkHome() + "/python/lib/py4j-0.8.2.1-src.zip:"
          + getSparkHome() + "/python";

//      Map<String, String> newEnv = new HashMap<String, String>(env);
//      newEnv.put("PYTHONPATH", pythonPath);

      env.put("PYTHONPATH", pythonPath);
      //EnvironmentUtils.addVariableToEnvironment(env, "PYTHONPATH="+pythonPath);

      executor.execute(cmd, env, this);
      pythonscriptRunning = true;
    } catch (IOException e) {
      throw new InterpreterException(e);
    }


    try {
      input.write("import sys, getopt\n".getBytes());
      ins.flush();
    } catch (IOException e) {
      throw new InterpreterException(e);
    }
  }

  private int findRandomOpenPortOnAllLocalInterfaces() {
    int port;
    try (ServerSocket socket = new ServerSocket(0);) {
      port = socket.getLocalPort();
      socket.close();
    } catch (IOException e) {
      throw new InterpreterException(e);
    }
    return port;
  }

  @Override
  public void close() {
    executor.getWatchdog().destroyProcess();
    gatewayServer.shutdown();
  }

  private String _statements;

  Integer statementSetNotifier = new Integer(0);

  public String getStatements() {
    synchronized (statementSetNotifier) {
      while (_statements == null) {
        try {
          logger.info("wait for statements");
          statementSetNotifier.wait(1000);
        } catch (InterruptedException e) {
        }
      }
      String st = _statements;
      _statements = null;
      return st;
    }
  }

  String statementOutput = null;
  boolean statementError = false;
  Integer statementFinishedNotifier = new Integer(0);

  public void setStatementsFinished(String out, boolean error) {
    synchronized (statementFinishedNotifier) {
      statementOutput = out;
      statementError = error;
      statementFinishedNotifier.notify();
    }

  }

  @Override
  public InterpreterResult interpret(String st, InterpreterContext context) {
    if (!pythonscriptRunning) {
      return new InterpreterResult(Code.ERROR, "python process not running");
    }

    _statements = st;
    statementOutput = null;

    synchronized (statementSetNotifier) {
      statementSetNotifier.notify();
    }

    synchronized (statementFinishedNotifier) {
      while (statementOutput == null) {
        try {
          logger.info("wait for output");
          statementFinishedNotifier.wait(1000);
        } catch (InterruptedException e) {
        }
      }
    }
    //System.out.println("from python = "+statementFinished);
    if (statementError) {
      return new InterpreterResult(Code.ERROR, statementOutput);
    } else {
      return new InterpreterResult(Code.SUCCESS, statementOutput);
    }

    /*
    //outputStream.reset();
    try {
      System.out.println("> is="+in.available()+", "+outputStream.size());
      input.write((st + "\n").getBytes());
      input.flush();
      System.out.println("- is="+in.available()+", "+outputStream.size());
    } catch (IOException e) {
      throw new InterpreterException(e);
    }
    try {
      Thread.sleep(1000);
    } catch (InterruptedException e) {

    }
    try {
      System.out.println("< is="+in.available()+", "+outputStream.size());
    } catch (IOException e) {
    }

    outputStream.size();
    String result = outputStream.toString();
    System.out.println("Result = " + result);
    logger.info("pyspark result " + result);
    return new InterpreterResult(Code.SUCCESS, result);
    */
  }

  @Override
  public void cancel(InterpreterContext context) {
    return;
  }

  @Override
  public FormType getFormType() {
    return FormType.NATIVE;
  }

  @Override
  public int getProgress(InterpreterContext context) {
    return 0;
  }

  @Override
  public List<String> completion(String buf, int cursor) {
    return null;
  }

  private SparkInterpreter getSparkInterpreter() {
    InterpreterGroup intpGroup = getInterpreterGroup();
    synchronized (intpGroup) {
      for (Interpreter intp : getInterpreterGroup()){
        if (intp.getClassName().equals(SparkInterpreter.class.getName())) {
          Interpreter p = intp;
          while (p instanceof WrappedInterpreter) {
            if (p instanceof LazyOpenInterpreter) {
              ((LazyOpenInterpreter) p).open();
            }
            p = ((WrappedInterpreter) p).getInnerInterpreter();
          }
          return (SparkInterpreter) p;
        }
      }
    }
    return null;
  }

  public JavaSparkContext getJavaSparkContext() {
    SparkInterpreter intp = getSparkInterpreter();
    if (intp == null) {
      return null;
    } else {
      return new JavaSparkContext(intp.getSparkContext());
    }
  }

  public SparkConf getSparkConf() {
    JavaSparkContext sc = getJavaSparkContext();
    if (sc == null) {
      return null;
    } else {
      return getJavaSparkContext().getConf();
    }
  }


  @Override
  public void onProcessComplete(int exitValue) {
    pythonscriptRunning = false;
    logger.info("python process terminated. exit code " + exitValue);
  }

  @Override
  public void onProcessFailed(ExecuteException e) {
    pythonscriptRunning = false;
    logger.error("python process failed", e);
  }
}
