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
import lombok.Getter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.cache.RemovalListener;
import com.google.common.cache.RemovalNotification;
import org.apache.spark.scheduler.DAGScheduler;
import org.apache.spark.scheduler.ActiveJob;
import com.google.common.base.Joiner;
import org.apache.spark.HttpServer;
import org.apache.spark.SparkConf;
import org.apache.spark.SparkContext;
import org.apache.spark.SparkEnv;
import org.apache.spark.repl.SparkCommandLine;
import org.apache.spark.repl.SparkILoop;
import org.apache.spark.repl.SparkIMain;
import org.apache.spark.repl.SparkJLineCompletion;
import org.apache.spark.scheduler.Pool;
import org.apache.spark.scheduler.Stage;
import org.apache.spark.sql.SQLContext;
import org.apache.spark.ui.jobs.JobProgressListener;
import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterGroup;
import org.apache.zeppelin.interpreter.InterpreterPropertyBuilder;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResult.Code;
import org.apache.zeppelin.interpreter.InterpreterUtils;
import org.apache.zeppelin.interpreter.WrappedInterpreter;
import org.apache.zeppelin.scheduler.Scheduler;
import org.apache.zeppelin.scheduler.SchedulerFactory;
import org.apache.zeppelin.spark.dep.DependencyContext;
import org.apache.zeppelin.spark.dep.DependencyResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import scala.Console;
import scala.Enumeration.Value;
import scala.None;
import scala.Some;
import scala.Tuple2;
import scala.collection.JavaConversions;
import scala.collection.JavaConverters;
import scala.collection.Iterator;
import scala.collection.mutable.HashMap;
import scala.collection.mutable.HashSet;
import scala.tools.nsc.Settings;
import scala.tools.nsc.interpreter.Completion;
import scala.tools.nsc.settings.MutableSettings.BooleanSetting;
import scala.tools.nsc.settings.MutableSettings.PathSetting;

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
            .add("zeppelin.spark.useHiveContext",
                getSystemDefault("ZEPPELIN_SPARK_USEHIVECONTEXT",
                    "zeppelin.spark.useHiveContext", "true"),
                "Use HiveContext instead of SQLContext if it is true.")
            .add("zeppelin.spark.maxResult",
                getSystemDefault("ZEPPELIN_SPARK_MAXRESULT", "zeppelin.spark.maxResult", "1000"),
                "Max number of SparkSQL result to display.")
            .add("args", "", "spark commandline args").build());

  }

    private Integer m_maxThreads;
    private Integer sparkInterpreterContexTTLHours;
    private Integer outputStreamMaxBytes;
    static final String ZEPPELIN_SPARK_OUTPUT_MAX_SIZE = "zeppelin.spark.outputStreamMaxSize";
    static final String ZEPPELIN_SPARK_CONCURRENT_SESSIONS = "zeppelin.spark.maxThreads";
    static final String ZEPPELIN_SPARK_INTERPRETER_CONTEXT_TTL_HOURS = "zeppelin.spark.InterpreterContexTTLHours";
    private SparkInterpreterContext initialSparkInterpreterContext;
    private Settings settings=null;
    private SparkContext sc;
    private SQLContext sqlc;
    private LoadingCache<String, SparkInterpreterContext> sparkInterpreterContextMap; // it holds multiple interpreters to support multi user, key-> notebook Id
    private DependencyResolver dep;
    private JobProgressListener sparkListener;
    private ConcurrentHashMap<String,ParagraphContext> paragraphContextMap;


    private SparkEnv env;

    public SparkInterpreter(Properties property) {
        super(property);
        getConfigs(property);

        sparkInterpreterContextMap =  CacheBuilder.newBuilder()
                .expireAfterAccess(sparkInterpreterContexTTLHours, TimeUnit.HOURS)
                .removalListener(SparkInterpreterContextRemovalListener)
                .build(SparkInterpreterContextLoader);
        paragraphContextMap =new ConcurrentHashMap<>();

    }

    //todo : remove this function, however if it's really used, then need to think..
    public SparkInterpreter(Properties property, SparkContext sc) {
        this(property);
        getConfigs(property);
        sparkInterpreterContextMap =  CacheBuilder.newBuilder()
                .expireAfterAccess(sparkInterpreterContexTTLHours, TimeUnit.HOURS)
                .removalListener(SparkInterpreterContextRemovalListener)
                .build(SparkInterpreterContextLoader);

        this.sc = sc;
        env = SparkEnv.get();
        sparkListener = setupListeners(this.sc);


    }


    private class SparkInterpreterContext {

        SparkInterpreterContext(){
            out=new ByteArrayOutputStream();
            printStream=new PrintStream(this.out);

            /* spark interpreter */
            interpreter = new SparkILoop(null, new PrintWriter(this.out));
            interpreter.settings_$eq(getSettings());

            interpreter.createInterpreter();

            intp = interpreter.intp();
            intp.setContextClassLoader();
            intp.initializeSynchronous();

        }



        @Getter private ZeppelinContext z;
        @Getter private SparkILoop interpreter;
        @Getter private SparkIMain intp;
        @Getter private ByteArrayOutputStream out;
        @Getter private PrintStream printStream;
        @Getter private SparkJLineCompletion completor;

        private SparkEnv env;

        java.util.HashMap<String, Object> binder;


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


        public void init() { // todo: send SparkContext as a parameter
            // sc is the variable of class .. should we send it as parama .. sqlc nad others too

            completor = new SparkJLineCompletion(intp);

            dep = getDependencyResolver();
            z = new ZeppelinContext(sc, sqlc, null, dep, printStream,
                    Integer.parseInt(getProperty("zeppelin.spark.maxResult")));

            // setting up the binder
            intp.interpret("@transient var _binder = new java.util.HashMap[String, Object]()");
            Object ret = intp.valueOfTerm("_binder");
            if (ret instanceof None) {
                binder= null;
            } else if (ret instanceof Some) {
                binder= (java.util.HashMap<String, Object>) ((Some) ret).get();
            } else {
                binder= (java.util.HashMap<String, Object>) ret;
            }

            binder.put("sc", sc);
            binder.put("sqlc", sqlc);

            binder.put("z", z);
            binder.put("out", printStream);


            intp.interpret("@transient val z = "
                    + "_binder.get(\"z\").asInstanceOf[org.apache.zeppelin.spark.ZeppelinContext]");
            intp.interpret("@transient val sc = "
                    + "_binder.get(\"sc\").asInstanceOf[org.apache.spark.SparkContext]");
            intp.interpret("@transient val sqlc = "
                    + "_binder.get(\"sqlc\").asInstanceOf[org.apache.spark.sql.SQLContext]");
            intp.interpret("@transient val sqlContext = "
                    + "_binder.get(\"sqlc\").asInstanceOf[org.apache.spark.sql.SQLContext]");
            intp.interpret("import org.apache.spark.SparkContext._");

            if (sc.version().startsWith("1.1")) {
                intp.interpret("import sqlContext._");
            } else if (sc.version().startsWith("1.2")) {
                intp.interpret("import sqlContext._");
            } else if (sc.version().startsWith("1.3")) {
                intp.interpret("import sqlContext.implicits._");
                intp.interpret("import sqlContext.sql");
                intp.interpret("import org.apache.spark.sql.functions._");
            } else if (sc.version().startsWith("1.4")) {
                intp.interpret("import sqlContext.implicits._");
                intp.interpret("import sqlContext.sql");
                intp.interpret("import org.apache.spark.sql.functions._");
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
                if (sc.version().startsWith("1.1") || sc.version().startsWith("1.2")) {
                    Method loadFiles = this.interpreter.getClass().getMethod("loadFiles", Settings.class);
                    loadFiles.invoke(this.interpreter, getSettings());
                } else if (sc.version().startsWith("1.3")) {
                    Method loadFiles = this.interpreter.getClass().getMethod(
                            "org$apache$spark$repl$SparkILoop$$loadFiles", Settings.class);
                    loadFiles.invoke(this.interpreter, getSettings());
                } else if (sc.version().startsWith("1.4")) {
                    Method loadFiles = this.interpreter.getClass().getMethod(
                            "org$apache$spark$repl$SparkILoop$$loadFiles", Settings.class);
                    loadFiles.invoke(this.interpreter, getSettings());
                }
            } catch (NoSuchMethodException | SecurityException | IllegalAccessException
                    | IllegalArgumentException | InvocationTargetException e) {
                throw new InterpreterException(e);
            }


        }


    }


    private static class ParagraphContext{
        private  final int  totalLines;
        private int completedLines;
        private boolean closed ;
        ParagraphContext(int totalLines){
            this.totalLines=totalLines;
            completedLines=0;
            closed=false;
        }

        public int getCompletedLines() {
            return completedLines;
        }

        public int getTotalLines() {
            return totalLines;
        }
        synchronized public int increaseCompletedLines() {
            return completedLines++;
        }

        synchronized public void setClosed(boolean closed){
            this.closed=closed;
        }
        public boolean isclosed(){return closed;}
    }

    public synchronized SparkContext getSparkContext() {
        if (sc == null) {
            sc = createSparkContext();
            env = SparkEnv.get();
            sparkListener = setupListeners(sc);

        }
        return sc;
    }

    public SparkContext createSparkContext() {
        System.err.println("------ Create new SparkContext " + getProperty("master") + " -------");

        String execUri = System.getenv("SPARK_EXECUTOR_URI");
        String[] jars = SparkILoop.getAddedJars();

        String classServerUri = null;


        try { // in case of spark 1.1x, spark 1.2x
            Method classServer = initialSparkInterpreterContext.getInterpreter().intp().getClass().getMethod("classServer");
            HttpServer httpServer = (HttpServer) classServer.invoke(initialSparkInterpreterContext.getInterpreter().intp());
            classServerUri = httpServer.uri();
        } catch (NoSuchMethodException | SecurityException | IllegalAccessException
                | IllegalArgumentException | InvocationTargetException e) {
            // continue
        }

        if (classServerUri == null) {
            try { // for spark 1.3x
                Method classServer = initialSparkInterpreterContext.getInterpreter().intp().getClass().getMethod("classServerUri");
                classServerUri = (String) classServer.invoke(initialSparkInterpreterContext.getInterpreter().intp());
            } catch (NoSuchMethodException | SecurityException | IllegalAccessException
                    | IllegalArgumentException | InvocationTargetException e) {
                throw new InterpreterException(e);
            }
        }


        logger.info("classServerUri attached with spark context=  "+ classServerUri);
        SparkConf conf =
                new SparkConf()
                        .setMaster(getProperty("master"))
                        .setAppName(getProperty("spark.app.name"))
                        .set("spark.repl.class.uri", classServerUri);


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

        String pysparkBasePath = getSystemDefault("SPARK_HOME", "spark.home", null);
        File pysparkPath;
        if (null == pysparkBasePath) {
            pysparkBasePath = getSystemDefault("ZEPPELIN_HOME", "zeppelin.home", "../");
            pysparkPath = new File(pysparkBasePath,
                    "interpreter" + File.separator + "spark" + File.separator + "pyspark");
        } else {
            pysparkPath = new File(pysparkBasePath,
                    "python" + File.separator + "lib");
        }

        String[] pythonLibs = new String[]{"pyspark.zip", "py4j-0.8.2.1-src.zip"};
        ArrayList<String> pythonLibUris = new ArrayList<>();
        for (String lib : pythonLibs) {
            File libFile = new File(pysparkPath, lib);
            if (libFile.exists()) {
                pythonLibUris.add(libFile.toURI().toString());
            }
        }
        pythonLibUris.trimToSize();
        if (pythonLibs.length == pythonLibUris.size()) {
            conf.set("spark.yarn.dist.files", Joiner.on(",").join(pythonLibUris));
            conf.set("spark.files", conf.get("spark.yarn.dist.files"));
            conf.set("spark.submit.pyArchives", Joiner.on(":").join(pythonLibs));
        }

        SparkContext sparkContext = new SparkContext(conf);

        // setting fair scheduling
        if (sparkContext.getPoolForName("fair").isEmpty()) {
            Value schedulingMode = org.apache.spark.scheduler.SchedulingMode.FAIR();
            int minimumShare = 0;
            int weight = 1;
            Pool pool = new Pool("fair", schedulingMode, minimumShare, weight);
            sparkContext.taskScheduler().rootPool().addSchedulable(pool);
        }


        // add jar
        DepInterpreter depInterpreter = getDepInterpreter();
        if (depInterpreter != null) {
            DependencyContext depc = depInterpreter.getDependencyContext();
            if (depc != null) {
                List<File> files = depc.getFilesDist();
                if (files != null) {
                    for (File f : files) {
                        if (f.getName().toLowerCase().endsWith(".jar")) {
                            sparkContext.addJar(f.getAbsolutePath());
                            logger.info("sc.addJar(" + f.getAbsolutePath() + ")");
                        } else {
                            sparkContext.addFile(f.getAbsolutePath());
                            logger.info("sc.addFile(" + f.getAbsolutePath() + ")");
                        }
                    }
                }
            }
        }

logger.info("SparkContext created ");
        return sparkContext;
    }

    final String toString(Object o) {
        return (o instanceof String) ? (String) o : "";
    }






  public boolean isSparkContextInitialized() {
    return sc != null;
  }

  private static JobProgressListener setupListeners(SparkContext context) {
    JobProgressListener pl = new JobProgressListener(context.getConf());
    context.listenerBus().addListener(pl);
    return pl;
  }

  private boolean useHiveContext() {
    return Boolean.parseBoolean(getProperty("zeppelin.spark.useHiveContext"));
  }

  public SQLContext getSQLContext() {
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



  private DepInterpreter getDepInterpreter() {
    InterpreterGroup intpGroup = getInterpreterGroup();
    if (intpGroup == null) return null;
    synchronized (intpGroup) {
      for (Interpreter intp : intpGroup) {
        if (intp.getClassName().equals(DepInterpreter.class.getName())) {
          Interpreter p = intp;
          while (p instanceof WrappedInterpreter) {
            p = ((WrappedInterpreter) p).getInnerInterpreter();
          }
          return (DepInterpreter) p;
        }
      }
    }
    return null;
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

  @Override
  public void open() {

      initialSparkInterpreterContext = new SparkInterpreterContext();
      sc=getSparkContext();
      sqlc=getSQLContext();
      initialSparkInterpreterContext.init();


  }

    synchronized  private  Settings getSettings() {
        if(settings==null) {
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
             settings = new Settings();
            if (getProperty("args") != null) {
                String[] argsArray = getProperty("args").split(" ");
                LinkedList<String> argList = new LinkedList<String>();
                for (String arg : argsArray) {
                    argList.add(arg);
                }

                SparkCommandLine command =
                        new SparkCommandLine(JavaConversions.asScalaBuffer(
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
        }

        return settings;
    }


    @Override
  public List<String> completion(String buf, int cursor, InterpreterContext context) {
        logger.info("TEST Completion pid="+ context.getParagraphId() + "  nid= "+context.getNoteId());


        Completion.ScalaCompleter c = null;
        try {
            c = sparkInterpreterContextMap.get(context.getNoteId()).getCompletor().completer();
        } catch (ExecutionException e) {
            logger.error("sparkInterpreterContextMap ExecutionException" ,e);

        }
        Completion.Candidates ret = c.complete(buf, cursor);
    return scala.collection.JavaConversions.asJavaList(ret.candidates());

  }



  String getJobGroup(org.apache.zeppelin.interpreter.InterpreterContext context){
    return "zeppelin-" + context.getParagraphId();
  }


    CacheLoader<String, SparkInterpreterContext> SparkInterpreterContextLoader =  new CacheLoader<String, SparkInterpreterContext>() {
        public SparkInterpreterContext load(String key) {
            return createSparkInterpreterContext(key);
        }
    };

    RemovalListener<String,SparkInterpreterContext> SparkInterpreterContextRemovalListener=new RemovalListener<String,SparkInterpreterContext> (){
        @Override
        public void onRemoval(RemovalNotification<String,SparkInterpreterContext> removalNotification) {
            logger.info("removed Key =" + removalNotification.getKey() + "due to " + removalNotification.getCause());
        }
    } ;




    public SparkInterpreterContext createSparkInterpreterContext(String key){
    synchronized (this) {
        System.setProperty("scala.repl.name.line", "line" + key.replaceAll("-|_", "") + "$");

        SparkInterpreterContext ic = null;
                ic = new SparkInterpreterContext();
                ic.init();

        // setting the class server
        Object classServer=null;
        try { // for spark 1.3x

            Method getclassServer =SparkIMain.class.getMethod("getClassServer");
            classServer =  getclassServer.invoke(initialSparkInterpreterContext.getIntp());
        } catch (NoSuchMethodException | SecurityException | IllegalAccessException
                | IllegalArgumentException | InvocationTargetException e) {
            logger.error("Not able to get the classServer from initialSparkInterpreterContext",e);
            throw new InterpreterException(e);
        }
if(classServer!=null) {
    try { // for spark 1.3x
        Method setClassServer = SparkIMain.class.getMethod("setClassServer",org.apache.spark.HttpServer.class);
        setClassServer.invoke(ic.getIntp(),classServer);
    } catch (NoSuchMethodException | SecurityException | IllegalAccessException
            | IllegalArgumentException | InvocationTargetException e) {
        logger.error("Not Able to set the classServer !! ",e);

        throw new InterpreterException(e);
    }
}else{
    logger.error(" classServer = null ! ");
     }


        return ic;
        }
    }


    /**
   * Interpret a single line.
   */
  @Override
  public InterpreterResult interpret(String line, org.apache.zeppelin.interpreter.InterpreterContext context) {
      try {
          sparkInterpreterContextMap.get(context.getNoteId()).getZ().setInterpreterContext(context);

      } catch (ExecutionException e) {
     logger.info("NoteId:"+context.getNoteId() + " ParagraphId: "+ context.getParagraphId()+" unable to setInterpreterContext, failed to get SparkInterpreterContext due to : "+ e.getCause(),e);

      }

      if (line == null || line.trim().length() == 0) {
      return new InterpreterResult(Code.SUCCESS);
    }
    return interpret(line.split("\n"), context);
  }

  public InterpreterResult interpret(String[] lines, InterpreterContext context) {


      try {
          sparkInterpreterContextMap.get(context.getNoteId()).getZ().setGui(context.getGui());
      } catch (ExecutionException e) {
          logger.info("NoteId:"+context.getNoteId() + " ParagraphId: "+ context.getParagraphId()+" unable to setGui, failed to get SparkInterpreterContext due to : "+ e.getCause(),e);
      }

      sc.setJobGroup(getJobGroup(context), "Zeppelin", false);
      InterpreterResult r = null;
      try {
          r = interpretInput(lines, context);
      } catch (ExecutionException e) {
          e.printStackTrace();
      }
      sc.clearJobGroup();
      return r;

  }


  public InterpreterResult interpretInput(String[] lines, org.apache.zeppelin.interpreter.InterpreterContext context) throws ExecutionException {
    SparkEnv.set(env);


    // add print("") to make sure not finishing with comment
    // see https://github.com/NFLabs/zeppelin/issues/151
    String[] linesToRun = new String[lines.length + 1];
    for (int i = 0; i < lines.length; i++) {
      linesToRun[i] = lines[i];
    }
    linesToRun[lines.length] = "print(\"\")";


    Console.setOut( sparkInterpreterContextMap.get(context.getNoteId()).printStream);
      ByteArrayOutputStream out= sparkInterpreterContextMap.get(context.getNoteId()).getOut();
      SparkIMain intp= sparkInterpreterContextMap.get(context.getNoteId()).getIntp();
      paragraphContextMap.put(context.getParagraphId(),new ParagraphContext(linesToRun.length));
    out.reset();
    Code r = null;
    String incomplete = "";
    for (String s : linesToRun) {
        if(paragraphContextMap.get(context.getParagraphId()).isclosed()){
            return new InterpreterResult(Code.SUCCESS, "closed !!");
        }


      scala.tools.nsc.interpreter.Results.Result res = null;
      try {
        res = intp.interpret(incomplete + s);
      } catch (Exception e) {
        sc.clearJobGroup();
        logger.info("Interpreter exception", e);
        return new InterpreterResult(Code.ERROR, InterpreterUtils.getMostRelevantMessage(e));
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
        paragraphContextMap.get(context.getParagraphId()).increaseCompletedLines();
    }

    if (r == Code.INCOMPLETE) {
      return new InterpreterResult(r, "Incomplete expression");
    } else {
       if(out.size() > outputStreamMaxBytes ){
        String truncatedOut= out.toString().substring(0,outputStreamMaxBytes).concat("\nOutput truncated after "+outputStreamMaxBytes+" bytes");
            return new InterpreterResult(Code.SUCCESS, truncatedOut);
        }

      return new InterpreterResult(r, out.toString()); // add the check for size..
        
    }
  }


  @Override
  public void cancel(org.apache.zeppelin.interpreter.InterpreterContext context) {
    sc.cancelJobGroup(getJobGroup(context));
     if(paragraphContextMap.containsKey(context.getParagraphId()))
         paragraphContextMap.get(context.getParagraphId()).setClosed(true);
  }

  @Override
  public int getProgress(org.apache.zeppelin.interpreter.InterpreterContext context) {

      double completedLines=0;
      double totalLines =0;

      if(paragraphContextMap.containsKey(context.getParagraphId())) {
          completedLines = paragraphContextMap.get(context.getParagraphId()).getCompletedLines();
          totalLines = paragraphContextMap.get(context.getParagraphId()).getTotalLines();

      }
      if(totalLines==0)return 0;
      return (int)(((completedLines + (getProgressOfJob( context ))) / totalLines) * 100);

  }

  public double getProgressOfJob(org.apache.zeppelin.interpreter.InterpreterContext context) {
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
    Iterator<ActiveJob> it =  jobs.iterator();
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
        } else if (sc.version().startsWith("1.3")) {
          progressInfo = getProgressFromStage_1_1x(sparkListener, job.finalStage());
        } else if (sc.version().startsWith("1.4")) {
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
    return (double)completedTasks  / (double)totalTasks;

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
     for(SparkInterpreterContext context : sparkInterpreterContextMap.asMap().values() ){
          context.getIntp().close();
      }
    initialSparkInterpreterContext.getIntp().close();
  }

  @Override
  public FormType getFormType() {
    return FormType.NATIVE;
  }

  public JobProgressListener getJobProgressListener() {

    return sparkListener;
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

    public ZeppelinContext getZeppelinContext(String key) {
        try {
            return sparkInterpreterContextMap.get(key).getZ();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
return null;
    }
    public ZeppelinContext getZeppelinContext() {
        return initialSparkInterpreterContext.getZ();

    }
    public DependencyResolver getDependencyResolver() {
        if (dep == null) {
            dep = new DependencyResolver(initialSparkInterpreterContext.getIntp(), sc, getProperty("zeppelin.dep.localrepo"));
        }
        return dep;
    }


    @Override
    public Scheduler getScheduler() {
        return SchedulerFactory.singleton().createOrGetParallelScheduler(
                SparkInterpreter.class.getName() + this.hashCode(), m_maxThreads);
    }
    public static int toBytes(String size) {
        int returnValue = -1;
        Pattern patt = Pattern.compile("([\\d.]+)([GMK]B)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = patt.matcher(size);
        if(matcher.groupCount()!=2){

            return -1;
        }
        Map<String, Integer> powerMap = new java.util.HashMap<String, Integer>();
        powerMap.put("GB", 3);
        powerMap.put("MB", 2);
        powerMap.put("KB", 1);



        if (matcher.find()) {
            String number = matcher.group(1);
            int pow = 0;
            if(powerMap.containsKey(matcher.group(2).toUpperCase()))
                pow=powerMap.get(matcher.group(2).toUpperCase());

            Double bytes = new Double(number);
            bytes = bytes * (int)(Math.pow(1024,pow));
            returnValue = bytes.intValue();
        }
        return returnValue;
    }
    private void getConfigs(Properties properties) {
        try{
            m_maxThreads = Integer.parseInt(properties.getProperty(ZEPPELIN_SPARK_CONCURRENT_SESSIONS, "10"));
            logger.info("got value for "+ZEPPELIN_SPARK_CONCURRENT_SESSIONS+" : "+m_maxThreads);
        }catch(Exception e){
            logger.error("error while getting value of "+ZEPPELIN_SPARK_CONCURRENT_SESSIONS+" setting default value of 10" , e);
            m_maxThreads=10;
        }
        try{
            sparkInterpreterContexTTLHours = Integer.parseInt(properties.getProperty(ZEPPELIN_SPARK_INTERPRETER_CONTEXT_TTL_HOURS,"24"));
            logger.info("got value for "+ZEPPELIN_SPARK_INTERPRETER_CONTEXT_TTL_HOURS+" : "+ sparkInterpreterContexTTLHours);
        }catch(Exception e){
            logger.error("error while getting value of "+ZEPPELIN_SPARK_INTERPRETER_CONTEXT_TTL_HOURS+" setting default value of 24" , e);
            sparkInterpreterContexTTLHours =24;
        }


        try{
            String strOutputMaxSize = properties.getProperty(ZEPPELIN_SPARK_OUTPUT_MAX_SIZE,"1mb");
            outputStreamMaxBytes =toBytes(strOutputMaxSize);
            logger.info("got value for "+ ZEPPELIN_SPARK_OUTPUT_MAX_SIZE +" : "+ outputStreamMaxBytes);
        }catch(Exception e){
            logger.error("error while getting value of "+ ZEPPELIN_SPARK_OUTPUT_MAX_SIZE +" setting default value of 1048576" , e);
            outputStreamMaxBytes =1048576; // 1mb
        }

    }

}
