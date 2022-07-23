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

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.mongodb.spark.MongoSpark;
import com.mongodb.spark.config.ReadConfig;
import javaslang.Tuple3;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.http.auth.InvalidCredentialsException;
import org.apache.spark.SparkContext;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.sql.AnalysisException;
import org.apache.spark.sql.SQLContext;
import org.apache.spark.sql.SparkSession;
import org.apache.zeppelin.interpreter.AbstractInterpreter;
import org.apache.zeppelin.interpreter.ZeppelinContext;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResult.Code;
import org.apache.zeppelin.interpreter.util.SqlSplitter;
import org.apache.zeppelin.scheduler.Scheduler;
import org.apache.zeppelin.scheduler.SchedulerFactory;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.Type;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Spark SQL interpreter for Zeppelin.
 */
public class SparkSqlInterpreter extends AbstractInterpreter {
  private static final Logger LOGGER = LoggerFactory.getLogger(SparkSqlInterpreter.class);

  private SparkInterpreter sparkInterpreter;
  private SqlSplitter sqlSplitter;

  public SparkSqlInterpreter(Properties property) {
    super(property);
  }

  @Override
  public void open() throws InterpreterException {
    this.sparkInterpreter = getInterpreterInTheSameSessionByClassName(SparkInterpreter.class);
    this.sqlSplitter = new SqlSplitter();
  }

  private boolean concurrentSQL() {
    return Boolean.parseBoolean(getProperty("zeppelin.spark.concurrentSQL"));
  }

  @Override
  public void close() {}

  @Override
  protected boolean isInterpolate() {
    return Boolean.parseBoolean(getProperty("zeppelin.spark.sql.interpolation", "false"));
  }

  @Override
  public ZeppelinContext getZeppelinContext() {
    return null;
  }

  @Override
  public InterpreterResult internalInterpret(String st, InterpreterContext context)
      throws InterpreterException {
    if (sparkInterpreter.isUnsupportedSparkVersion()) {
      return new InterpreterResult(Code.ERROR, "Spark "
          + sparkInterpreter.getSparkVersion().toString() + " is not supported");
    }
    Utils.printDeprecateMessage(sparkInterpreter.getSparkVersion(), context, properties);
    sparkInterpreter.getZeppelinContext().setInterpreterContext(context);
    SQLContext sqlContext = sparkInterpreter.getSQLContext();
    SparkContext sc = sparkInterpreter.getSparkContext();

    try {
      st = enableCrossDatabaseQuerySupport(st,context);
    } catch (Exception e) {
      try{
        LOGGER.error("Can not enable cross datasource query support:",e);
        context.out.write(e.toString());
        e.printStackTrace();
        context.out.flush();
        return new InterpreterResult(Code.ERROR);
      }catch(IOException ex) {
        LOGGER.error("Fail to write output:", ex);
        return new InterpreterResult(Code.ERROR);
      }

    }

    List<String> sqls = sqlSplitter.splitSql(st);
    int maxResult = Integer.parseInt(context.getLocalProperties().getOrDefault("limit",
            "" + sparkInterpreter.getZeppelinContext().getMaxResult()));

    sc.setLocalProperty("spark.scheduler.pool", context.getLocalProperties().get("pool"));
    sc.setJobGroup(Utils.buildJobGroupId(context), Utils.buildJobDesc(context), false);
    String curSql = null;
    ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
    try {
      Thread.currentThread().setContextClassLoader(sparkInterpreter.getScalaShellClassLoader());
      for (String sql : sqls) {
        curSql = sql;
        String result = sparkInterpreter.getZeppelinContext()
                .showData(sqlContext.sql(sql), maxResult);
        context.out.write(result);
      }
      context.out.flush();
    } catch (Exception e) {
      try {
        if (e.getCause() instanceof AnalysisException) {
          // just return the error message from spark if it is AnalysisException
          context.out.write(e.getCause().getMessage());
          context.out.flush();
          return new InterpreterResult(Code.ERROR);
        } else {
          LOGGER.error("Error happens in sql: {}", curSql, e);
          context.out.write("\nError happens in sql: " + curSql + "\n");
          if (Boolean.parseBoolean(getProperty("zeppelin.spark.sql.stacktrace", "false"))) {
            if (e.getCause() != null) {
              context.out.write(ExceptionUtils.getStackTrace(e.getCause()));
            } else {
              context.out.write(ExceptionUtils.getStackTrace(e));
            }
          } else {
            StringBuilder msgBuilder = new StringBuilder();
            if (e.getCause() != null) {
              msgBuilder.append(e.getCause().getMessage());
            } else {
              msgBuilder.append(e.getMessage());
            }
            msgBuilder.append("\nset zeppelin.spark.sql.stacktrace = true to see full stacktrace");
            context.out.write(msgBuilder.toString());
          }
          context.out.flush();
          return new InterpreterResult(Code.ERROR);
        }
      } catch (IOException ex) {
        LOGGER.error("Fail to write output", ex);
        return new InterpreterResult(Code.ERROR);
      }
    } finally {
      sc.clearJobGroup();
      Thread.currentThread().setContextClassLoader(originalClassLoader);
    }

    return new InterpreterResult(Code.SUCCESS);
  }


  /**
   * inject jdbc/mongodb table to spark sql session
   * table should be declared in format : interpreterName.dbName.tableName
   * @param st query string
   * @param context zeppelin context
   * @return new query string
   */
  public String enableCrossDatabaseQuerySupport(String st, InterpreterContext context) throws IOException, InvalidCredentialsException {
    AuthenticationInfo authenticationInfo = context.getAuthenticationInfo();
    HashSet<String> usersAndRoles = new HashSet<>(authenticationInfo.getUsersAndRoles());
    Gson gson = new Gson();
    // try to load interpreter settings from
    Type listType = new TypeToken<ArrayList<InterSetting>>(){}.getType();
    String interSettingsStr = context.getLocalProperties().get("interpreterSettings");

    if(interSettingsStr == null || interSettingsStr.isEmpty()){
      context.out.write("\ncannot perform cross datasource query , reason : can not load interpreter settings" );
    }


    List<InterSetting> interSettings = gson.fromJson(context.getLocalProperties().get("interpreterSettings"),listType);
    // get spark session
    SparkSession session = (SparkSession) sparkInterpreter.getSparkSession();
    // try to get interpreter name and db_name and table_name
    Pattern pattern = Pattern.compile("([-_0-9a-zA-Z]*)\\.([-_0-9a-zA-Z]*)\\.([-_0-9a-zA-Z]*)");
    Matcher matcher = pattern.matcher(st);
    HashMap<String, Tuple3<String,String,String>> imap = new HashMap<>();

    while(matcher.find()){
      if(!imap.containsKey(matcher.group())){
        // the value of map is interpreter_id, db_name, table_name
        imap.put(matcher.group(), new Tuple3<>(matcher.group(1),matcher.group(2),matcher.group(3)));
      }
    }
    for(Tuple3<String,String,String> info:imap.values()){
      String interpreterId = info._1;
      String dbName = info._2;
      String tableName = info._3;
      InterSetting iSetting = getInterpreterFromList(interSettings,interpreterId).orElse(null);
      if(iSetting == null){
        throw new IOException(String.format("No such interpreter with id : %s", interpreterId));
      }
      // check if this user can access current interpreter
      HashSet<String> owners = new HashSet<>(iSetting.option.owners);
      // if owners is empty, means all users can access
      if(!owners.isEmpty()){
        int size1 = owners.size();
        owners.retainAll(usersAndRoles);
        int size2 = owners.size();
        if(size1 == size2){
          // no user or roles match
          throw new InvalidCredentialsException(String.format(String.format("user %s has not privilege to access interpreter %s",authenticationInfo.getUser(), interpreterId)));
        }
      }
      String iGroup = iSetting.group;

      String newTableName = String.format("%s_%s_%s", interpreterId,dbName,tableName).replace("-","_");
      // do inject
      if(iGroup.equals("jdbc")){
        String jdbcUrl = iSetting.getProp("default.url");
        String user = iSetting.getProp("default.user");
        String password =  iSetting.getProp("default.password");
        String driver =  iSetting.getProp("default.driver");
        Properties properties = new Properties();
        properties.setProperty("user", user);
        properties.setProperty("password", password);
        properties.setProperty("driver",driver);
        session.read()
                .jdbc(jdbcUrl, String.format("%s.%s", dbName,tableName),properties)
                .registerTempTable(newTableName);

      }else if (iGroup.equals("mongodb")){
        HashMap<String,String> mongoProps = new HashMap<>();
        String user =  iSetting.getProp("mongo.server.username","");
        String password =  iSetting.getProp("mongo.server.password","");
        String host = iSetting.getProp("mongo.server.host","127.0.0.1");
        String port = iSetting.getProp("mongo.server.port","27017");
        String authDb = iSetting.getProp("mongo.server.authenticationDatabase","");
        mongoProps.put("uri", String.format("mongodb://%s:%s@%s:%s/%s.%s?authSource=%s",user,password,host,port,dbName,tableName,authDb));
        mongoProps.put("collection",tableName);
        mongoProps.put("database",dbName);
        JavaSparkContext jsc = new JavaSparkContext(session.sparkContext());
        ReadConfig readConfig = ReadConfig.create(mongoProps);
        MongoSpark.builder().javaSparkContext(jsc).readConfig(readConfig).build().toJavaRDD().toDF().registerTempTable(newTableName);
      }
      else{
        throw new IOException(String.format("Unsupported interpreter: %s", interpreterId));
      }
      // replace table qualifier in sql str
      st = st.replaceAll(interpreterId+"\\."+dbName+"\\."+tableName,newTableName);

    }
    return st;


  }

  private Optional<InterSetting> getInterpreterFromList(List<InterSetting> interpreterSettings,String interpreterId){
    return interpreterSettings.stream().filter(interpreterSetting -> Objects.equals(interpreterSetting.id, interpreterId)).findFirst();
  }

  private class InterSetting implements Serializable {
    public String id;
    public String name;
    public String group;
    public Map<String,Prop> properties;
    public String status;
    public Opt option;

    public String getProp(String key){
      Prop prop =  properties.get(key);
      if(prop == null){
        return null;
      }
      return prop.value;
    }

    public String getProp(String key,String default_val){
      if (getProp(key) == null){
        return default_val;
      }else{
        return getProp(key);
      }
    }

    public class Prop implements Serializable{
      public String name;
      public String value;
      public String type;
      public String description;
    }

    private class Opt implements Serializable{
      public boolean remote;
      public int port;
      public String perNote;
      public String perUser;
      public boolean isExistingProcess;
      public boolean setPermission;
      public List<String> owners;
      public boolean isUserImpersonate;
    }

  }

  @Override
  public void cancel(InterpreterContext context) throws InterpreterException {
    SparkContext sc = sparkInterpreter.getSparkContext();
    sc.cancelJobGroup(Utils.buildJobGroupId(context));
  }

  @Override
  public FormType getFormType() {
    return FormType.SIMPLE;
  }

  @Override
  public int getProgress(InterpreterContext context) throws InterpreterException {
    return sparkInterpreter.getProgress(context);
  }

  @Override
  public Scheduler getScheduler() {
    if (concurrentSQL()) {
      int maxConcurrency = Integer.parseInt(getProperty("zeppelin.spark.concurrentSQL.max", "10"));
      return SchedulerFactory.singleton().createOrGetParallelScheduler(
          SparkSqlInterpreter.class.getName() + this.hashCode(), maxConcurrency);
    } else {
      // getSparkInterpreter() calls open() inside.
      // That means if SparkInterpreter is not opened, it'll wait until SparkInterpreter open.
      // In this moment UI displays 'READY' or 'FINISHED' instead of 'PENDING' or 'RUNNING'.
      // It's because of scheduler is not created yet, and scheduler is created by this function.
      // Therefore, we can still use getSparkInterpreter() here, but it's better and safe
      // to getSparkInterpreter without opening it.
      try {
        return getInterpreterInTheSameSessionByClassName(SparkInterpreter.class, false)
            .getScheduler();
      } catch (InterpreterException e) {
        throw new RuntimeException("Fail to getScheduler", e);
      }
    }
  }
}
