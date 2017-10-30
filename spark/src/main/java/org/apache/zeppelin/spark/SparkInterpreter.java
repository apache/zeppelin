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
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.lang3.StringUtils;
import org.apache.hadoop.security.UserGroupInformation;
import org.apache.spark.SecurityManager;
import org.apache.spark.SparkConf;
import org.apache.spark.SparkContext;
import org.apache.spark.SparkEnv;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.repl.SparkILoop;
import org.apache.spark.scheduler.ActiveJob;
import org.apache.spark.scheduler.DAGScheduler;
import org.apache.spark.scheduler.Pool;
import org.apache.spark.scheduler.SparkListenerJobStart;
import org.apache.spark.sql.SQLContext;
import org.apache.spark.ui.SparkUI;
import org.apache.spark.ui.jobs.JobProgressListener;
import org.apache.zeppelin.interpreter.BaseZeppelinContext;
import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterHookRegistry;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResult.Code;
import org.apache.zeppelin.interpreter.InterpreterUtils;
import org.apache.zeppelin.interpreter.WrappedInterpreter;
import org.apache.zeppelin.interpreter.remote.RemoteEventClientWrapper;
import org.apache.zeppelin.interpreter.thrift.InterpreterCompletion;
import org.apache.zeppelin.interpreter.util.InterpreterOutputStream;
import org.apache.zeppelin.resource.ResourcePool;
import org.apache.zeppelin.resource.WellKnownResourceName;
import org.apache.zeppelin.scheduler.Scheduler;
import org.apache.zeppelin.scheduler.SchedulerFactory;
import org.apache.zeppelin.spark.dep.SparkDependencyContext;
import org.apache.zeppelin.spark.dep.SparkDependencyResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import scala.Console;
import scala.Enumeration.Value;
import scala.None;
import scala.Option;
import scala.Some;
import scala.Tuple2;
import scala.collection.Iterator;
import scala.collection.JavaConversions;
import scala.collection.JavaConverters;
import scala.collection.Seq;
import scala.collection.convert.WrapAsJava$;
import scala.collection.mutable.HashMap;
import scala.collection.mutable.HashSet;
import scala.reflect.io.AbstractFile;
import scala.tools.nsc.Global;
import scala.tools.nsc.Settings;
import scala.tools.nsc.interpreter.Completion.Candidates;
import scala.tools.nsc.interpreter.Completion.ScalaCompleter;
import scala.tools.nsc.interpreter.IMain;
import scala.tools.nsc.interpreter.Results;
import scala.tools.nsc.settings.MutableSettings;
import scala.tools.nsc.settings.MutableSettings.BooleanSetting;
import scala.tools.nsc.settings.MutableSettings.PathSetting;

/**
 * Spark interpreter for Zeppelin.
 *
 */
public class SparkInterpreter extends Interpreter {
  public static Logger logger = LoggerFactory.getLogger(SparkInterpreter.class);

  private SparkZeppelinContext z;
  private SparkILoop interpreter;
  /**
   * intp - org.apache.spark.repl.SparkIMain (scala 2.10)
   * intp - scala.tools.nsc.interpreter.IMain; (scala 2.11)
   */
  private Object intp;
  private SparkConf conf;
  private static SparkContext sc;
  private static SQLContext sqlc;
  private static InterpreterHookRegistry hooks;
  private static SparkEnv env;
  private static Object sparkSession;    // spark 2.x
  private static JobProgressListener sparkListener;
  private static AbstractFile classOutputDir;
  private static Integer sharedInterpreterLock = new Integer(0);
  private static AtomicInteger numReferenceOfSparkContext = new AtomicInteger(0);

  private InterpreterOutputStream out;
  private SparkDependencyResolver dep;
  private static String sparkUrl;

  /**
   * completer - org.apache.spark.repl.SparkJLineCompletion (scala 2.10)
   */
  private Object completer = null;

  private Map<String, Object> binder;
  private SparkVersion sparkVersion;
  private static File outputDir;          // class outputdir for scala 2.11
  private Object classServer;      // classserver for scala 2.11
  private JavaSparkContext jsc;
  private boolean enableSupportedVersionCheck;

  public SparkInterpreter(Properties property) {
    super(property);
    out = new InterpreterOutputStream(logger);
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

  public JavaSparkContext getJavaSparkContext() {
    synchronized (sharedInterpreterLock) {
      if (jsc == null) {
        jsc = JavaSparkContext.fromSparkContext(sc);
      }
      return jsc;
    }
  }

  public boolean isSparkContextInitialized() {
    synchronized (sharedInterpreterLock) {
      return sc != null;
    }
  }

  static JobProgressListener setupListeners(SparkContext context) {
    JobProgressListener pl = new JobProgressListener(context.getConf()) {
      @Override
      public synchronized void onJobStart(SparkListenerJobStart jobStart) {
        super.onJobStart(jobStart);
        int jobId = jobStart.jobId();
        String jobGroupId = jobStart.properties().getProperty("spark.jobGroup.id");
        String uiEnabled = jobStart.properties().getProperty("spark.ui.enabled");
        String jobUrl = getJobUrl(jobId);
        String noteId = Utils.getNoteId(jobGroupId);
        String paragraphId = Utils.getParagraphId(jobGroupId);
        // Button visible if Spark UI property not set, set as invalid boolean or true
        java.lang.Boolean showSparkUI =
                uiEnabled == null || !uiEnabled.trim().toLowerCase().equals("false");
        if (showSparkUI && jobUrl != null) {
          RemoteEventClientWrapper eventClient = BaseZeppelinContext.getEventClient();
          Map<String, String> infos = new java.util.HashMap<>();
          infos.put("jobUrl", jobUrl);
          infos.put("label", "SPARK JOB");
          infos.put("tooltip", "View in Spark web UI");
          if (eventClient != null) {
            eventClient.onParaInfosReceived(noteId, paragraphId, infos);
          }
        }
      }

      private String getJobUrl(int jobId) {
        String jobUrl = null;
        if (sparkUrl != null) {
          jobUrl = sparkUrl + "/jobs/job/?id=" + jobId;
        }
        return jobUrl;
      }

    };
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

  /**
   * See org.apache.spark.sql.SparkSession.hiveClassesArePresent
   * @return
   */
  private boolean hiveClassesArePresent() {
    try {
      this.getClass().forName("org.apache.spark.sql.hive.execution.InsertIntoHiveTable");
      this.getClass().forName("org.apache.hadoop.hive.conf.HiveConf");
      return true;
    } catch (ClassNotFoundException | NoClassDefFoundError e) {
      return false;
    }
  }

  private boolean importImplicit() {
    return java.lang.Boolean.parseBoolean(getProperty("zeppelin.spark.importImplicit"));
  }

  public Object getSparkSession() {
    synchronized (sharedInterpreterLock) {
      if (sparkSession == null) {
        createSparkSession();
      }
      return sparkSession;
    }
  }

  public SQLContext getSQLContext() {
    synchronized (sharedInterpreterLock) {
      if (Utils.isSpark2()) {
        return getSQLContext_2();
      } else {
        return getSQLContext_1();
      }
    }
  }

  /**
   * Get SQLContext for spark 2.x
   */
  private SQLContext getSQLContext_2() {
    if (sqlc == null) {
      sqlc = (SQLContext) Utils.invokeMethod(sparkSession, "sqlContext");
    }
    return sqlc;
  }

  public SQLContext getSQLContext_1() {
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


  public SparkDependencyResolver getDependencyResolver() {
    if (dep == null) {
      dep = new SparkDependencyResolver(
          (Global) Utils.invokeMethod(intp, "global"),
          (ClassLoader) Utils.invokeMethod(Utils.invokeMethod(intp, "classLoader"), "getParent"),
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

  public boolean isYarnMode() {
    String master = getProperty("master");
    if (master == null) {
      master = getProperty("spark.master", "local[*]");
    }
    return master.startsWith("yarn");
  }

  /**
   * Spark 2.x
   * Create SparkSession
   */
  public Object createSparkSession() {
    // use local mode for embedded spark mode when spark.master is not found
    conf.setIfMissing("spark.master", "local");
    logger.info("------ Create new SparkSession {} -------", conf.get("spark.master"));
    String execUri = System.getenv("SPARK_EXECUTOR_URI");
    if (outputDir != null) {
      conf.set("spark.repl.class.outputDir", outputDir.getAbsolutePath());
    }

    if (execUri != null) {
      conf.set("spark.executor.uri", execUri);
    }
    conf.set("spark.scheduler.mode", "FAIR");

    Properties intpProperty = getProperties();
    for (Object k : intpProperty.keySet()) {
      String key = (String) k;
      String val = toString(intpProperty.get(key));
      if (!val.trim().isEmpty()) {
        if (key.startsWith("spark.")) {
          logger.debug(String.format("SparkConf: key = [%s], value = [%s]", key, val));
          conf.set(key, val);
        }
        if (key.startsWith("zeppelin.spark.")) {
          String sparkPropertyKey = key.substring("zeppelin.spark.".length());
          logger.debug(String.format("SparkConf: key = [%s], value = [%s]", sparkPropertyKey, val));
          conf.set(sparkPropertyKey, val);
        }
      }
    }

    Class SparkSession = Utils.findClass("org.apache.spark.sql.SparkSession");
    Object builder = Utils.invokeStaticMethod(SparkSession, "builder");
    Utils.invokeMethod(builder, "config", new Class[]{ SparkConf.class }, new Object[]{ conf });

    if (useHiveContext()) {
      if (hiveClassesArePresent()) {
        Utils.invokeMethod(builder, "enableHiveSupport");
        sparkSession = Utils.invokeMethod(builder, "getOrCreate");
        logger.info("Created Spark session with Hive support");
      } else {
        Utils.invokeMethod(builder, "config",
            new Class[]{ String.class, String.class},
            new Object[]{ "spark.sql.catalogImplementation", "in-memory"});
        sparkSession = Utils.invokeMethod(builder, "getOrCreate");
        logger.info("Created Spark session with Hive support use in-memory catalogImplementation");
      }
    } else {
      sparkSession = Utils.invokeMethod(builder, "getOrCreate");
      logger.info("Created Spark session");
    }

    return sparkSession;
  }

  public SparkContext createSparkContext() {
    if (Utils.isSpark2()) {
      return createSparkContext_2();
    } else {
      return createSparkContext_1();
    }
  }

  /**
   * Create SparkContext for spark 2.x
   * @return
   */
  private SparkContext createSparkContext_2() {
    return (SparkContext) Utils.invokeMethod(sparkSession, "sparkContext");
  }

  public SparkContext createSparkContext_1() {
    // use local mode for embedded spark mode when spark.master is not found
    if (!conf.contains("spark.master")) {
      conf.setMaster("local");
    }
    logger.info("------ Create new SparkContext {} -------", conf.get("spark.master"));

    String execUri = System.getenv("SPARK_EXECUTOR_URI");
    String[] jars = null;

    if (Utils.isScala2_10()) {
      jars = (String[]) Utils.invokeStaticMethod(SparkILoop.class, "getAddedJars");
    } else {
      jars = (String[]) Utils.invokeStaticMethod(
              Utils.findClass("org.apache.spark.repl.Main"), "getAddedJars");
    }

    String classServerUri = null;
    String replClassOutputDirectory = null;

    try { // in case of spark 1.1x, spark 1.2x
      Method classServer = intp.getClass().getMethod("classServer");
      Object httpServer = classServer.invoke(intp);
      classServerUri = (String) Utils.invokeMethod(httpServer, "uri");
    } catch (NoSuchMethodException | SecurityException | IllegalAccessException
        | IllegalArgumentException | InvocationTargetException e) {
      // continue
    }

    if (classServerUri == null) {
      try { // for spark 1.3x
        Method classServer = intp.getClass().getMethod("classServerUri");
        classServerUri = (String) classServer.invoke(intp);
      } catch (NoSuchMethodException | SecurityException | IllegalAccessException
          | IllegalArgumentException | InvocationTargetException e) {
        // continue instead of: throw new InterpreterException(e);
        // Newer Spark versions (like the patched CDH5.7.0 one) don't contain this method
        logger.warn(String.format("Spark method classServerUri not available due to: [%s]",
          e.getMessage()));
      }
    }

    if (classServerUri == null) {
      try { // for RcpEnv
        Method getClassOutputDirectory = intp.getClass().getMethod("getClassOutputDirectory");
        File classOutputDirectory = (File) getClassOutputDirectory.invoke(intp);
        replClassOutputDirectory = classOutputDirectory.getAbsolutePath();
      } catch (NoSuchMethodException | SecurityException | IllegalAccessException
              | IllegalArgumentException | InvocationTargetException e) {
        // continue
      }
    }

    if (Utils.isScala2_11()) {
      classServer = createHttpServer(outputDir);
      Utils.invokeMethod(classServer, "start");
      classServerUri = (String) Utils.invokeMethod(classServer, "uri");
    }

    if (classServerUri != null) {
      conf.set("spark.repl.class.uri", classServerUri);
    }

    if (replClassOutputDirectory != null) {
      conf.set("spark.repl.class.outputDir", replClassOutputDirectory);
    }

    if (jars.length > 0) {
      conf.setJars(jars);
    }

    if (execUri != null) {
      conf.set("spark.executor.uri", execUri);
    }
    conf.set("spark.scheduler.mode", "FAIR");

    Properties intpProperty = getProperties();
    for (Object k : intpProperty.keySet()) {
      String key = (String) k;
      String val = toString(intpProperty.get(key));
      if (!val.trim().isEmpty()) {
        if (key.startsWith("spark.")) {
          logger.debug(String.format("SparkConf: key = [%s], value = [%s]", key, val));
          conf.set(key, val);
        }

        if (key.startsWith("zeppelin.spark.")) {
          String sparkPropertyKey = key.substring("zeppelin.spark.".length());
          logger.debug(String.format("SparkConf: key = [%s], value = [%s]", sparkPropertyKey, val));
          conf.set(sparkPropertyKey, val);
        }
      }
    }
    SparkContext sparkContext = new SparkContext(conf);
    return sparkContext;
  }

  static final String toString(Object o) {
    return (o instanceof String) ? (String) o : "";
  }

  public static boolean useSparkSubmit() {
    return null != System.getenv("SPARK_SUBMIT");
  }

  public boolean printREPLOutput() {
    return java.lang.Boolean.parseBoolean(getProperty("zeppelin.spark.printREPLOutput"));
  }

  @Override
  public void open() throws InterpreterException {
    this.enableSupportedVersionCheck = java.lang.Boolean.parseBoolean(
        getProperty("zeppelin.spark.enableSupportedVersionCheck", "true"));

    // set properties and do login before creating any spark stuff for secured cluster
    if (isYarnMode()) {
      System.setProperty("SPARK_YARN_MODE", "true");
    }
    if (getProperties().containsKey("spark.yarn.keytab") &&
            getProperties().containsKey("spark.yarn.principal")) {
      try {
        String keytab = getProperties().getProperty("spark.yarn.keytab");
        String principal = getProperties().getProperty("spark.yarn.principal");
        UserGroupInformation.loginUserFromKeytab(principal, keytab);
      } catch (IOException e) {
        throw new RuntimeException("Can not pass kerberos authentication", e);
      }
    }

    conf = new SparkConf();
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

    // process args
    String args = getProperty("args");
    if (args == null) {
      args = "";
    }

    String[] argsArray = args.split(" ");
    LinkedList<String> argList = new LinkedList<>();
    for (String arg : argsArray) {
      argList.add(arg);
    }

    DepInterpreter depInterpreter = getDepInterpreter();
    String depInterpreterClasspath = "";
    if (depInterpreter != null) {
      SparkDependencyContext depc = depInterpreter.getDependencyContext();
      if (depc != null) {
        List<File> files = depc.getFiles();
        if (files != null) {
          for (File f : files) {
            if (depInterpreterClasspath.length() > 0) {
              depInterpreterClasspath += File.pathSeparator;
            }
            depInterpreterClasspath += f.getAbsolutePath();
          }
        }
      }
    }


    if (Utils.isScala2_10()) {
      scala.collection.immutable.List<String> list =
          JavaConversions.asScalaBuffer(argList).toList();

      Object sparkCommandLine = Utils.instantiateClass(
          "org.apache.spark.repl.SparkCommandLine",
          new Class[]{ scala.collection.immutable.List.class },
          new Object[]{ list });

      settings = (Settings) Utils.invokeMethod(sparkCommandLine, "settings");
    } else {
      String sparkReplClassDir = getProperty("spark.repl.classdir");
      if (sparkReplClassDir == null) {
        sparkReplClassDir = System.getProperty("spark.repl.classdir");
      }
      if (sparkReplClassDir == null) {
        sparkReplClassDir = System.getProperty("java.io.tmpdir");
      }

      synchronized (sharedInterpreterLock) {
        if (outputDir == null) {
          outputDir = createTempDir(sparkReplClassDir);
        }
      }
      argList.add("-Yrepl-class-based");
      argList.add("-Yrepl-outdir");
      argList.add(outputDir.getAbsolutePath());

      String classpath = "";
      if (conf.contains("spark.jars")) {
        classpath = StringUtils.join(conf.get("spark.jars").split(","), File.separator);
      }

      if (!depInterpreterClasspath.isEmpty()) {
        if (!classpath.isEmpty()) {
          classpath += File.separator;
        }
        classpath += depInterpreterClasspath;
      }

      if (!classpath.isEmpty()) {
        argList.add("-classpath");
        argList.add(classpath);
      }

      scala.collection.immutable.List<String> list =
          JavaConversions.asScalaBuffer(argList).toList();

      settings.processArguments(list, true);
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
    if (classpath.length() > 0) {
      classpath += File.pathSeparator;
    }
    classpath += depInterpreterClasspath;

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
    settings.explicitParentLoader_$eq(new Some<>(Thread.currentThread()
        .getContextClassLoader()));
    BooleanSetting b = (BooleanSetting) settings.usejavacp();
    b.v_$eq(true);
    settings.scala$tools$nsc$settings$StandardScalaSettings$_setter_$usejavacp_$eq(b);

    /* Required for scoped mode.
     * In scoped mode multiple scala compiler (repl) generates class in the same directory.
     * Class names is not randomly generated and look like '$line12.$read$$iw$$iw'
     * Therefore it's possible to generated class conflict(overwrite) with other repl generated
     * class.
     *
     * To prevent generated class name conflict,
     * change prefix of generated class name from each scala compiler (repl) instance.
     *
     * In Spark 2.x, REPL generated wrapper class name should compatible with the pattern
     * ^(\$line(?:\d+)\.\$read)(?:\$\$iw)+$
     *
     * As hashCode() can return a negative integer value and the minus character '-' is invalid
     * in a package name we change it to a numeric value '0' which still conforms to the regexp.
     * 
     */
    System.setProperty("scala.repl.name.line", ("$line" + this.hashCode()).replace('-', '0'));

    // To prevent 'File name too long' error on some file system.
    MutableSettings.IntSetting numClassFileSetting = settings.maxClassfileName();
    numClassFileSetting.v_$eq(128);
    settings.scala$tools$nsc$settings$ScalaSettings$_setter_$maxClassfileName_$eq(
        numClassFileSetting);

    synchronized (sharedInterpreterLock) {
      /* create scala repl */
      if (printREPLOutput()) {
        this.interpreter = new SparkILoop((java.io.BufferedReader) null, new PrintWriter(out));
      } else {
        this.interpreter = new SparkILoop((java.io.BufferedReader) null,
            new PrintWriter(Console.out(), false));
      }

      interpreter.settings_$eq(settings);

      interpreter.createInterpreter();

      intp = Utils.invokeMethod(interpreter, "intp");
      Utils.invokeMethod(intp, "setContextClassLoader");
      Utils.invokeMethod(intp, "initializeSynchronous");

      if (Utils.isScala2_10()) {
        if (classOutputDir == null) {
          classOutputDir = settings.outputDirs().getSingleOutput().get();
        } else {
          // change SparkIMain class output dir
          settings.outputDirs().setSingleOutput(classOutputDir);
          ClassLoader cl = (ClassLoader) Utils.invokeMethod(intp, "classLoader");
          try {
            Field rootField = cl.getClass().getSuperclass().getDeclaredField("root");
            rootField.setAccessible(true);
            rootField.set(cl, classOutputDir);
          } catch (NoSuchFieldException | IllegalAccessException e) {
            logger.error(e.getMessage(), e);
          }
        }
      }

      if (Utils.findClass("org.apache.spark.repl.SparkJLineCompletion", true) != null) {
        completer = Utils.instantiateClass(
            "org.apache.spark.repl.SparkJLineCompletion",
            new Class[]{Utils.findClass("org.apache.spark.repl.SparkIMain")},
            new Object[]{intp});
      } else if (Utils.findClass(
          "scala.tools.nsc.interpreter.PresentationCompilerCompleter", true) != null) {
        completer = Utils.instantiateClass(
            "scala.tools.nsc.interpreter.PresentationCompilerCompleter",
            new Class[]{ IMain.class },
            new Object[]{ intp });
      } else if (Utils.findClass(
          "scala.tools.nsc.interpreter.JLineCompletion", true) != null) {
        completer = Utils.instantiateClass(
            "scala.tools.nsc.interpreter.JLineCompletion",
            new Class[]{ IMain.class },
            new Object[]{ intp });
      }

      if (Utils.isSpark2()) {
        sparkSession = getSparkSession();
      }
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
      
      hooks = getInterpreterGroup().getInterpreterHookRegistry();

      z = new SparkZeppelinContext(sc, sqlc, hooks,
              Integer.parseInt(getProperty("zeppelin.spark.maxResult")));

      interpret("@transient val _binder = new java.util.HashMap[String, Object]()");
      Map<String, Object> binder;
      if (Utils.isScala2_10()) {
        binder = (Map<String, Object>) getValue("_binder");
      } else {
        binder = (Map<String, Object>) getLastObject();
      }
      binder.put("sc", sc);
      binder.put("sqlc", sqlc);
      binder.put("z", z);

      if (Utils.isSpark2()) {
        binder.put("spark", sparkSession);
      }

      interpret("@transient val z = "
              + "_binder.get(\"z\").asInstanceOf[org.apache.zeppelin.spark.SparkZeppelinContext]");
      interpret("@transient val sc = "
              + "_binder.get(\"sc\").asInstanceOf[org.apache.spark.SparkContext]");
      interpret("@transient val sqlc = "
              + "_binder.get(\"sqlc\").asInstanceOf[org.apache.spark.sql.SQLContext]");
      interpret("@transient val sqlContext = "
              + "_binder.get(\"sqlc\").asInstanceOf[org.apache.spark.sql.SQLContext]");

      if (Utils.isSpark2()) {
        interpret("@transient val spark = "
            + "_binder.get(\"spark\").asInstanceOf[org.apache.spark.sql.SparkSession]");
      }

      interpret("import org.apache.spark.SparkContext._");

      if (importImplicit()) {
        if (Utils.isSpark2()) {
          interpret("import spark.implicits._");
          interpret("import spark.sql");
          interpret("import org.apache.spark.sql.functions._");
        } else {
          if (sparkVersion.oldSqlContextImplicits()) {
            interpret("import sqlContext._");
          } else {
            interpret("import sqlContext.implicits._");
            interpret("import sqlContext.sql");
            interpret("import org.apache.spark.sql.functions._");
          }
        }
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

    if (Utils.isScala2_10()) {
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

  public String getSparkUIUrl() {
    if (sparkUrl != null) {
      return sparkUrl;
    }

    String sparkUrlProp = getProperty("zeppelin.spark.uiWebUrl", "");
    if (!StringUtils.isBlank(sparkUrlProp)) {
      return sparkUrlProp;
    }

    if (sparkVersion.newerThanEquals(SparkVersion.SPARK_2_0_0)) {
      Option<String> uiWebUrlOption = (Option<String>) Utils.invokeMethod(sc, "uiWebUrl");
      if (uiWebUrlOption.isDefined()) {
        return uiWebUrlOption.get();
      }
    } else {
      Option<SparkUI> sparkUIOption = (Option<SparkUI>) Utils.invokeMethod(sc, "ui");
      if (sparkUIOption.isDefined()) {
        return (String) Utils.invokeMethod(sparkUIOption.get(), "appUIAddress");
      }
    }
    return null;
  }

  private Results.Result interpret(String line) {
    out.ignoreLeadingNewLinesFromScalaReporter();
    return (Results.Result) Utils.invokeMethod(
        intp,
        "interpret",
        new Class[] {String.class},
        new Object[] {line});
  }

  public void populateSparkWebUrl(InterpreterContext ctx) {
    sparkUrl = getSparkUIUrl();
    Map<String, String> infos = new java.util.HashMap<>();
    infos.put("url", sparkUrl);
    String uiEnabledProp = getProperty("spark.ui.enabled", "true");
    java.lang.Boolean uiEnabled = java.lang.Boolean.parseBoolean(
            uiEnabledProp.trim());
    if (!uiEnabled) {
      infos.put("message", "Spark UI disabled");
    } else {
      if (StringUtils.isNotBlank(sparkUrl)) {
        infos.put("message", "Spark UI enabled");
      } else {
        infos.put("message", "No spark url defined");
      }
    }
    if (ctx != null && ctx.getClient() != null) {
      logger.info("Sending metadata to Zeppelin server: {}", infos.toString());
      getZeppelinContext().setEventClient(ctx.getClient());
      ctx.getClient().onMetaInfosReceived(infos);
    }
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
    List<File> paths = new LinkedList<>();
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
  public List<InterpreterCompletion> completion(String buf, int cursor,
      InterpreterContext interpreterContext) {
    if (completer == null) {
      logger.warn("Can't find completer");
      return new LinkedList<>();
    }

    if (buf.length() < cursor) {
      cursor = buf.length();
    }

    ScalaCompleter c = (ScalaCompleter) Utils.invokeMethod(completer, "completer");
    
    if (Utils.isScala2_10() || !Utils.isCompilerAboveScala2_11_7()) {
      String singleToken = getCompletionTargetString(buf, cursor);
      Candidates ret = c.complete(singleToken, singleToken.length());
      
      List<String> candidates = WrapAsJava$.MODULE$.seqAsJavaList(ret.candidates());
      List<InterpreterCompletion> completions = new LinkedList<>();
  
      for (String candidate : candidates) {
        completions.add(new InterpreterCompletion(candidate, candidate, StringUtils.EMPTY));
      }
  
      return completions;
    } else {
      Candidates ret = c.complete(buf, cursor);
  
      List<String> candidates = WrapAsJava$.MODULE$.seqAsJavaList(ret.candidates());
      List<InterpreterCompletion> completions = new LinkedList<>();
  
      for (String candidate : candidates) {
        completions.add(new InterpreterCompletion(candidate, candidate, StringUtils.EMPTY));
      }
  
      return completions;
    }
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

  /*
   * this method doesn't work in scala 2.11
   * Somehow intp.valueOfTerm returns scala.None always with -Yrepl-class-based option
   */
  public Object getValue(String name) {
    Object ret = Utils.invokeMethod(
            intp, "valueOfTerm", new Class[]{String.class}, new Object[]{name});

    if (ret instanceof None || ret instanceof scala.None$) {
      return null;
    } else if (ret instanceof Some) {
      return ((Some) ret).get();
    } else {
      return ret;
    }
  }

  public Object getLastObject() {
    IMain.Request r = (IMain.Request) Utils.invokeMethod(intp, "lastRequest");
    if (r == null || r.lineRep() == null) {
      return null;
    }
    Object obj = r.lineRep().call("$result",
        JavaConversions.asScalaBuffer(new LinkedList<>()));
    return obj;
  }

  boolean isUnsupportedSparkVersion() {
    return enableSupportedVersionCheck  && sparkVersion.isUnsupportedVersion();
  }

  /**
   * Interpret a single line.
   */
  @Override
  public InterpreterResult interpret(String line, InterpreterContext context) {
    if (isUnsupportedSparkVersion()) {
      return new InterpreterResult(Code.ERROR, "Spark " + sparkVersion.toString()
          + " is not supported");
    }
    populateSparkWebUrl(context);
    z.setInterpreterContext(context);
    if (line == null || line.trim().length() == 0) {
      return new InterpreterResult(Code.SUCCESS);
    }
    return interpret(line.split("\n"), context);
  }

  public InterpreterResult interpret(String[] lines, InterpreterContext context) {
    synchronized (this) {
      z.setGui(context.getGui());
      String jobDesc = "Started by: " + Utils.getUserName(context.getAuthenticationInfo());
      sc.setJobGroup(Utils.buildJobGroupId(context), jobDesc, false);
      InterpreterResult r = interpretInput(lines, context);
      sc.clearJobGroup();
      return r;
    }
  }

  public InterpreterResult interpretInput(String[] lines, InterpreterContext context) {
    SparkEnv.set(env);

    String[] linesToRun = new String[lines.length];
    for (int i = 0; i < lines.length; i++) {
      linesToRun[i] = lines[i];
    }

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
        res = interpret(incomplete + s);
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

    // make sure code does not finish with comment
    if (r == Code.INCOMPLETE) {
      scala.tools.nsc.interpreter.Results.Result res = null;
      res = interpret(incomplete + "\nprint(\"\")");
      r = getResultCode(res);
    }

    if (r == Code.INCOMPLETE) {
      sc.clearJobGroup();
      out.setInterpreterOutput(null);
      return new InterpreterResult(r, "Incomplete expression");
    } else {
      sc.clearJobGroup();
      putLatestVarInResourcePool(context);
      out.setInterpreterOutput(null);
      return new InterpreterResult(Code.SUCCESS);
    }
  }

  private void putLatestVarInResourcePool(InterpreterContext context) {
    String varName = (String) Utils.invokeMethod(intp, "mostRecentVar");
    if (varName == null || varName.isEmpty()) {
      return;
    }
    Object lastObj = null;
    try {
      if (Utils.isScala2_10()) {
        lastObj = getValue(varName);
      } else {
        lastObj = getLastObject();
      }
    } catch (NullPointerException e) {
      // Some case, scala.tools.nsc.interpreter.IMain$ReadEvalPrint.call throws an NPE
      logger.error(e.getMessage(), e);
    }

    if (lastObj != null) {
      ResourcePool resourcePool = context.getResourcePool();
      resourcePool.put(context.getNoteId(), context.getParagraphId(),
          WellKnownResourceName.ZeppelinReplResult.toString(), lastObj);
    }
  };


  @Override
  public void cancel(InterpreterContext context) {
    sc.cancelJobGroup(Utils.buildJobGroupId(context));
  }

  @Override
  public int getProgress(InterpreterContext context) {
    String jobGroup = Utils.buildJobGroupId(context);
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
      if (sparkSession != null) {
        Utils.invokeMethod(sparkSession, "stop");
      } else if (sc != null){
        sc.stop();
      }
      sparkSession = null;
      sc = null;
      jsc = null;
      if (classServer != null) {
        Utils.invokeMethod(classServer, "stop");
        classServer = null;
      }
    }

    Utils.invokeMethod(intp, "close");
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

  public SparkZeppelinContext getZeppelinContext() {
    return z;
  }

  public SparkVersion getSparkVersion() {
    return sparkVersion;
  }

  private File createTempDir(String dir) {
    File file = null;

    // try Utils.createTempDir()
    file = (File) Utils.invokeStaticMethod(
      Utils.findClass("org.apache.spark.util.Utils"),
      "createTempDir",
      new Class[]{String.class, String.class},
      new Object[]{dir, "spark"});

    // fallback to old method
    if (file == null) {
      file = (File) Utils.invokeStaticMethod(
        Utils.findClass("org.apache.spark.util.Utils"),
        "createTempDir",
        new Class[]{String.class},
        new Object[]{dir});
    }

    return file;
  }

  private Object createHttpServer(File outputDir) {
    SparkConf conf = new SparkConf();
    try {
      // try to create HttpServer
      Constructor<?> constructor = getClass().getClassLoader()
          .loadClass("org.apache.spark.HttpServer")
          .getConstructor(new Class[]{
            SparkConf.class, File.class, SecurityManager.class, int.class, String.class});

      Object securityManager = createSecurityManager(conf);
      return constructor.newInstance(new Object[]{
        conf, outputDir, securityManager, 0, "HTTP Server"});

    } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException |
        InstantiationException | InvocationTargetException e) {
      // fallback to old constructor
      Constructor<?> constructor = null;
      try {
        constructor = getClass().getClassLoader()
            .loadClass("org.apache.spark.HttpServer")
            .getConstructor(new Class[]{
              File.class, SecurityManager.class, int.class, String.class});
        return constructor.newInstance(new Object[] {
          outputDir, createSecurityManager(conf), 0, "HTTP Server"});
      } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException |
          InstantiationException | InvocationTargetException e1) {
        logger.error(e1.getMessage(), e1);
        return null;
      }
    }
  }

  /**
   * Constructor signature of SecurityManager changes in spark 2.1.0, so we use this method to
   * create SecurityManager properly for different versions of spark
   *
   * @param conf
   * @return
   * @throws ClassNotFoundException
   * @throws NoSuchMethodException
   * @throws IllegalAccessException
   * @throws InvocationTargetException
   * @throws InstantiationException
   */
  private Object createSecurityManager(SparkConf conf) throws ClassNotFoundException,
      NoSuchMethodException, IllegalAccessException, InvocationTargetException,
      InstantiationException {
    Object securityManager = null;
    try {
      Constructor<?> smConstructor = getClass().getClassLoader()
          .loadClass("org.apache.spark.SecurityManager")
          .getConstructor(new Class[]{ SparkConf.class, scala.Option.class });
      securityManager = smConstructor.newInstance(conf, null);
    } catch (NoSuchMethodException e) {
      Constructor<?> smConstructor = getClass().getClassLoader()
          .loadClass("org.apache.spark.SecurityManager")
          .getConstructor(new Class[]{ SparkConf.class });
      securityManager = smConstructor.newInstance(conf);
    }
    return securityManager;
  }
}
