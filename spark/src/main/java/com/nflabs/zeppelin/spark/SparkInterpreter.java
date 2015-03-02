package com.nflabs.zeppelin.spark;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

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
import org.apache.spark.scheduler.Stage;
import org.apache.spark.sql.SQLContext;
import org.apache.spark.sql.hive.HiveContext;
import org.apache.spark.ui.jobs.JobProgressListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import scala.Console;
import scala.Enumeration.Value;
import scala.None;
import scala.Some;
import scala.Tuple2;
import scala.collection.Iterator;
import scala.collection.JavaConversions;
import scala.collection.JavaConverters;
import scala.collection.mutable.HashMap;
import scala.collection.mutable.HashSet;
import scala.tools.nsc.Settings;
import scala.tools.nsc.interpreter.Completion.Candidates;
import scala.tools.nsc.interpreter.Completion.ScalaCompleter;
import scala.tools.nsc.settings.MutableSettings.BooleanSetting;
import scala.tools.nsc.settings.MutableSettings.PathSetting;

import com.nflabs.zeppelin.interpreter.Interpreter;
import com.nflabs.zeppelin.interpreter.InterpreterContext;
import com.nflabs.zeppelin.interpreter.InterpreterGroup;
import com.nflabs.zeppelin.interpreter.InterpreterPropertyBuilder;
import com.nflabs.zeppelin.interpreter.InterpreterResult;
import com.nflabs.zeppelin.interpreter.InterpreterResult.Code;
import com.nflabs.zeppelin.interpreter.WrappedInterpreter;
import com.nflabs.zeppelin.notebook.form.Setting;
import com.nflabs.zeppelin.scheduler.Scheduler;
import com.nflabs.zeppelin.scheduler.SchedulerFactory;
import com.nflabs.zeppelin.spark.dep.DependencyContext;
import com.nflabs.zeppelin.spark.dep.DependencyResolver;

/**
 * Spark interpreter for Zeppelin.
 *
 */
public class SparkInterpreter extends Interpreter {
  Logger logger = LoggerFactory.getLogger(SparkInterpreter.class);

  static {
    Interpreter.register(
        "spark",
        "spark",
        SparkInterpreter.class.getName(),
        new InterpreterPropertyBuilder()
            .add("spark.app.name", "Zeppelin", "The name of spark application.")
            .add("master",
                getSystemDefault("MASTER", "spark.master", "local[*]"),
                "Spark master uri. ex) spark://masterhost:7077")
            .add("spark.executor.memory",
                getSystemDefault(null, "spark.executor.memory", "512m"),
                "Executor memory per worker instance. ex) 512m, 32g")
            .add("spark.cores.max",
                getSystemDefault(null, "spark.cores.max", ""),
                "Total number of cores to use. Empty value uses all available core.")
            .add("spark.yarn.jar",
                getSystemDefault("SPARK_YARN_JAR", "spark.yarn.jar", ""),
                "The location of the Spark jar file. If you use yarn as a cluster, "
                + "we should set this value")
            .add("args", "", "spark commandline args").build());

  }

  private ZeppelinContext z;
  private SparkILoop interpreter;
  private SparkIMain intp;
  private SparkContext sc;
  private ByteArrayOutputStream out;
  private SQLContext sqlc;
  private HiveContext hiveContext;
  private DependencyResolver dep;
  private SparkJLineCompletion completor;

  private JobProgressListener sparkListener;

  private Map<String, Object> binder;
  private SparkEnv env;


  public SparkInterpreter(Properties property) {
    super(property);
    out = new ByteArrayOutputStream();
  }

  public SparkInterpreter(Properties property, SparkContext sc) {
    this(property);

    this.sc = sc;
    env = SparkEnv.get();
    sparkListener = setupListeners(this.sc);
  }

  public synchronized SparkContext getSparkContext() {
    if (sc == null) {
      sc = createSparkContext();
      env = SparkEnv.get();
      sparkListener = setupListeners(sc);
    }
    return sc;
  }

  public boolean isSparkContextInitialized() {
    return sc != null;
  }

  private static JobProgressListener setupListeners(SparkContext context) {
    JobProgressListener pl = new JobProgressListener(context.getConf());
    context.listenerBus().addListener(pl);
    return pl;
  }

  public SQLContext getSQLContext() {
    if (sqlc == null) {
      sqlc = new SQLContext(getSparkContext());
    }
    return sqlc;
  }

  public HiveContext getHiveContext() {
    if (hiveContext == null) {
      hiveContext = new HiveContext(getSparkContext());
    }
    return hiveContext;
  }

  public DependencyResolver getDependencyResolver() {
    if (dep == null) {
      dep = new DependencyResolver(intp, sc);
    }
    return dep;
  }

  private DepInterpreter getDepInterpreter() {
    InterpreterGroup intpGroup = getInterpreterGroup();
    if (intpGroup == null) return null;
    for (Interpreter intp : intpGroup) {
      if (intp.getClassName().equals(DepInterpreter.class.getName())) {
        Interpreter p = intp;
        while (p instanceof WrappedInterpreter) {
          p = ((WrappedInterpreter) p).getInnerInterpreter();
        }
        return (DepInterpreter) p;
      }
    }
    return null;
  }

  public SparkContext createSparkContext() {
    System.err.println("------ Create new SparkContext " + getProperty("master") + " -------");

    String execUri = System.getenv("SPARK_EXECUTOR_URI");
    String[] jars = SparkILoop.getAddedJars();
    SparkConf conf =
        new SparkConf()
            .setMaster(getProperty("master"))
            .setAppName(getProperty("spark.app.name"))
            .setJars(jars)
            .set("spark.repl.class.uri", interpreter.intp().classServer().uri());

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
      if (key.startsWith("spark.")) {
        Object value = intpProperty.get(key);
        if (value != null
            && value instanceof String
            && !((String) value).trim().isEmpty()) {
          logger.debug(String.format("SparkConf: key = [%s], value = [%s]", key, value));
          conf.set(key, (String) value);
        }
      }
    }

    SparkContext sparkContext = new SparkContext(conf);
    return sparkContext;
  }

  private static String getSystemDefault(
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
      DependencyContext depc = depInterpreter.getDependencyContext();
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

    pathSettings.v_$eq(classpath);
    settings.scala$tools$nsc$settings$ScalaSettings$_setter_$classpath_$eq(pathSettings);


    // set classloader for scala compiler
    settings.explicitParentLoader_$eq(new Some<ClassLoader>(Thread.currentThread()
        .getContextClassLoader()));
    BooleanSetting b = (BooleanSetting) settings.usejavacp();
    b.v_$eq(true);
    settings.scala$tools$nsc$settings$StandardScalaSettings$_setter_$usejavacp_$eq(b);

    PrintStream printStream = new PrintStream(out);

    /* spark interpreter */
    this.interpreter = new SparkILoop(null, new PrintWriter(out));
    interpreter.settings_$eq(settings);

    interpreter.createInterpreter();

    intp = interpreter.intp();
    intp.setContextClassLoader();
    intp.initializeSynchronous();

    completor = new SparkJLineCompletion(intp);

    sc = getSparkContext();
    if (sc.getPoolForName("fair").isEmpty()) {
      Value schedulingMode = org.apache.spark.scheduler.SchedulingMode.FAIR();
      int minimumShare = 0;
      int weight = 1;
      Pool pool = new Pool("fair", schedulingMode, minimumShare, weight);
      sc.taskScheduler().rootPool().addSchedulable(pool);
    }

    sqlc = getSQLContext();

    dep = getDependencyResolver();

    z = new ZeppelinContext(sc, sqlc, getHiveContext(), null, dep, printStream);

    this.interpreter.loadFiles(settings);

    intp.interpret("@transient var _binder = new java.util.HashMap[String, Object]()");
    binder = (Map<String, Object>) getValue("_binder");
    binder.put("sc", sc);
    binder.put("sqlc", sqlc);
    binder.put("hiveContext", getHiveContext());
    binder.put("z", z);
    binder.put("out", printStream);

    intp.interpret("@transient val z = "
                 + "_binder.get(\"z\").asInstanceOf[com.nflabs.zeppelin.spark.ZeppelinContext]");
    intp.interpret("@transient val sc = "
                 + "_binder.get(\"sc\").asInstanceOf[org.apache.spark.SparkContext]");
    intp.interpret("@transient val sqlc = "
                 + "_binder.get(\"sqlc\").asInstanceOf[org.apache.spark.sql.SQLContext]");
    intp.interpret("@transient val hiveContext = "
        + "_binder.get(\"hiveContext\").asInstanceOf[org.apache.spark.sql.hive.HiveContext]");
    intp.interpret("import org.apache.spark.SparkContext._");
    intp.interpret("import sqlc._");

    // add jar
    if (depInterpreter != null) {
      DependencyContext depc = depInterpreter.getDependencyContext();
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
    ScalaCompleter c = completor.completer();
    Candidates ret = c.complete(buf, cursor);
    return scala.collection.JavaConversions.asJavaList(ret.candidates());
  }

  @Override
  public void bindValue(String name, Object o) {
    if ("form".equals(name) && o instanceof Setting) { // form controller injection from
                                                       // Paragraph.jobRun
      z.setFormSetting((Setting) o);
    }
    getResultCode(intp.bindValue(name, o));
  }

  @Override
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

  private String getJobGroup(InterpreterContext context){
    return "zeppelin-" + this.hashCode() + "-" + context.getParagraph().getId();
  }

  /**
   * Interpret a single line.
   */
  @Override
  public InterpreterResult interpret(String line, InterpreterContext context) {
    z.setInterpreterContext(context);
    if (line == null || line.trim().length() == 0) {
      return new InterpreterResult(Code.SUCCESS);
    }
    return interpret(line.split("\n"), context);
  }

  public InterpreterResult interpret(String[] lines, InterpreterContext context) {
    synchronized (this) {
      sc.setJobGroup(getJobGroup(context), "Zeppelin", false);
      InterpreterResult r = interpretInput(lines);
      sc.clearJobGroup();
      return r;
    }
  }

  public InterpreterResult interpretInput(String[] lines) {
    SparkEnv.set(env);

    // add print("") to make sure not finishing with comment
    // see https://github.com/NFLabs/zeppelin/issues/151
    String[] linesToRun = new String[lines.length + 1];
    for (int i = 0; i < lines.length; i++) {
      linesToRun[i] = lines[i];
    }
    linesToRun[lines.length] = "print(\"\")";

    Console.setOut((java.io.PrintStream) binder.get("out"));
    out.reset();
    Code r = null;
    String incomplete = "";
    for (String s : linesToRun) {
      scala.tools.nsc.interpreter.Results.Result res = null;
      try {
        res = intp.interpret(incomplete + s);
      } catch (Exception e) {
        sc.clearJobGroup();
        logger.info("Interpreter exception", e);
        return new InterpreterResult(Code.ERROR, e.getMessage());
      }

      r = getResultCode(res);

      if (r == Code.ERROR) {
        sc.clearJobGroup();
        return new InterpreterResult(r, out.toString());
      } else if (r == Code.INCOMPLETE) {
        incomplete += s + "\n";
      } else {
        incomplete = "";
      }
    }

    if (r == Code.INCOMPLETE) {
      return new InterpreterResult(r, "Incomplete expression");
    } else {
      return new InterpreterResult(r, out.toString());
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
        if (sc.version().startsWith("1.0")) {
          progressInfo = getProgressFromStage_1_0x(sparkListener, job.finalStage());
        } else if (sc.version().startsWith("1.1")) {
          progressInfo = getProgressFromStage_1_1x(sparkListener, job.finalStage());
        } else if (sc.version().startsWith("1.2")) {
          progressInfo = getProgressFromStage_1_1x(sparkListener, job.finalStage());
        } else {
          continue;
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

  private int[] getProgressFromStage_1_0x(JobProgressListener sparkListener, Stage stage) {
    int numTasks = stage.numTasks();
    int completedTasks = 0;

    Method method;
    Object completedTaskInfo = null;
    try {
      method = sparkListener.getClass().getMethod("stageIdToTasksComplete");
      completedTaskInfo =
          JavaConversions.asJavaMap((HashMap<Object, Object>) method.invoke(sparkListener)).get(
              stage.id());
    } catch (NoSuchMethodException | SecurityException e) {
      logger.error("Error while getting progress", e);
    } catch (IllegalAccessException e) {
      logger.error("Error while getting progress", e);
    } catch (IllegalArgumentException e) {
      logger.error("Error while getting progress", e);
    } catch (InvocationTargetException e) {
      logger.error("Error while getting progress", e);
    }

    if (completedTaskInfo != null) {
      completedTasks += (int) completedTaskInfo;
    }
    List<Stage> parents = JavaConversions.asJavaList(stage.parents());
    if (parents != null) {
      for (Stage s : parents) {
        int[] p = getProgressFromStage_1_0x(sparkListener, s);
        numTasks += p[0];
        completedTasks += p[1];
      }
    }

    return new int[] {numTasks, completedTasks};
  }

  private int[] getProgressFromStage_1_1x(JobProgressListener sparkListener, Stage stage) {
    int numTasks = stage.numTasks();
    int completedTasks = 0;

    try {
      Method stageIdToData = sparkListener.getClass().getMethod("stageIdToData");
      HashMap<Tuple2<Object, Object>, Object> stageIdData =
          (HashMap<Tuple2<Object, Object>, Object>) stageIdToData.invoke(sparkListener);
      Class<?> stageUIDataClass =
          this.getClass().forName("org.apache.spark.ui.jobs.UIData$StageUIData");

      Method numCompletedTasks = stageUIDataClass.getMethod("numCompleteTasks");

      Set<Tuple2<Object, Object>> keys =
          JavaConverters.asJavaSetConverter(stageIdData.keySet()).asJava();
      for (Tuple2<Object, Object> k : keys) {
        if (stage.id() == (int) k._1()) {
          Object uiData = stageIdData.get(k).get();
          completedTasks += (int) numCompletedTasks.invoke(uiData);
        }
      }
    } catch (Exception e) {
      logger.error("Error on getting progress information", e);
    }

    List<Stage> parents = JavaConversions.asJavaList(stage.parents());
    if (parents != null) {
      for (Stage s : parents) {
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
    sc.stop();
    sc = null;

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
}
