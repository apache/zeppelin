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

package org.apache.zeppelin.shell;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.io.Resources;
import com.hubspot.jinjava.Jinjava;
import org.apache.commons.io.Charsets;
import org.apache.commons.lang3.StringUtils;
import org.apache.zeppelin.interpreter.ZeppelinContext;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResult.Code;
import org.apache.zeppelin.interpreter.InterpreterResultMessageOutput;
import org.apache.zeppelin.interpreter.KerberosInterpreter;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterUtils;
import org.apache.zeppelin.interpreter.thrift.InterpreterCompletion;
import org.apache.zeppelin.scheduler.Scheduler;
import org.apache.zeppelin.scheduler.SchedulerFactory;
import org.apache.zeppelin.shell.terminal.TerminalManager;
import org.apache.zeppelin.shell.terminal.TerminalThread;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URL;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;

/**
 * Shell interpreter for Zeppelin.
 */
public class TerminalInterpreter extends KerberosInterpreter {
  private static final Logger LOGGER = LoggerFactory.getLogger(TerminalInterpreter.class);

  public TerminalInterpreter(Properties property) {
    super(property);
  }

  private TerminalThread terminalThread = null;

  private InterpreterContext intpContext;

  private int terminalPort = 0;

  // terminal web socket status
  // ui_templates/terminal-dashboard.jinja
  public static final String TERMINAL_SOCKET_STATUS = "TERMINAL_SOCKET_STATUS";
  public static final String TERMINAL_SOCKET_CONNECT = "TERMINAL_SOCKET_CONNECT";
  public static final String TERMINAL_SOCKET_CLOSE = "TERMINAL_SOCKET_CLOSE";
  public static final String TERMINAL_SOCKET_ERROR = "TERMINAL_SOCKET_ERROR";

  @Override
  public void open() {
    super.open();
  }

  private void setParagraphConfig() {
    intpContext.getConfig().put("editorHide", true);
    intpContext.getConfig().put("title", false);
  }

  @Override
  public void close() {
    intpContext.getAngularObjectRegistry().add(TERMINAL_SOCKET_STATUS, TERMINAL_SOCKET_CLOSE,
        intpContext.getNoteId(), intpContext.getParagraphId());
    if (null != terminalThread) {
      TerminalManager.getInstance().cleanIntpContext(intpContext.getNoteId());
      terminalThread.stopRunning();
    }
    super.close();
  }

  @Override
  public ZeppelinContext getZeppelinContext() {
    return null;
  }

  @Override
  public InterpreterResult internalInterpret(String cmd, InterpreterContext context) {
    this.intpContext = context;
    TerminalManager.getInstance().setInterpreterContext(context);

    if (null == terminalThread) {
      try {
        terminalPort = RemoteInterpreterUtils.findRandomAvailablePortOnAllLocalInterfaces();
        terminalThread = new TerminalThread(terminalPort);
        terminalThread.start();
      } catch (IOException e) {
        LOGGER.error(e.getMessage(), e);
        return new InterpreterResult(Code.ERROR, e.getMessage());
      }

      for (int i = 0; i < 10 && !terminalThread.isRunning(); i++) {
        try {
          LOGGER.info("loop = " + i);
          Thread.sleep(500);
        } catch (InterruptedException e) {
          LOGGER.error(e.getMessage(), e);
        }
      }

      if (!terminalThread.isRunning()) {
        LOGGER.error("Terminal interpreter can not running!");
      }
    }
    setParagraphConfig();
    createTerminalDashboard(context.getNoteId(), context.getParagraphId(), terminalPort);

    return new InterpreterResult(Code.SUCCESS);
  }

  public void createTerminalDashboard(String noteId, String paragraphId, int port) {
    String hostName = "", hostIp = "";
    URL urlTemplate = Resources.getResource("ui_templates/terminal-dashboard.jinja");
    String template = null;
    try {
      template = Resources.toString(urlTemplate, Charsets.UTF_8);
      InetAddress addr = InetAddress.getLocalHost();
      hostName = addr.getHostName().toString();
      hostIp = RemoteInterpreterUtils.findAvailableHostAddress();
    } catch (IOException e) {
      LOGGER.error(e.getMessage(), e);
    }

    Jinjava jinjava = new Jinjava();
    HashMap<String, Object> jinjaParams = new HashMap();
    Date now = new Date();
    String terminalServerUrl = "http://" + hostIp + ":" + port +
        "?noteId=" + noteId + "&paragraphId=" + paragraphId + "&t=" + now.getTime();
    jinjaParams.put("HOST_NAME", hostName);
    jinjaParams.put("HOST_IP", hostIp);
    jinjaParams.put("TERMINAL_SERVER_URL", terminalServerUrl);
    String terminalDashboardTemplate = jinjava.render(template, jinjaParams);

    LOGGER.info(terminalDashboardTemplate);
    try {
      intpContext.out.setType(InterpreterResult.Type.ANGULAR);
      InterpreterResultMessageOutput outputUI = intpContext.out.getOutputAt(0);
      outputUI.clear();
      outputUI.write(terminalDashboardTemplate);
      outputUI.flush();
    } catch (IOException e) {
      LOGGER.error(e.getMessage(), e);
    }
  }

  @Override
  public void cancel(InterpreterContext context) {
  }

  @Override
  public FormType getFormType() {
    return FormType.SIMPLE;
  }

  @Override
  public int getProgress(InterpreterContext context) {
    return 0;
  }

  @Override
  public Scheduler getScheduler() {
    return SchedulerFactory.singleton().createOrGetParallelScheduler(
        TerminalInterpreter.class.getName() + this.hashCode(), 10);
  }

  @Override
  public List<InterpreterCompletion> completion(String buf, int cursor,
      InterpreterContext interpreterContext) {
    return null;
  }

  @Override
  protected boolean runKerberosLogin() {
    try {
      String kinitCommand = String.format("kinit -k -t %s %s",
          properties.getProperty("zeppelin.shell.keytab.location"),
          properties.getProperty("zeppelin.shell.principal"));

      TerminalManager.getInstance().runCommand(kinitCommand);
      return true;
    } catch (Exception e) {
      LOGGER.error("Unable to run kinit for zeppelin", e);
    }
    return false;
  }

  @Override
  protected boolean isKerboseEnabled() {
    if (!StringUtils.isAnyEmpty(getProperty("zeppelin.shell.auth.type")) && getProperty(
        "zeppelin.shell.auth.type").equalsIgnoreCase("kerberos")) {
      return true;
    }
    return false;
  }

  @VisibleForTesting
  public int getTerminalPort() {
    return terminalPort;
  }

  @VisibleForTesting
  public boolean terminalThreadIsRunning() {
    return terminalThread.isRunning();
  }
}
