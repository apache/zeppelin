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

package org.apache.zeppelin.interpreter;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.NullArgumentException;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.conf.ZeppelinConfiguration.ConfVars;
import org.apache.zeppelin.display.AngularObjectRegistry;
import org.apache.zeppelin.display.AngularObjectRegistryListener;
import org.apache.zeppelin.interpreter.Interpreter.RegisteredInterpreter;
import org.apache.zeppelin.interpreter.remote.RemoteAngularObjectRegistry;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreter;
import org.apache.zeppelin.scheduler.Job;
import org.apache.zeppelin.scheduler.Job.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Manage interpreters.
 *
 */
public class InterpreterFactory {
  Logger logger = LoggerFactory.getLogger(InterpreterFactory.class);

  private Map<String, URLClassLoader> cleanCl = Collections
      .synchronizedMap(new HashMap<String, URLClassLoader>());

  private ZeppelinConfiguration conf;
  String[] interpreterClassList;

  private Map<String, InterpreterSetting> interpreterSettings =
      new HashMap<String, InterpreterSetting>();

  private Map<String, List<String>> interpreterBindings = new HashMap<String, List<String>>();

  private Gson gson;

  private InterpreterOption defaultOption;

  AngularObjectRegistryListener angularObjectRegistryListener;

  public InterpreterFactory(ZeppelinConfiguration conf,
      AngularObjectRegistryListener angularObjectRegistryListener)
      throws InterpreterException, IOException {
    this(conf, new InterpreterOption(true), angularObjectRegistryListener);
  }


  public InterpreterFactory(ZeppelinConfiguration conf, InterpreterOption defaultOption,
      AngularObjectRegistryListener angularObjectRegistryListener)
      throws InterpreterException, IOException {
    this.conf = conf;
    this.defaultOption = defaultOption;
    this.angularObjectRegistryListener = angularObjectRegistryListener;
    String replsConf = conf.getString(ConfVars.ZEPPELIN_INTERPRETERS);
    interpreterClassList = replsConf.split(",");

    GsonBuilder builder = new GsonBuilder();
    builder.setPrettyPrinting();
    builder.registerTypeAdapter(Interpreter.class, new InterpreterSerializer());
    gson = builder.create();

    init();
  }

  private void init() throws InterpreterException, IOException {
    ClassLoader oldcl = Thread.currentThread().getContextClassLoader();

    // Load classes
    File[] interpreterDirs = new File(conf.getInterpreterDir()).listFiles();
    if (interpreterDirs != null) {
      for (File path : interpreterDirs) {
        logger.info("Reading " + path.getAbsolutePath());
        URL[] urls = null;
        try {
          urls = recursiveBuildLibList(path);
        } catch (MalformedURLException e1) {
          logger.error("Can't load jars ", e1);
        }
        URLClassLoader ccl = new URLClassLoader(urls, oldcl);

        for (String className : interpreterClassList) {
          try {
            Class.forName(className, true, ccl);
            Set<String> keys = Interpreter.registeredInterpreters.keySet();
            for (String intName : keys) {
              if (className.equals(
                  Interpreter.registeredInterpreters.get(intName).getClassName())) {
                Interpreter.registeredInterpreters.get(intName).setPath(path.getAbsolutePath());
                logger.info("Interpreter " + intName + " found. class=" + className);
                cleanCl.put(path.getAbsolutePath(), ccl);
              }
            }
          } catch (ClassNotFoundException e) {
            // nothing to do
          }
        }
      }
    }

    loadFromFile();

    // if no interpreter settings are loaded, create default set
    synchronized (interpreterSettings) {
      if (interpreterSettings.size() == 0) {
        HashMap<String, List<RegisteredInterpreter>> groupClassNameMap =
            new HashMap<String, List<RegisteredInterpreter>>();

        for (String k : Interpreter.registeredInterpreters.keySet()) {
          RegisteredInterpreter info = Interpreter.registeredInterpreters.get(k);

          if (!groupClassNameMap.containsKey(info.getGroup())) {
            groupClassNameMap.put(info.getGroup(), new LinkedList<RegisteredInterpreter>());
          }

          groupClassNameMap.get(info.getGroup()).add(info);
        }

        for (String className : interpreterClassList) {
          for (String groupName : groupClassNameMap.keySet()) {
            List<RegisteredInterpreter> infos = groupClassNameMap.get(groupName);

            boolean found = false;
            Properties p = new Properties();
            for (RegisteredInterpreter info : infos) {
              if (found == false && info.getClassName().equals(className)) {
                found = true;
              }

              for (String k : info.getProperties().keySet()) {
                p.put(k, info.getProperties().get(k).getDefaultValue());
              }
            }

            if (found) {
              // add all interpreters in group
              add(groupName, groupName, defaultOption, p);
              groupClassNameMap.remove(groupName);
              break;
            }
          }
        }
      }
    }

    for (String settingId : interpreterSettings.keySet()) {
      InterpreterSetting setting = interpreterSettings.get(settingId);
      logger.info("Interpreter setting group {} : id={}, name={}",
          setting.getGroup(), settingId, setting.getName());
      for (Interpreter interpreter : setting.getInterpreterGroup()) {
        logger.info("  className = {}", interpreter.getClassName());
      }
    }
  }

  private void loadFromFile() throws IOException {
    GsonBuilder builder = new GsonBuilder();
    builder.setPrettyPrinting();
    builder.registerTypeAdapter(Interpreter.class, new InterpreterSerializer());
    Gson gson = builder.create();

    File settingFile = new File(conf.getInterpreterSettingPath());
    if (!settingFile.exists()) {
      // nothing to read
      return;
    }
    FileInputStream fis = new FileInputStream(settingFile);
    InputStreamReader isr = new InputStreamReader(fis);
    BufferedReader bufferedReader = new BufferedReader(isr);
    StringBuilder sb = new StringBuilder();
    String line;
    while ((line = bufferedReader.readLine()) != null) {
      sb.append(line);
    }
    isr.close();
    fis.close();

    String json = sb.toString();
    InterpreterInfoSaving info = gson.fromJson(json, InterpreterInfoSaving.class);

    for (String k : info.interpreterSettings.keySet()) {
      InterpreterSetting setting = info.interpreterSettings.get(k);

      // Always use separate interpreter process
      // While we decided to turn this feature on always (without providing
      // enable/disable option on GUI).
      // previously created setting should turn this feature on here.
      setting.getOption().setRemote(true);



      InterpreterSetting intpSetting = new InterpreterSetting(
          setting.id(),
          setting.getName(),
          setting.getGroup(),
          setting.getOption());

      InterpreterGroup interpreterGroup = createInterpreterGroup(
          setting.id(),
          setting.getGroup(),
          setting.getOption(),
          setting.getProperties());
      intpSetting.setInterpreterGroup(interpreterGroup);

      interpreterSettings.put(k, intpSetting);
    }

    this.interpreterBindings = info.interpreterBindings;
  }


  private void saveToFile() throws IOException {
    String jsonString;

    synchronized (interpreterSettings) {
      InterpreterInfoSaving info = new InterpreterInfoSaving();
      info.interpreterBindings = interpreterBindings;
      info.interpreterSettings = interpreterSettings;

      jsonString = gson.toJson(info);
    }

    File settingFile = new File(conf.getInterpreterSettingPath());
    if (!settingFile.exists()) {
      settingFile.createNewFile();
    }

    FileOutputStream fos = new FileOutputStream(settingFile, false);
    OutputStreamWriter out = new OutputStreamWriter(fos);
    out.append(jsonString);
    out.close();
    fos.close();
  }

  private RegisteredInterpreter getRegisteredReplInfoFromClassName(String clsName) {
    Set<String> keys = Interpreter.registeredInterpreters.keySet();
    for (String intName : keys) {
      RegisteredInterpreter info = Interpreter.registeredInterpreters.get(intName);
      if (clsName.equals(info.getClassName())) {
        return info;
      }
    }
    return null;
  }

  /**
   * Return ordered interpreter setting list.
   * The list does not contain more than one setting from the same interpreter class.
   * Order by InterpreterClass (order defined by ZEPPELIN_INTERPRETERS), Interpreter setting name
   * @return
   */
  public List<String> getDefaultInterpreterSettingList() {
    // this list will contain default interpreter setting list
    List<String> defaultSettings = new LinkedList<String>();

    // to ignore the same interpreter group
    Map<String, Boolean> interpreterGroupCheck = new HashMap<String, Boolean>();

    List<InterpreterSetting> sortedSettings = get();

    for (InterpreterSetting setting : sortedSettings) {
      if (defaultSettings.contains(setting.id())) {
        continue;
      }

      if (!interpreterGroupCheck.containsKey(setting.getGroup())) {
        defaultSettings.add(setting.id());
        interpreterGroupCheck.put(setting.getGroup(), true);
      }
    }
    return defaultSettings;
  }

  public List<RegisteredInterpreter> getRegisteredInterpreterList() {
    List<RegisteredInterpreter> registeredInterpreters = new LinkedList<RegisteredInterpreter>();

    for (String className : interpreterClassList) {
      RegisteredInterpreter ri = Interpreter.findRegisteredInterpreterByClassName(className);
      if (ri != null) {
        registeredInterpreters.add(ri);
      }
    }
    return registeredInterpreters;
  }

  /**
   * @param name user defined name
   * @param groupName interpreter group name to instantiate
   * @param properties
   * @return
   * @throws InterpreterException
   * @throws IOException
   */
  public InterpreterGroup add(String name, String groupName,
      InterpreterOption option, Properties properties)
      throws InterpreterException, IOException {
    synchronized (interpreterSettings) {

      InterpreterSetting intpSetting = new InterpreterSetting(
          name,
          groupName,
          option);

      InterpreterGroup interpreterGroup = createInterpreterGroup(
          intpSetting.id(), groupName, option, properties);

      intpSetting.setInterpreterGroup(interpreterGroup);

      interpreterSettings.put(intpSetting.id(), intpSetting);
      saveToFile();
      return interpreterGroup;
    }
  }

  private InterpreterGroup createInterpreterGroup(String id,
      String groupName,
      InterpreterOption option,
      Properties properties)
      throws InterpreterException , NullArgumentException {

    //When called from REST API without option we receive NPE
    if (option == null )
      throw new NullArgumentException("option");
    //When called from REST API without option we receive NPE
    if (properties == null )
      throw new NullArgumentException("properties");

    AngularObjectRegistry angularObjectRegistry;

    InterpreterGroup interpreterGroup = new InterpreterGroup(id);
    if (option.isRemote()) {
      angularObjectRegistry = new RemoteAngularObjectRegistry(
          id,
          angularObjectRegistryListener,
          interpreterGroup
      );
    } else {
      angularObjectRegistry = new AngularObjectRegistry(
          id,
          angularObjectRegistryListener);
    }

    interpreterGroup.setAngularObjectRegistry(angularObjectRegistry);


    for (String className : interpreterClassList) {
      Set<String> keys = Interpreter.registeredInterpreters.keySet();
      for (String intName : keys) {
        RegisteredInterpreter info = Interpreter.registeredInterpreters
            .get(intName);
        if (info.getClassName().equals(className)
            && info.getGroup().equals(groupName)) {
          Interpreter intp;

          if (option.isRemote()) {
            intp = createRemoteRepl(info.getPath(),
                info.getClassName(),
                properties);
          } else {
            intp = createRepl(info.getPath(),
                info.getClassName(),
                properties);
          }
          interpreterGroup.add(intp);
          intp.setInterpreterGroup(interpreterGroup);
          break;
        }
      }
    }
    return interpreterGroup;
  }

  public void remove(String id) throws IOException {
    synchronized (interpreterSettings) {
      if (interpreterSettings.containsKey(id)) {
        InterpreterSetting intp = interpreterSettings.get(id);
        intp.getInterpreterGroup().close();
        intp.getInterpreterGroup().destroy();

        interpreterSettings.remove(id);
        for (List<String> settings : interpreterBindings.values()) {
          Iterator<String> it = settings.iterator();
          while (it.hasNext()) {
            String settingId = it.next();
            if (settingId.equals(id)) {
              it.remove();
            }
          }
        }
        saveToFile();
      }
    }
  }

  /**
   * Get loaded interpreters
   * @return
   */
  public List<InterpreterSetting> get() {
    synchronized (interpreterSettings) {
      List<InterpreterSetting> orderedSettings = new LinkedList<InterpreterSetting>();
      List<InterpreterSetting> settings = new LinkedList<InterpreterSetting>(
          interpreterSettings.values());
      Collections.sort(settings, new Comparator<InterpreterSetting>(){
        @Override
        public int compare(InterpreterSetting o1, InterpreterSetting o2) {
          return o1.getName().compareTo(o2.getName());
        }
      });

      for (String className : interpreterClassList) {
        for (InterpreterSetting setting : settings) {
          for (InterpreterSetting orderedSetting : orderedSettings) {
            if (orderedSetting.id().equals(setting.id())) {
              continue;
            }
          }

          for (Interpreter intp : setting.getInterpreterGroup()) {
            if (className.equals(intp.getClassName())) {
              boolean alreadyAdded = false;
              for (InterpreterSetting st : orderedSettings) {
                if (setting.id().equals(st.id())) {
                  alreadyAdded = true;
                }
              }
              if (alreadyAdded == false) {
                orderedSettings.add(setting);
              }
            }
          }
        }
      }
      return orderedSettings;
    }
  }

  public InterpreterSetting get(String name) {
    synchronized (interpreterSettings) {
      return interpreterSettings.get(name);
    }
  }

  public void putNoteInterpreterSettingBinding(String noteId,
      List<String> settingList) throws IOException {
    synchronized (interpreterSettings) {
      interpreterBindings.put(noteId, settingList);
      saveToFile();
    }
  }

  public void removeNoteInterpreterSettingBinding(String noteId) {
    synchronized (interpreterSettings) {
      interpreterBindings.remove(noteId);
    }
  }

  public List<String> getNoteInterpreterSettingBinding(String noteId) {
    LinkedList<String> bindings = new LinkedList<String>();
    synchronized (interpreterSettings) {
      List<String> settingIds = interpreterBindings.get(noteId);
      if (settingIds != null) {
        bindings.addAll(settingIds);
      }
    }
    return bindings;
  }

  /**
   * Change interpreter property and restart
   * @param name
   * @param properties
   * @throws IOException
   */
  public void setPropertyAndRestart(String id, InterpreterOption option,
      Properties properties) throws IOException {
    synchronized (interpreterSettings) {
      InterpreterSetting intpsetting = interpreterSettings.get(id);
      if (intpsetting != null) {

        stopJobAllInterpreter(intpsetting);

        intpsetting.getInterpreterGroup().close();
        intpsetting.getInterpreterGroup().destroy();

        intpsetting.setOption(option);

        InterpreterGroup interpreterGroup = createInterpreterGroup(
            intpsetting.id(),
            intpsetting.getGroup(), option, properties);
        intpsetting.setInterpreterGroup(interpreterGroup);
        saveToFile();
      } else {
        throw new InterpreterException("Interpreter setting id " + id
            + " not found");
      }
    }
  }

  public void restart(String id) {
    synchronized (interpreterSettings) {
      InterpreterSetting intpsetting = interpreterSettings.get(id);
      if (intpsetting != null) {

        stopJobAllInterpreter(intpsetting);

        intpsetting.getInterpreterGroup().close();
        intpsetting.getInterpreterGroup().destroy();

        InterpreterGroup interpreterGroup = createInterpreterGroup(
            intpsetting.id(),
            intpsetting.getGroup(), intpsetting.getOption(), intpsetting.getProperties());
        intpsetting.setInterpreterGroup(interpreterGroup);
      } else {
        throw new InterpreterException("Interpreter setting id " + id
            + " not found");
      }
    }
  }

  private void stopJobAllInterpreter(InterpreterSetting intpsetting) {
    if (intpsetting != null) {
      for (Interpreter intp : intpsetting.getInterpreterGroup()) {
        for (Job job : intp.getScheduler().getJobsRunning()) {
          job.abort();
          job.setStatus(Status.ABORT);
          logger.info("Job " + job.getJobName() + " aborted ");
        }
        for (Job job : intp.getScheduler().getJobsWaiting()) {
          job.abort();
          job.setStatus(Status.ABORT);
          logger.info("Job " + job.getJobName() + " aborted ");
        }
      }
    }
  }

  public void close() {
    List<Thread> closeThreads = new LinkedList<Thread>();
    synchronized (interpreterSettings) {
      Collection<InterpreterSetting> intpsettings = interpreterSettings.values();
      for (final InterpreterSetting intpsetting : intpsettings) {
        Thread t = new Thread() {
          public void run() {
            intpsetting.getInterpreterGroup().close();
            intpsetting.getInterpreterGroup().destroy();
          }
        };
        t.start();
        closeThreads.add(t);
      }
    }

    for (Thread t : closeThreads) {
      try {
        t.join();
      } catch (InterruptedException e) {
        logger.error("Can't close interpreterGroup", e);
      }
    }
  }

  private Interpreter createRepl(String dirName, String className,
      Properties property)
      throws InterpreterException {
    logger.info("Create repl {} from {}", className, dirName);

    ClassLoader oldcl = Thread.currentThread().getContextClassLoader();
    try {

      URLClassLoader ccl = cleanCl.get(dirName);
      if (ccl == null) {
        // classloader fallback
        ccl = URLClassLoader.newInstance(new URL[] {}, oldcl);
      }

      boolean separateCL = true;
      try { // check if server's classloader has driver already.
        Class cls = this.getClass().forName(className);
        if (cls != null) {
          separateCL = false;
        }
      } catch (Exception e) {
        logger.error("exception checking server classloader driver" , e);
      }

      URLClassLoader cl;

      if (separateCL == true) {
        cl = URLClassLoader.newInstance(new URL[] {}, ccl);
      } else {
        cl = ccl;
      }
      Thread.currentThread().setContextClassLoader(cl);

      Class<Interpreter> replClass = (Class<Interpreter>) cl.loadClass(className);
      Constructor<Interpreter> constructor =
          replClass.getConstructor(new Class[] {Properties.class});
      Interpreter repl = constructor.newInstance(property);
      repl.setClassloaderUrls(ccl.getURLs());
      LazyOpenInterpreter intp = new LazyOpenInterpreter(
          new ClassloaderInterpreter(repl, cl));
      return intp;
    } catch (SecurityException e) {
      throw new InterpreterException(e);
    } catch (NoSuchMethodException e) {
      throw new InterpreterException(e);
    } catch (IllegalArgumentException e) {
      throw new InterpreterException(e);
    } catch (InstantiationException e) {
      throw new InterpreterException(e);
    } catch (IllegalAccessException e) {
      throw new InterpreterException(e);
    } catch (InvocationTargetException e) {
      throw new InterpreterException(e);
    } catch (ClassNotFoundException e) {
      throw new InterpreterException(e);
    } finally {
      Thread.currentThread().setContextClassLoader(oldcl);
    }
  }


  private Interpreter createRemoteRepl(String interpreterPath, String className,
      Properties property) {

    int connectTimeout = conf.getInt(ConfVars.ZEPPELIN_INTERPRETER_CONNECT_TIMEOUT);
    LazyOpenInterpreter intp = new LazyOpenInterpreter(new RemoteInterpreter(
        property, className, conf.getInterpreterRemoteRunnerPath(),
        interpreterPath, connectTimeout));
    return intp;
  }


  private URL[] recursiveBuildLibList(File path) throws MalformedURLException {
    URL[] urls = new URL[0];
    if (path == null || path.exists() == false) {
      return urls;
    } else if (path.getName().startsWith(".")) {
      return urls;
    } else if (path.isDirectory()) {
      File[] files = path.listFiles();
      if (files != null) {
        for (File f : files) {
          urls = (URL[]) ArrayUtils.addAll(urls, recursiveBuildLibList(f));
        }
      }
      return urls;
    } else {
      return new URL[] {path.toURI().toURL()};
    }
  }
}
