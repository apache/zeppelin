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
package org.apache.zeppelin.ignite;

import org.apache.ignite.Ignite;
import org.apache.ignite.Ignition;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi;
import org.apache.ignite.spi.discovery.tcp.ipfinder.vm.TcpDiscoveryVmIpFinder;
import org.apache.zeppelin.interpreter.*;
import org.apache.zeppelin.interpreter.InterpreterResult.Code;
import org.apache.zeppelin.interpreter.thrift.InterpreterCompletion;
import org.apache.zeppelin.scheduler.Scheduler;
import org.apache.zeppelin.scheduler.SchedulerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintWriter;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.LinkedList;
import java.util.Map;
import java.util.Properties;

import scala.Console;
import scala.None;
import scala.Some;
import scala.collection.JavaConversions;
import scala.tools.nsc.Settings;
import scala.tools.nsc.interpreter.IMain;
import scala.tools.nsc.interpreter.Results.Result;
import scala.tools.nsc.settings.MutableSettings.BooleanSetting;
import scala.tools.nsc.settings.MutableSettings.PathSetting;

/**
 * Apache Ignite interpreter (http://ignite.incubator.apache.org/).
 *
 * Use the following properties for interpreter configuration:
 *
 * <ul>
 *     <li>{@code ignite.addresses} - coma separated list of hosts in form {@code <host>:<port>}
 *     or {@code <host>:<port_1>..<port_n>} </li>
 *     <li>{@code ignite.clientMode} - indicates that Ignite interpreter
 *     should start node in client mode ({@code true} or {@code false}).</li>
 *     <li>{@code ignite.peerClassLoadingEnabled} - enables/disables peer class loading
 *     ({@code true} or {@code false}).</li>
 *     <li>{@code ignite.config.url} - URL for Ignite configuration. If this URL specified then
 *     all aforementioned properties will not be taken in account.</li>
 * </ul>
 */
public class IgniteInterpreter extends Interpreter {
  static final String IGNITE_ADDRESSES = "ignite.addresses";

  static final String IGNITE_CLIENT_MODE = "ignite.clientMode";

  static final String IGNITE_PEER_CLASS_LOADING_ENABLED = "ignite.peerClassLoadingEnabled";

  static final String IGNITE_CFG_URL = "ignite.config.url";

  static {
    Interpreter.register(
            "ignite",
            "ignite",
            IgniteInterpreter.class.getName(),
            true,
            new InterpreterPropertyBuilder()
                    .add(IGNITE_ADDRESSES, "127.0.0.1:47500..47509",
                            "Coma separated list of addresses "
                                    + "(e.g. 127.0.0.1:47500 or 127.0.0.1:47500..47509)")
                    .add(IGNITE_CLIENT_MODE, "true", "Client mode. true or false")
                    .add(IGNITE_CFG_URL, "", "Configuration URL. Overrides all other settings.")
                    .add(IGNITE_PEER_CLASS_LOADING_ENABLED, "true",
                            "Peer class loading enabled. true or false")
                    .build());
  }

  private Logger logger = LoggerFactory.getLogger(IgniteInterpreter.class);
  private Ignite ignite;
  private ByteArrayOutputStream out;
  private IMain imain;
  private Throwable initEx;

  public IgniteInterpreter(Properties property) {
    super(property);
  }

  @Override
  public void open() {
    Settings settings = new Settings();

    URL[] urls = getClassloaderUrls();

    // set classpath
    PathSetting pathSettings = settings.classpath();
    StringBuilder sb = new StringBuilder();

    for (File f : currentClassPath()) {
      if (sb.length() > 0) {
        sb.append(File.pathSeparator);
      }
      sb.append(f.getAbsolutePath());
    }

    if (urls != null) {
      for (URL u : urls) {
        if (sb.length() > 0) {
          sb.append(File.pathSeparator);
        }
        sb.append(u.getFile());
      }
    }

    pathSettings.v_$eq(sb.toString());
    settings.scala$tools$nsc$settings$ScalaSettings$_setter_$classpath_$eq(pathSettings);

    settings.explicitParentLoader_$eq(new Some<>(Thread.currentThread().getContextClassLoader()));

    BooleanSetting b = (BooleanSetting) settings.usejavacp();
    b.v_$eq(true);
    settings.scala$tools$nsc$settings$StandardScalaSettings$_setter_$usejavacp_$eq(b);

    out = new ByteArrayOutputStream();
    imain = new IMain(settings, new PrintWriter(out));

    initIgnite();
  }

  private List<File> currentClassPath() {
    List<File> paths = classPath(Thread.currentThread().getContextClassLoader());
    String[] cps = System.getProperty("java.class.path").split(File.pathSeparator);

    for (String cp : cps) {
      paths.add(new File(cp));
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

  public Object getLastObject() {
    Object obj = imain.lastRequest().lineRep().call(
        "$result",
        JavaConversions.asScalaBuffer(new LinkedList<>()));
    return obj;
  }

  private Ignite getIgnite() {
    if (ignite == null) {
      try {
        String cfgUrl = getProperty(IGNITE_CFG_URL);

        if (cfgUrl != null && !cfgUrl.isEmpty()) {
          ignite = Ignition.start(new URL(cfgUrl));
        } else {
          IgniteConfiguration conf = new IgniteConfiguration();

          conf.setClientMode(Boolean.parseBoolean(getProperty(IGNITE_CLIENT_MODE)));

          TcpDiscoveryVmIpFinder ipFinder = new TcpDiscoveryVmIpFinder();
          ipFinder.setAddresses(getAddresses());

          TcpDiscoverySpi discoSpi = new TcpDiscoverySpi();
          discoSpi.setIpFinder(ipFinder);
          conf.setDiscoverySpi(discoSpi);

          conf.setPeerClassLoadingEnabled(
                  Boolean.parseBoolean(getProperty(IGNITE_PEER_CLASS_LOADING_ENABLED)));

          ignite = Ignition.start(conf);
        }

        initEx = null;
      } catch (Exception e) {
        logger.error("Error in IgniteInterpreter while getIgnite: " , e);
        initEx = e;
      }
    }
    return ignite;
  }

  private void initIgnite() {
    imain.interpret("@transient var _binder = new java.util.HashMap[String, Object]()");
    Map<String, Object> binder = (Map<String, Object>) getLastObject();

    if (getIgnite() != null) {
      binder.put("ignite", ignite);

      imain.interpret("@transient val ignite = "
              + "_binder.get(\"ignite\")"
              + ".asInstanceOf[org.apache.ignite.Ignite]");
    }
  }

  @Override
  public void close() {
    initEx = null;

    if (ignite != null) {
      ignite.close();
      ignite = null;
    }

    if (imain != null) {
      imain.close();
      imain = null;
    }
  }

  private List<String> getAddresses() {
    String prop = getProperty(IGNITE_ADDRESSES);

    if (prop == null || prop.isEmpty()) {
      return Collections.emptyList();
    }

    String[] tokens = prop.split(",");
    List<String> addresses = new ArrayList<>(tokens.length);
    Collections.addAll(addresses, tokens);

    return addresses;
  }

  @Override
  public InterpreterResult interpret(String line, InterpreterContext context) {
    if (initEx != null) {
      return IgniteInterpreterUtils.buildErrorResult(initEx);
    }

    if (line == null || line.trim().length() == 0) {
      return new InterpreterResult(Code.SUCCESS);
    }

    return interpret(line.split("\n"));
  }

  @Override
  public void cancel(InterpreterContext context) {
  }

  private InterpreterResult interpret(String[] lines) {
    String[] linesToRun = new String[lines.length + 1];
    System.arraycopy(lines, 0, linesToRun, 0, lines.length);
    linesToRun[lines.length] = "print(\"\")";

    Console.setOut(out);
    out.reset();
    Code code = null;

    String incomplete = "";
    for (int l = 0; l < linesToRun.length; l++) {
      String s = linesToRun[l];      
      // check if next line starts with "." (but not ".." or "./") it is treated as an invocation
      if (l + 1 < linesToRun.length) {
        String nextLine = linesToRun[l + 1].trim();
        if (nextLine.startsWith(".") && !nextLine.startsWith("..") && !nextLine.startsWith("./")) {
          incomplete += s + "\n";
          continue;
        }
      }

      try {
        code = getResultCode(imain.interpret(incomplete + s));
      } catch (Exception e) {
        logger.info("Interpreter exception", e);
        return new InterpreterResult(Code.ERROR, InterpreterUtils.getMostRelevantMessage(e));
      }

      if (code == Code.ERROR) {
        return new InterpreterResult(code, out.toString());
      } else if (code == Code.INCOMPLETE) {
        incomplete += s + '\n';
      } else {
        incomplete = "";
      }
    }

    if (code == Code.INCOMPLETE) {
      return new InterpreterResult(code, "Incomplete expression");
    } else {
      return new InterpreterResult(code, out.toString());
    }
  }

  private Code getResultCode(Result res) {
    if (res instanceof scala.tools.nsc.interpreter.Results.Success$) {
      return Code.SUCCESS;
    } else if (res instanceof scala.tools.nsc.interpreter.Results.Incomplete$) {
      return Code.INCOMPLETE;
    } else {
      return Code.ERROR;
    }
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
  public List<InterpreterCompletion> completion(String buf, int cursor) {
    return new LinkedList<>();
  }

  @Override
  public Scheduler getScheduler() {
    return SchedulerFactory.singleton().createOrGetFIFOScheduler(
            IgniteInterpreter.class.getName() + this.hashCode());
  }
}
