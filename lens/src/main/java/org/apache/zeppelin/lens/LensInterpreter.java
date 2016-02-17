/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.zeppelin.lens;

import java.util.List;
import java.util.Properties;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.io.ByteArrayOutputStream;

import org.apache.lens.client.LensClient;
import org.apache.lens.client.LensClientConfig;
import org.apache.lens.client.LensClientSingletonWrapper;
import org.apache.lens.cli.commands.BaseLensCommand;
import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterPropertyBuilder;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResult.Code;
import org.apache.zeppelin.scheduler.Scheduler;
import org.apache.zeppelin.scheduler.SchedulerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.shell.Bootstrap;
import org.springframework.shell.core.CommandResult;
import org.springframework.shell.core.JLineShell;
import org.springframework.shell.core.JLineShellComponent;
import org.springframework.shell.support.logging.HandlerUtils;


/**
 * Lens interpreter for Zeppelin.
 */
public class LensInterpreter extends Interpreter {

  static final Logger s_logger = LoggerFactory.getLogger(LensInterpreter.class);
  static final String LENS_CLIENT_DBNAME = "lens.client.dbname";
  static final String LENS_SERVER_URL = "lens.server.base.url";
  static final String LENS_SESSION_CLUSTER_USER = "lens.session.cluster.user";
  static final String LENS_PERSIST_RESULTSET = "lens.query.enable.persistent.resultset";
  static final String ZEPPELIN_LENS_RUN_CONCURRENT_SESSION = "zeppelin.lens.run.concurrent";
  static final String ZEPPELIN_LENS_CONCURRENT_SESSIONS = "zeppelin.lens.maxThreads";
  static final String ZEPPELIN_MAX_ROWS = "zeppelin.lens.maxResults";
  static final Map<String, Pattern> LENS_TABLE_FORMAT_REGEX = new LinkedHashMap<String, Pattern>() {
    {
      put("cubes", Pattern.compile(".*show\\s+cube.*"));
      put("nativetables", Pattern.compile(".*show\\s+nativetable.*"));
      put("storages", Pattern.compile(".*show\\s+storage.*"));
      put("facts", Pattern.compile(".*show\\s+fact.*"));
      put("dimensions", Pattern.compile(".*show\\s+dimension.*"));
      put("params", Pattern.compile(".*show\\s+param.*"));
      put("databases", Pattern.compile(".*show\\s+database.*"));
      put("query results", Pattern.compile(".*query\\s+results.*"));
    }
  };

  private static Pattern s_queryExecutePattern = Pattern.compile(".*query\\s+execute\\s+(.*)");
  private static Map<String, ExecutionDetail> s_paraToQH = 
    new ConcurrentHashMap<String, ExecutionDetail> (); //tracks paragraphID -> Lens QueryHandle
  private static Map<LensClient, Boolean> s_clientMap =
    new ConcurrentHashMap<LensClient, Boolean>();

  private int m_maxResults;
  private int m_maxThreads;
  private JLineShell m_shell;
  private LensClientConfig m_lensConf;
  private Bootstrap m_bs;
  private LensClient m_lensClient;
  

  static {
    Interpreter.register(
      "lens",
      "lens",
      LensInterpreter.class.getName(),
      new InterpreterPropertyBuilder()
        .add(ZEPPELIN_LENS_RUN_CONCURRENT_SESSION, "true", "Run concurrent Lens Sessions")
        .add(ZEPPELIN_LENS_CONCURRENT_SESSIONS, "10", 
          "If concurrency is true then how many threads?")
        .add(ZEPPELIN_MAX_ROWS, "1000", "max number of rows to display")
        .add(LENS_SERVER_URL, "http://<hostname>:<port>/lensapi", "The URL for Lens Server")
        .add(LENS_CLIENT_DBNAME, "default", "The database schema name")
        .add(LENS_PERSIST_RESULTSET, "false", "Apache Lens to persist result in HDFS?")
        .add(LENS_SESSION_CLUSTER_USER, "default", "Hadoop cluster username").build());
  }

  public LensInterpreter(Properties property) {
    super(property);
    try {
      m_lensConf = new LensClientConfig();
      m_lensConf.set(LENS_SERVER_URL, property.get(LENS_SERVER_URL).toString());
      m_lensConf.set(LENS_CLIENT_DBNAME, property.get(LENS_CLIENT_DBNAME).toString());
      m_lensConf.set(LENS_SESSION_CLUSTER_USER, property.get(LENS_SESSION_CLUSTER_USER).toString());
      m_lensConf.set(LENS_PERSIST_RESULTSET, property.get(LENS_PERSIST_RESULTSET).toString());
      try {
        m_maxResults = Integer.parseInt(property.get(ZEPPELIN_MAX_ROWS).toString());
      } catch (NumberFormatException|NullPointerException e) {
        m_maxResults = 1000;
        s_logger.error("unable to parse " + ZEPPELIN_MAX_ROWS + " :" 
          + property.get(ZEPPELIN_MAX_ROWS), e);
      }
      try {
        m_maxThreads = Integer.parseInt(property.get(ZEPPELIN_LENS_CONCURRENT_SESSIONS).toString());
      } catch (NumberFormatException|NullPointerException e) {
        m_maxThreads = 10;
        s_logger.error("unable to parse " + ZEPPELIN_LENS_CONCURRENT_SESSIONS + " :" 
            + property.get(ZEPPELIN_LENS_CONCURRENT_SESSIONS), e);
      }
      s_logger.info("LensInterpreter created");
    }
    catch (Exception e) {
      s_logger.error(e.toString(), e);
      s_logger.error("unable to create lens interpreter", e);
    }
  }

  private Bootstrap createBootstrap() {
    return new LensBootstrap();
  }

  private JLineShell getJLineShell(Bootstrap bs) {
    if (bs instanceof LensBootstrap) {
      return ((LensBootstrap) bs).getLensJLineShellComponent();
    } else {
      return bs.getJLineShellComponent();
    }
  }

  protected void init() {
    try {
      m_bs = createBootstrap();
      m_shell = getJLineShell(m_bs);
    } catch (Exception ex) {
      s_logger.error("could not initialize commandLine", ex);
    }
  }

  @Override
  public void open() {
    s_logger.info("LensInterpreter opening");
    m_lensClient = new LensClient(m_lensConf);
    LensClientSingletonWrapper.instance().setClient(m_lensClient);
    init();
    s_logger.info("LensInterpreter opened");
  }
  
  @Override
  public void close() {
    closeConnections();
    s_logger.info("LensInterpreter closed");
  }

  private static void closeConnections() {
    for (LensClient cl : s_clientMap.keySet()) {
      if (cl.isConnectionOpen()) {
        closeLensClient(cl);
      }
    }
  }

  private static void closeLensClient(LensClient lensClient) {
    try {
      lensClient.closeConnection();
    } catch (Exception e) {
      s_logger.error("unable to close lensClient", e);
    }
  }

  private LensClient createAndSetLensClient(Bootstrap bs) {
    LensClient lensClient = null;
    try {
      lensClient = new LensClient(m_lensConf);
      
      for (String beanName : bs.getApplicationContext().getBeanDefinitionNames()) {
        if (bs.getApplicationContext().getBean(beanName) instanceof BaseLensCommand) {
          ((BaseLensCommand) bs.getApplicationContext().getBean(beanName))
            .setClient(lensClient);
        }
      }
    } catch (Exception e) {
      s_logger.error("unable to create lens client", e);
      throw e;
    }
    return lensClient;
  }

  private InterpreterResult HandleHelp(JLineShell shell, String st) {
    java.util.logging.StreamHandler sh = null;
    java.util.logging.Logger springLogger = null;
    java.util.logging.Formatter formatter = new java.util.logging.Formatter() {
      public String format(java.util.logging.LogRecord record) {
        return record.getMessage();
      }
    };
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try {
      sh = new java.util.logging.StreamHandler(baos, formatter);
      springLogger = HandlerUtils.getLogger(org.springframework.shell.core.SimpleParser.class);
      springLogger.addHandler(sh);
      shell.executeCommand(st);
    } catch (Exception e) {
      s_logger.error(e.getMessage(), e);
      return new InterpreterResult(Code.ERROR, e.getMessage());
    }
    finally {
      sh.flush();
      springLogger.removeHandler(sh);
      sh.close();
    }
    return new InterpreterResult(Code.SUCCESS, baos.toString());
  }
  
  private String modifyQueryStatement(String st) {
    Matcher matcher = s_queryExecutePattern.matcher(st.toLowerCase());
    if (!matcher.find()) {
      return st;
    }
    StringBuilder sb = new StringBuilder("query execute ");
    if (!st.toLowerCase().matches(".*--async\\s+true")) {
      sb.append("--async true ");
    }
    sb.append(matcher.group(1));
    if (!st.toLowerCase().matches(".*limit\\s+\\d+.*")) {
      sb.append(" limit ");
      sb.append(m_maxResults);
    }
    return sb.toString();
  }

  @Override
  public InterpreterResult interpret(String input, InterpreterContext context) {
    if (input == null || input.length() == 0) {
      return new InterpreterResult(Code.ERROR, "no command submitted");
    }
    String st = input.replaceAll("\\n", " ");
    s_logger.info("LensInterpreter command: " + st);
    
    Bootstrap bs = createBootstrap();
    JLineShell  shell = getJLineShell(bs);
    CommandResult res = null;
    LensClient lensClient = null;
    String qh = null;
    
    if (st.trim().startsWith("help")) {
      return HandleHelp(shell, st);
    }
    
    try {
      lensClient = createAndSetLensClient(bs);
      s_clientMap.put(lensClient, true);
      
      String lensCommand = modifyQueryStatement(st);
      
      s_logger.info("executing command : " + lensCommand);
      res = shell.executeCommand(lensCommand);
      
      if (!lensCommand.equals(st) && res != null 
          && res.getResult() != null 
          && res.getResult().toString().trim().matches("[a-z0-9-]+")) {
        // setup query progress tracking
        qh = res.getResult().toString();
        s_paraToQH.put(context.getParagraphId(), 
          new ExecutionDetail(qh, lensClient, shell));
        String getResultsCmd = "query results --async false " + qh;
        s_logger.info("executing query results command : " + context.getParagraphId() 
          + " : " + getResultsCmd);
        res = shell.executeCommand(getResultsCmd); 
        s_paraToQH.remove(context.getParagraphId());
      }
    } catch (Exception ex) {
      s_logger.error("error in interpret", ex);
      return new InterpreterResult(Code.ERROR, ex.getMessage());
    } 
    finally {
      if (shell != null) {
        closeShell(shell);
      }
      if (lensClient != null) {
        closeLensClient(lensClient);
        s_clientMap.remove(lensClient);
      }
      if (qh != null) {
        s_paraToQH.remove(context.getParagraphId());
      }
    }
    return new InterpreterResult(Code.SUCCESS, formatResult(st, res));
  }
  
  private void closeShell(JLineShell shell) {
    if (shell instanceof LensJLineShellComponent) {
      ((LensJLineShellComponent) shell).stop();
    } else {
      ((JLineShellComponent) shell).stop();
    }
  }
  
  private String formatResult(String st, CommandResult result) {
    if (result == null) {
      return "error in interpret, no result object returned";
    }
    if (!result.isSuccess() || result.getResult() == null) {
      if (result.getException() != null) {
        return result.getException().getMessage();
        //try describe cube (without cube name)- error is written as a warning, 
        //but not returned to result object
      } else {
        return "error in interpret, unable to execute command";
      }
    }
    StringBuilder sb = new StringBuilder();
    for (Map.Entry<String, Pattern> entry : LENS_TABLE_FORMAT_REGEX.entrySet()) {
      if (entry.getValue().matcher(st.toLowerCase()).find()) {
        sb.append("%table " + entry.getKey() + " \n");
        break;
      }
    }
    if (s_queryExecutePattern.matcher(st.toLowerCase()).find() &&
      result.getResult().toString().contains(" rows process in (")) {
      sb.append("%table ");
    }
    if (sb.length() > 0) {
      return sb.append(result.getResult().toString()).toString();
    }
    return result.getResult().toString();
    //Lens sends error messages without setting result.isSuccess() = false.
  }

  @Override
  public void cancel(InterpreterContext context) {
    if (!s_paraToQH.containsKey(context.getParagraphId())) {
      s_logger.error("ignoring cancel from " + context.getParagraphId());
      return;
    }
    String qh = s_paraToQH.get(context.getParagraphId()).getQueryHandle();
    s_logger.info("preparing to cancel : (" + context.getParagraphId() + ") :" + qh);
    Bootstrap bs = createBootstrap();
    JLineShell shell = getJLineShell(bs);
    LensClient lensClient = null;
    try {
      lensClient = createAndSetLensClient(bs);
      s_clientMap.put(lensClient, true);
      s_logger.info("invoke query kill (" + context.getParagraphId() + ") " + qh);
      CommandResult res = shell.executeCommand("query kill " + qh);
      s_logger.info("query kill returned (" + context.getParagraphId() + ") " + qh 
        + " with: " + res.getResult());
    } catch (Exception e) {
      s_logger.error("unable to kill query ("
        + context.getParagraphId() + ") " + qh, e);
    } finally {
      try {
        if (lensClient != null) {
          closeLensClient(lensClient);
          s_clientMap.remove(lensClient);
        }
        closeLensClient(s_paraToQH.get(context.getParagraphId()).getLensClient());
        closeShell(s_paraToQH.get(context.getParagraphId()).getShell());
      } catch (Exception e) {
        // ignore
        s_logger.info("Exception in LensInterpreter while cancel finally, ignore", e);
      }
      s_paraToQH.remove(context.getParagraphId());
      closeShell(shell);
    }
  }

  @Override
  public FormType getFormType() {
    return FormType.SIMPLE;
  }

  @Override
  public int getProgress(InterpreterContext context) {
    if (s_paraToQH.containsKey(context.getParagraphId())) {
      s_logger.info("number of items for which progress can be reported :" + s_paraToQH.size());
      s_logger.info("number of open lensclient :" + s_clientMap.size());
      Bootstrap bs = createBootstrap();
      JLineShell shell = getJLineShell(bs);
      LensClient lensClient = null;
      String qh = s_paraToQH.get(context.getParagraphId()).getQueryHandle();
      try {
        s_logger.info("fetch query status for : (" + context.getParagraphId() + ") :" + qh);
        lensClient = createAndSetLensClient(bs);
        s_clientMap.put(lensClient, true);
        CommandResult res = shell.executeCommand("query status " + qh);
        s_logger.info(context.getParagraphId() + " --> " + res.getResult().toString());
        //change to debug
        Pattern pattern = Pattern.compile(".*(Progress : (\\d\\.\\d)).*");
        Matcher matcher = pattern.matcher(res.getResult().toString().replaceAll("\\n", " "));
        if (matcher.find(2)) {
          Double d = Double.parseDouble(matcher.group(2)) * 100;
          if (d.intValue() == 100) {
            s_paraToQH.remove(context.getParagraphId());
          }
          return d.intValue();
        } else {
          return 1;
        }
      }
      catch (Exception e) {
        s_logger.error("unable to get progress for (" + context.getParagraphId() + ") :" + qh, e);
        s_paraToQH.remove(context.getParagraphId());
        return 0;
      } finally {
        if (lensClient != null) {
          closeLensClient(lensClient);
          s_clientMap.remove(lensClient);
        }
        if (shell != null) {
          closeShell(shell);
        }
      }
    }
    return 0;
  }

  @Override
  public List<String> completion(String buf, int cursor) {
    return null;
  }
  
  public boolean concurrentRequests() {
    return Boolean.parseBoolean(getProperty(ZEPPELIN_LENS_RUN_CONCURRENT_SESSION));
  }
  @Override
  public Scheduler getScheduler() {
    if (concurrentRequests()) {
      return SchedulerFactory.singleton().createOrGetParallelScheduler(
          LensInterpreter.class.getName() + this.hashCode(), m_maxThreads);
    } else {
      return super.getScheduler();
    }
  }
}
