/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.zeppelin.spark;

import java.io.File;
import java.io.PrintWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.common.base.Joiner;

import org.apache.spark.HttpServer;
import org.apache.spark.SparkConf;
import org.apache.spark.SparkContext;
import org.apache.spark.SparkEnv;
import org.apache.spark.repl.SparkCommandLine;
import org.apache.spark.repl.SparkILoop;
import org.apache.spark.repl.SparkIMain;
import org.apache.spark.repl.SparkJLineCompletion;
import org.apache.spark.scheduler.ActiveJob;
import org.apache.spark.scheduler.DAGScheduler;
import org.apache.spark.scheduler.Pool;
import org.apache.spark.sql.SQLContext;
import org.apache.spark.ui.jobs.JobProgressListener;
import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterPropertyBuilder;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResult.Code;
import org.apache.zeppelin.interpreter.InterpreterUtils;
import org.apache.zeppelin.interpreter.WrappedInterpreter;
import org.apache.zeppelin.scheduler.Scheduler;
import org.apache.zeppelin.scheduler.SchedulerFactory;
import org.apache.zeppelin.spark.dep.SparkDependencyContext;
import org.apache.zeppelin.spark.dep.SparkDependencyResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import scala.*;
import scala.Enumeration.Value;
import scala.collection.Iterator;
import scala.collection.JavaConversions;
import scala.collection.JavaConverters;
import scala.collection.Seq;
import scala.collection.mutable.HashMap;
import scala.collection.mutable.HashSet;
import scala.reflect.io.AbstractFile;
import scala.tools.nsc.Settings;
import scala.tools.nsc.interpreter.Completion.Candidates;
import scala.tools.nsc.interpreter.Completion.ScalaCompleter;
import scala.tools.nsc.settings.MutableSettings.BooleanSetting;
import scala.tools.nsc.settings.MutableSettings.PathSetting;

/**
 * Spark interpreter for Zeppelin.
 *
 */
public class SparkInterpreter extends Interpreter {
  public static Logger logger = LoggerFactory.getLogger(SparkInterpreter.class);

  static {
    Interpreter.register(
      "spark",
      "spark",
      SparkInterpreter.class.getName(),
      new InterpreterPropertyBuilder()
        .add("spark.app.name",
          getSystemDefault("SPARK_APP_NAME", "spark.app.name", "Zeppelin"),
          "The name of spark application.")
        .add("master",
          getSystemDefault("MASTER", "spark.master", "local[*]"),
          "Spark master uri. ex) spark://masterhost:7077")
        .add("spark.executor.memory",
          getSystemDefault(null, "spark.executor.memory", ""),
          "Executor memory per worker instance. ex) 512m, 32g")
        .add("spark.cores.max",
          getSystemDefault(null, "spark.cores.max", ""),
          "Total number of cores to use. Empty value uses all available core.")
        .add("zeppelin.spark.useHiveContext",
          getSystemDefault("ZEPPELIN_SPARK_USEHIVECONTEXT",
            "zeppelin.spark.useHiveContext", "true"),
          "Use HiveContext instead of SQLContext if it is true.")
        .add("zeppelin.spark.maxResult",
          getSystemDefault("ZEPPELIN_SPARK_MAXRESULT", "zeppelin.spark.maxResult", "1000"),
          "Max number of SparkSQL result to display.")
        .add("args", "", "spark commandline args")
        .add("zeppelin.spark.printREPLOutput", "true",
          "Print REPL output")
        .build()
    );
  }

  private ZeppelinContext z;
  private SparkILoop interpreter;
  private SparkIMain intp;
  private static SparkContext sc;
  private static SQLContext sqlc;
  private static SparkEnv env;
  private static JobProgressListener sparkListener;
  private static AbstractFile classOutputDir;
  private static Integer sharedInterpreterLock = new Integer(0);
  private static AtomicInteger numReferenceOfSparkContext = new AtomicInteger(0);

  private SparkOutputStream out;
  private SparkDependencyResolver dep;
  private SparkJLineCompletion completor;

  private Map<String, Object> binder;
  private SparkVersion sparkVersion;


  public SparkInterpreter(Properties property) {
    super(property);
    out = new SparkOutputStream();
  }

  public SparkInterpreter(Properties property, SparkContext sc) {
    this(property);

    this.sc = sc;
    env = SparkEnv.get();
    sparkListener = setupListeners(this.sc);
  }

  public SparkContext getSparkContext() {
    synchronized (sharedInterpreterLock) {
      if (sc == null) {
        sc = createSparkContext();
        env = SparkEnv.get();
        sparkListener = setupListeners(sc);
      }
      return sc;
    }
  }

  public boolean isSparkContextInitialized() {
    synchronized (sharedInterpreterLock) {
      return sc != null;
    }
  }

  static JobProgressListener setupListeners(SparkContext context) {
    JobProgressListener pl = new JobProgressListener(context.getConf());
    try {
      Object listenerBus = context.getClass().getMethod("listenerBus").invoke(context);

      Method[] methods = listenerBus.getClass().getMethods();
      Method addListenerMethod = null;
      for (Method m : methods) {
        if (!m.getName().equals("addListener")) {
          continue;
        }

        Class<?>[] parameterTypes = m.getParameterTypes();

        if (parameterTypes.length != 1) {
          continue;
        }

        if (!parameterTypes[0].isAssignableFrom(JobProgressListener.class)) {
          continue;
        }

        addListenerMethod = m;
        break;
      }

      if (addListenerMethod != null) {
        addListenerMethod.invoke(listenerBus, pl);
      } else {
        return null;
      }
    } catch (NoSuchMethodException | SecurityException | IllegalAccessException
        | IllegalArgumentException | InvocationTargetException e) {
      logger.error(e.toString(), e);
      return null;
    }
    return pl;
  }

  private boolean useHiveContext() {
    return java.lang.Boolean.parseBoolean(getProperty("zeppelin.spark.useHiveContext"));
  }

  public SQLContext getSQLContext() {
    synchronized (sharedInterpreterLock) {
      if (sqlc == null) {
        if (useHiveContext()) {
          String name = "org.apache.spark.sql.hive.HiveContext";
          Constructor<?> hc;
          try {
            hc = getClass().getClassLoader().loadClass(name)
                .getConstructor(SparkContext.class);
            sqlc = (SQLContext) hc.newInstance(getSparkContext());
          } catch (NoSuchMethodException | SecurityException
              | ClassNotFoundException | InstantiationException
              | IllegalAccessException | IllegalArgumentException
              | InvocationTargetException e) {
            logger.warn("Can't create HiveContext. Fallback to SQLContext", e);
            // when hive dependency is not loaded, it'll fail.
            // in this case SQLContext can be used.
            sqlc = new SQLContext(getSparkContext());
          }
        } else {
          sqlc = new SQLContext(getSparkContext());
        }
      }
      return sqlc;
    }
  }

  public SparkDependencyResolver getDependencyResolver() {
    if (dep == null) {
      dep = new SparkDependencyResolver(intp,
                                   sc,
                                   getProperty("zeppelin.dep.localrepo"),
                                   getProperty("zeppelin.dep.additionalRemoteRepository"));
    }
    return dep;
  }

  private DepInterpreter getDepInterpreter() {
    Interpreter p = getInterpreterInTheSameSessionByClassName(DepInterpreter.class.getName());
    if (p == null) {
      return null;
    }

    while (p instanceof WrappedInterpreter) {
      p = ((WrappedInterpreter) p).getInnerInterpreter();
    }
    return (DepInterpreter) p;
  }

  public SparkContext createSparkContext() {
    logger.info("------ Create new SparkContext {} -------", getProperty("master"));

    String execUri = System.getenv("SPARK_EXECUTOR_URI");
    String[] jars = SparkILoop.getAddedJars();

    String classServerUri = null;

    try { // in case of spark 1.1x, spark 1.2x
      Method classServer = interpreter.intp().getClass().getMethod("classServer");
      HttpServer httpServer = (HttpServer) classServer.invoke(interpreter.intp());
      classServerUri = httpServer.uri();
    } catch (NoSuchMethodException | SecurityException | IllegalAccessException
        | IllegalArgumentException | InvocationTargetException e) {
      // continue
    }

    if (classServerUri == null) {
      try { // for spark 1.3x
        Method classServer = interpreter.intp().getClass().getMethod("classServerUri");
        classServerUri = (String) classServer.invoke(interpreter.intp());
      } catch (NoSuchMethodException | SecurityException | IllegalAccessException
          | IllegalArgumentException | InvocationTargetException e) {
        // continue instead of: throw new InterpreterException(e);
        // Newer Spark versions (like the patched CDH5.7.0 one) don't contain this method
        logger.warn(String.format("Spark method classServerUri not available due to: [%s]", 
          e.getMessage()));
      }
    }

    SparkConf conf =
        new SparkConf()
            .setMaster(getProperty("master"))
            .setAppName(getProperty("spark.app.name"));

    if (classServerUri != null) {
      conf.set("spark.repl.class.uri", classServerUri);
    }

    if (jars.length > 0) {
      conf.setJars(jars);
    }

    if (execUri != null) {
      conf.set("spark.executor.uri", execUri);
    }
    if (System.getenv("SPARK_HOME") != null) {
      conf.setSparkHome(System.getenv("SPARK_HOME"));
    }
    conf.set("spark.scheduler.mode", "FAIR");

    Properties intpProperty = getProperty();

    for (Object k : intpProperty.keySet()) {
      String key = (String) k;
      String val = toString(intpProperty.get(key));
      if (!key.startsWith("spark.") || !val.trim().isEmpty()) {
        logger.debug(String.format("SparkConf: key = [%s], value = [%s]", key, val));
        conf.set(key, val);
      }
    }

    //TODO(jongyoul): Move these codes into PySparkInterpreter.java
    String pysparkBasePath = getSystemDefault("SPARK_HOME", null, null);
    File pysparkPath;
    if (null == pysparkBasePath) {
      pysparkBasePath = getSystemDefault("ZEPPELIN_HOME", "zeppelin.home", "../");
      pysparkPath = new File(pysparkBasePath,
          "interpreter" + File.separator + "spark" + File.separator + "pyspark");
    } else {
      pysparkPath = new File(pysparkBasePath,
          "python" + File.separator + "lib");
    }

    //Only one of py4j-0.9-src.zip and py4j-0.8.2.1-src.zip should exist
    String[] pythonLibs = new String[]{"pyspark.zip", "py4j-0.9-src.zip", "py4j-0.8.2.1-src.zip"};
    ArrayList<String> pythonLibUris = new ArrayList<>();
    for (String lib : pythonLibs) {
      File libFile = new File(pysparkPath, lib);
      if (libFile.exists()) {
        pythonLibUris.add(libFile.toURI().toString());
      }
    }
    pythonLibUris.trimToSize();

    // Distribute two libraries(pyspark.zip and py4j-*.zip) to workers
    // when spark version is less than or equal to 1.4.1
    if (pythonLibUris.size() == 2) {
      try {
        String confValue = conf.get("spark.yarn.dist.files");
        conf.set("spark.yarn.dist.files", confValue + "," + Joiner.on(",").join(pythonLibUris));
      } catch (NoSuchElementException e) {
        conf.set("spark.yarn.dist.files", Joiner.on(",").join(pythonLibUris));
      }
      if (!useSparkSubmit()) {
        conf.set("spark.files", conf.get("spark.yarn.dist.files"));
      }
      conf.set("spark.submit.pyArchives", Joiner.on(":").join(pythonLibs));
    }

    // Distributes needed libraries to workers
    // when spark version is greater than or equal to 1.5.0
    if (getProperty("master").equals("yarn-client")) {
      conf.set("spark.yarn.isPython", "true");
    }

    SparkContext sparkContext = new SparkContext(conf);
    return sparkContext;
  }

  static final String toString(Object o) {
    return (o instanceof String) ? (String) o : "";
  }

  private boolean useSparkSubmit() {
    return null != System.getenv("SPARK_SUBMIT");
  }

  public static String getSystemDefault(
      String envName,
      String propertyName,
      String defaultValue) {

    if (envName != null && !envName.isEmpty()) {
      String envValue = System.getenv().get(envName);
      if (envValue != null) {
        return envValue;
      }
    }

    if (propertyName != null && !propertyName.isEmpty()) {
      String propValue = System.getProperty(propertyName);
      if (propValue != null) {
        return propValue;
      }
    }
    return defaultValue;
  }

  public boolean printREPLOutput() {
    return java.lang.Boolean.parseBoolean(getProperty("zeppelin.spark.printREPLOutput"));
  }

  @Override
  public void open() {
    URL[] urls = getClassloaderUrls();

    // Very nice discussion about how scala compiler handle classpath
    // https://groups.google.com/forum/#!topic/scala-user/MlVwo2xCCI0

    /*
     * > val env = new nsc.Settings(errLogger) > env.usejavacp.value = true > val p = new
     * Interpreter(env) > p.setContextClassLoader > Alternatively you can set the class path through
     * nsc.Settings.classpath.
     *
     * >> val settings = new Settings() >> settings.usejavacp.value = true >>
     * settings.classpath.value += File.pathSeparator + >> System.getProperty("java.class.path") >>
     * val in = new Interpreter(settings) { >> override protected def parentClassLoader =
     * getClass.getClassLoader >> } >> in.setContextClassLoader()
     */
    Settings settings = new Settings();
    if (getProperty("args") != null) {
      String[] argsArray = getProperty("args").split(" ");
      LinkedList<String> argList = new LinkedList<String>();
      for (String arg : argsArray) {
        argList.add(arg);
      }

      SparkCommandLine command =
          new SparkCommandLine(scala.collection.JavaConversions.asScalaBuffer(
              argList).toList());
      settings = command.settings();
    }

    // set classpath for scala compiler
    PathSetting pathSettings = settings.classpath();
    String classpath = "";
    List<File> paths = currentClassPath();
    for (File f : paths) {
      if (classpath.length() > 0) {
        classpath += File.pathSeparator;
      }
      classpath += f.getAbsolutePath();
    }

    if (urls != null) {
      for (URL u : urls) {
        if (classpath.length() > 0) {
          classpath += File.pathSeparator;
        }
        classpath += u.getFile();
      }
    }

    // add dependency from DepInterpreter
    DepInterpreter depInterpreter = getDepInterpreter();
    if (depInterpreter != null) {
      SparkDependencyContext depc = depInterpreter.getDependencyContext();
      if (depc != null) {
        List<File> files = depc.getFiles();
        if (files != null) {
          for (File f : files) {
            if (classpath.length() > 0) {
              classpath += File.pathSeparator;
            }
            classpath += f.getAbsolutePath();
          }
        }
      }
    }

    // add dependency from local repo
    String localRepo = getProperty("zeppelin.interpreter.localRepo");
    if (localRepo != null) {
      File localRepoDir = new File(localRepo);
      if (localRepoDir.exists()) {
        File[] files = localRepoDir.listFiles();
        if (files != null) {
          for (File f : files) {
            if (classpath.length() > 0) {
              classpath += File.pathSeparator;
            }
            classpath += f.getAbsolutePath();
          }
        }
      }
    }

    pathSettings.v_$eq(classpath);
    settings.scala$tools$nsc$settings$ScalaSettings$_setter_$classpath_$eq(pathSettings);


    // set classloader for scala compiler
    settings.explicitParentLoader_$eq(new Some<ClassLoader>(Thread.currentThread()
        .getContextClassLoader()));
    BooleanSetting b = (BooleanSetting) settings.usejavacp();
    b.v_$eq(true);
    settings.scala$tools$nsc$settings$StandardScalaSettings$_setter_$usejavacp_$eq(b);

    System.setProperty("scala.repl.name.line", "line" + this.hashCode() + "$");

    synchronized (sharedInterpreterLock) {
      /* create scala repl */
      if (printREPLOutput()) {
        this.interpreter = new SparkILoop(null, new PrintWriter(out));
      } else {
        this.interpreter = new SparkILoop(null, new PrintWriter(Console.out(), false));
      }

      interpreter.settings_$eq(settings);

      interpreter.createInterpreter();

      intp = interpreter.intp();
      intp.setContextClassLoader();
      intp.initializeSynchronous();

      if (classOutputDir == null) {
        classOutputDir = settings.outputDirs().getSingleOutput().get();
      } else {
        // change SparkIMain class output dir
        settings.outputDirs().setSingleOutput(classOutputDir);
        ClassLoader cl = intp.classLoader();

        try {
          Field rootField = cl.getClass().getSuperclass().getDeclaredField("root");
          rootField.setAccessible(true);
          rootField.set(cl, classOutputDir);
        } catch (NoSuchFieldException | IllegalAccessException e) {
          logger.error(e.getMessage(), e);
        }
      }

      completor = new SparkJLineCompletion(intp);

      sc = getSparkContext();
      if (sc.getPoolForName("fair").isEmpty()) {
        Value schedulingMode = org.apache.spark.scheduler.SchedulingMode.FAIR();
        int minimumShare = 0;
        int weight = 1;
        Pool pool = new Pool("fair", schedulingMode, minimumShare, weight);
        sc.taskScheduler().rootPool().addSchedulable(pool);
      }

      sparkVersion = SparkVersion.fromVersionString(sc.version());

      sqlc = getSQLContext();

      dep = getDependencyResolver();

      z = new ZeppelinContext(sc, sqlc, null, dep,
              Integer.parseInt(getProperty("zeppelin.spark.maxResult")));

      intp.interpret("@transient var _binder = new java.util.HashMap[String, Object]()");
      binder = (Map<String, Object>) getValue("_binder");
      binder.put("sc", sc);
      binder.put("sqlc", sqlc);
      binder.put("z", z);

      intp.interpret("@transient val z = "
              + "_binder.get(\"z\").asInstanceOf[org.apache.zeppelin.spark.ZeppelinContext]");
      intp.interpret("@transient val sc = "
              + "_binder.get(\"sc\").asInstanceOf[org.apache.spark.SparkContext]");
      intp.interpret("@transient val sqlc = "
              + "_binder.get(\"sqlc\").asInstanceOf[org.apache.spark.sql.SQLContext]");
      intp.interpret("@transient val sqlContext = "
              + "_binder.get(\"sqlc\").asInstanceOf[org.apache.spark.sql.SQLContext]");
      intp.interpret("import org.apache.spark.SparkContext._");

      if (sparkVersion.oldSqlContextImplicits()) {
        intp.interpret("import sqlContext._");
      } else {
        intp.interpret("import sqlContext.implicits._");
        intp.interpret("import sqlContext.sql");
        intp.interpret("import org.apache.spark.sql.functions._");
      }
    }

    /* Temporary disabling DisplayUtils. see https://issues.apache.org/jira/browse/ZEPPELIN-127
     *
    // Utility functions for display
    intp.interpret("import org.apache.zeppelin.spark.utils.DisplayUtils._");

    // Scala implicit value for spark.maxResult
    intp.interpret("import org.apache.zeppelin.spark.utils.SparkMaxResult");
    intp.interpret("implicit val sparkMaxResult = new SparkMaxResult(" +
            Integer.parseInt(getProperty("zeppelin.spark.maxResult")) + ")");
     */

    try {
      if (sparkVersion.oldLoadFilesMethodName()) {
        Method loadFiles = this.interpreter.getClass().getMethod("loadFiles", Settings.class);
        loadFiles.invoke(this.interpreter, settings);
      } else {
        Method loadFiles = this.interpreter.getClass().getMethod(
                "org$apache$spark$repl$SparkILoop$$loadFiles", Settings.class);
        loadFiles.invoke(this.interpreter, settings);
      }
    } catch (NoSuchMethodException | SecurityException | IllegalAccessException
            | IllegalArgumentException | InvocationTargetException e) {
      throw new InterpreterException(e);
    }

    // add jar from DepInterpreter
    if (depInterpreter != null) {
      SparkDependencyContext depc = depInterpreter.getDependencyContext();
      if (depc != null) {
        List<File> files = depc.getFilesDist();
        if (files != null) {
          for (File f : files) {
            if (f.getName().toLowerCase().endsWith(".jar")) {
              sc.addJar(f.getAbsolutePath());
              logger.info("sc.addJar(" + f.getAbsolutePath() + ")");
            } else {
              sc.addFile(f.getAbsolutePath());
              logger.info("sc.addFile(" + f.getAbsolutePath() + ")");
            }
          }
        }
      }
    }

    // add jar from local repo
    if (localRepo != null) {
      File localRepoDir = new File(localRepo);
      if (localRepoDir.exists()) {
        File[] files = localRepoDir.listFiles();
        if (files != null) {
          for (File f : files) {
            if (f.getName().toLowerCase().endsWith(".jar")) {
              sc.addJar(f.getAbsolutePath());
              logger.info("sc.addJar(" + f.getAbsolutePath() + ")");
            } else {
              sc.addFile(f.getAbsolutePath());
              logger.info("sc.addFile(" + f.getAbsolutePath() + ")");
            }
          }
        }
      }
    }

    numReferenceOfSparkContext.incrementAndGet();
  }

  private List<File> currentClassPath() {
    List<File> paths = classPath(Thread.currentThread().getContextClassLoader());
    String[] cps = System.getProperty("java.class.path").split(File.pathSeparator);
    if (cps != null) {
      for (String cp : cps) {
        paths.add(new File(cp));
      }
    }
    return paths;
  }

  private List<File> classPath(ClassLoader cl) {
    List<File> paths = new LinkedList<File>();
    if (cl == null) {
      return paths;
    }

    if (cl instanceof URLClassLoader) {
      URLClassLoader ucl = (URLClassLoader) cl;
      URL[] urls = ucl.getURLs();
      if (urls != null) {
        for (URL url : urls) {
          paths.add(new File(url.getFile()));
        }
      }
    }
    return paths;
  }

  @Override
  public List<String> completion(String buf, int cursor) {
    if (buf.length() < cursor) {
      cursor = buf.length();
    }
    String completionText = getCompletionTargetString(buf, cursor);
    if (completionText == null) {
      completionText = "";
      cursor = completionText.length();
    }
    ScalaCompleter c = completor.completer();
    Candidates ret = c.complete(completionText, cursor);
    return scala.collection.JavaConversions.seqAsJavaList(ret.candidates());
  }

  private String getCompletionTargetString(String text, int cursor) {
    String[] completionSeqCharaters = {" ", "\n", "\t"};
    int completionEndPosition = cursor;
    int completionStartPosition = cursor;
    int indexOfReverseSeqPostion = cursor;

    String resultCompletionText = "";
    String completionScriptText = "";
    try {
      completionScriptText = text.substring(0, cursor);
    }
    catch (Exception e) {
      logger.error(e.toString());
      return null;
    }
    completionEndPosition = completionScriptText.length();

    String tempReverseCompletionText = new StringBuilder(completionScriptText).reverse().toString();

    for (String seqCharacter : completionSeqCharaters) {
      indexOfReverseSeqPostion = tempReverseCompletionText.indexOf(seqCharacter);

      if (indexOfReverseSeqPostion < completionStartPosition && indexOfReverseSeqPostion > 0) {
        completionStartPosition = indexOfReverseSeqPostion;
      }

    }

    if (completionStartPosition == completionEndPosition) {
      completionStartPosition = 0;
    }
    else
    {
      completionStartPosition = completionEndPosition - completionStartPosition;
    }
    resultCompletionText = completionScriptText.substring(
            completionStartPosition , completionEndPosition);

    return resultCompletionText;
  }

  public Object getValue(String name) {
    Object ret = intp.valueOfTerm(name);
    if (ret instanceof None) {
      return null;
    } else if (ret instanceof Some) {
      return ((Some) ret).get();
    } else {
      return ret;
    }
  }

  String getJobGroup(InterpreterContext context){
    return "zeppelin-" + context.getParagraphId();
  }

  /**
   * Interpret a single line.
   */
  @Override
  public InterpreterResult interpret(String line, InterpreterContext context) {
    if (sparkVersion.isUnsupportedVersion()) {
      return new InterpreterResult(Code.ERROR, "Spark " + sparkVersion.toString()
          + " is not supported");
    }

    z.setInterpreterContext(context);
    if (line == null || line.trim().length() == 0) {
      return new InterpreterResult(Code.SUCCESS);
    }
    return interpret(line.split("\n"), context);
  }

  public InterpreterResult interpret(String[] lines, InterpreterContext context) {
    synchronized (this) {
      z.setGui(context.getGui());
      sc.setJobGroup(getJobGroup(context), "Zeppelin", false);
      InterpreterResult r = interpretInput(lines, context);
      sc.clearJobGroup();
      return r;
    }
  }

  public InterpreterResult interpretInput(String[] lines, InterpreterContext context) {
    SparkEnv.set(env);

    // add print("") to make sure not finishing with comment
    // see https://github.com/NFLabs/zeppelin/issues/151
    String[] linesToRun = new String[lines.length + 1];
    for (int i = 0; i < lines.length; i++) {
      linesToRun[i] = lines[i];
    }
    linesToRun[lines.length] = "print(\"\")";

    Console.setOut(context.out);
    out.setInterpreterOutput(context.out);
    context.out.clear();
    Code r = null;
    String incomplete = "";
    boolean inComment = false;

    for (int l = 0; l < linesToRun.length; l++) {
      String s = linesToRun[l];
      // check if next line starts with "." (but not ".." or "./") it is treated as an invocation
      if (l + 1 < linesToRun.length) {
        String nextLine = linesToRun[l + 1].trim();
        boolean continuation = false;
        if (nextLine.isEmpty()
           || nextLine.startsWith("//")         // skip empty line or comment
           || nextLine.startsWith("}")
           || nextLine.startsWith("object")) {  // include "} object" for Scala companion object
          continuation = true;
        } else if (!inComment && nextLine.startsWith("/*")) {
          inComment = true;
          continuation = true;
        } else if (inComment && nextLine.lastIndexOf("*/") >= 0) {
          inComment = false;
          continuation = true;
        } else if (nextLine.length() > 1
                && nextLine.charAt(0) == '.'
                && nextLine.charAt(1) != '.'     // ".."
                && nextLine.charAt(1) != '/') {  // "./"
          continuation = true;
        } else if (inComment) {
          continuation = true;
        }
        if (continuation) {
          incomplete += s + "\n";
          continue;
        }
      }

      scala.tools.nsc.interpreter.Results.Result res = null;
      try {
        res = intp.interpret(incomplete + s);
      } catch (Exception e) {
        sc.clearJobGroup();
        out.setInterpreterOutput(null);
        logger.info("Interpreter exception", e);
        return new InterpreterResult(Code.ERROR, InterpreterUtils.getMostRelevantMessage(e));
      }

      r = getResultCode(res);

      if (r == Code.ERROR) {
        sc.clearJobGroup();
        out.setInterpreterOutput(null);
        return new InterpreterResult(r, "");
      } else if (r == Code.INCOMPLETE) {
        incomplete += s + "\n";
      } else {
        incomplete = "";
      }
    }

    if (r == Code.INCOMPLETE) {
      sc.clearJobGroup();
      out.setInterpreterOutput(null);
      return new InterpreterResult(r, "Incomplete expression");
    } else {
      sc.clearJobGroup();
      out.setInterpreterOutput(null);
      return new InterpreterResult(Code.SUCCESS);
    }
  }


  @Override
  public void cancel(InterpreterContext context) {
    sc.cancelJobGroup(getJobGroup(context));
  }

  @Override
  public int getProgress(InterpreterContext context) {
    String jobGroup = getJobGroup(context);
    int completedTasks = 0;
    int totalTasks = 0;

    DAGScheduler scheduler = sc.dagScheduler();
    if (scheduler == null) {
      return 0;
    }
    HashSet<ActiveJob> jobs = scheduler.activeJobs();
    if (jobs == null || jobs.size() == 0) {
      return 0;
    }
    Iterator<ActiveJob> it = jobs.iterator();
    while (it.hasNext()) {
      ActiveJob job = it.next();
      String g = (String) job.properties().get("spark.jobGroup.id");
      if (jobGroup.equals(g)) {
        int[] progressInfo = null;
        try {
          Object finalStage = job.getClass().getMethod("finalStage").invoke(job);
          if (sparkVersion.getProgress1_0()) {
            progressInfo = getProgressFromStage_1_0x(sparkListener, finalStage);
          } else {
            progressInfo = getProgressFromStage_1_1x(sparkListener, finalStage);
          }
        } catch (IllegalAccessException | IllegalArgumentException
            | InvocationTargetException | NoSuchMethodException
            | SecurityException e) {
          logger.error("Can't get progress info", e);
          return 0;
        }
        totalTasks += progressInfo[0];
        completedTasks += progressInfo[1];
      }
    }

    if (totalTasks == 0) {
      return 0;
    }
    return completedTasks * 100 / totalTasks;
  }

  private int[] getProgressFromStage_1_0x(JobProgressListener sparkListener, Object stage)
      throws IllegalAccessException, IllegalArgumentException,
      InvocationTargetException, NoSuchMethodException, SecurityException {
    int numTasks = (int) stage.getClass().getMethod("numTasks").invoke(stage);
    int completedTasks = 0;

    int id = (int) stage.getClass().getMethod("id").invoke(stage);

    Object completedTaskInfo = null;

    completedTaskInfo = JavaConversions.mapAsJavaMap(
        (HashMap<Object, Object>) sparkListener.getClass()
            .getMethod("stageIdToTasksComplete").invoke(sparkListener)).get(id);

    if (completedTaskInfo != null) {
      completedTasks += (int) completedTaskInfo;
    }
    List<Object> parents = JavaConversions.seqAsJavaList((Seq<Object>) stage.getClass()
        .getMethod("parents").invoke(stage));
    if (parents != null) {
      for (Object s : parents) {
        int[] p = getProgressFromStage_1_0x(sparkListener, s);
        numTasks += p[0];
        completedTasks += p[1];
      }
    }

    return new int[] {numTasks, completedTasks};
  }

  private int[] getProgressFromStage_1_1x(JobProgressListener sparkListener, Object stage)
      throws IllegalAccessException, IllegalArgumentException,
      InvocationTargetException, NoSuchMethodException, SecurityException {
    int numTasks = (int) stage.getClass().getMethod("numTasks").invoke(stage);
    int completedTasks = 0;
    int id = (int) stage.getClass().getMethod("id").invoke(stage);

    try {
      Method stageIdToData = sparkListener.getClass().getMethod("stageIdToData");
      HashMap<Tuple2<Object, Object>, Object> stageIdData =
          (HashMap<Tuple2<Object, Object>, Object>) stageIdToData.invoke(sparkListener);
      Class<?> stageUIDataClass =
          this.getClass().forName("org.apache.spark.ui.jobs.UIData$StageUIData");

      Method numCompletedTasks = stageUIDataClass.getMethod("numCompleteTasks");
      Set<Tuple2<Object, Object>> keys =
          JavaConverters.setAsJavaSetConverter(stageIdData.keySet()).asJava();
      for (Tuple2<Object, Object> k : keys) {
        if (id == (int) k._1()) {
          Object uiData = stageIdData.get(k).get();
          completedTasks += (int) numCompletedTasks.invoke(uiData);
        }
      }
    } catch (Exception e) {
      logger.error("Error on getting progress information", e);
    }

    List<Object> parents = JavaConversions.seqAsJavaList((Seq<Object>) stage.getClass()
        .getMethod("parents").invoke(stage));
    if (parents != null) {
      for (Object s : parents) {
        int[] p = getProgressFromStage_1_1x(sparkListener, s);
        numTasks += p[0];
        completedTasks += p[1];
      }
    }
    return new int[] {numTasks, completedTasks};
  }

  private Code getResultCode(scala.tools.nsc.interpreter.Results.Result r) {
    if (r instanceof scala.tools.nsc.interpreter.Results.Success$) {
      return Code.SUCCESS;
    } else if (r instanceof scala.tools.nsc.interpreter.Results.Incomplete$) {
      return Code.INCOMPLETE;
    } else {
      return Code.ERROR;
    }
  }

  @Override
  public void close() {
    logger.info("Close interpreter");

    if (numReferenceOfSparkContext.decrementAndGet() == 0) {
      sc.stop();
      sc = null;
    }

    intp.close();
  }

  @Override
  public FormType getFormType() {
    return FormType.NATIVE;
  }

  public JobProgressListener getJobProgressListener() {
    return sparkListener;
  }

  @Override
  public Scheduler getScheduler() {
    return SchedulerFactory.singleton().createOrGetFIFOScheduler(
      SparkInterpreter.class.getName() + this.hashCode());
  }

  public ZeppelinContext getZeppelinContext() {
    return z;
  }

  public SparkVersion getSparkVersion() {
    return sparkVersion;
  }
}
