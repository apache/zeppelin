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

import com.google.common.base.Joiner;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Type;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.internal.StringMap;
import com.google.gson.reflect.TypeToken;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.NullArgumentException;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonatype.aether.RepositoryException;
import org.sonatype.aether.repository.Authentication;
import org.sonatype.aether.repository.Proxy;
import org.sonatype.aether.repository.RemoteRepository;

import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.conf.ZeppelinConfiguration.ConfVars;
import org.apache.zeppelin.dep.Dependency;
import org.apache.zeppelin.dep.DependencyResolver;
import org.apache.zeppelin.display.AngularObjectRegistry;
import org.apache.zeppelin.display.AngularObjectRegistryListener;
import org.apache.zeppelin.helium.ApplicationEventListener;
import org.apache.zeppelin.interpreter.Interpreter.RegisteredInterpreter;
import org.apache.zeppelin.interpreter.remote.RemoteAngularObjectRegistry;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreter;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterProcessListener;
import org.apache.zeppelin.scheduler.Job;
import org.apache.zeppelin.scheduler.Job.Status;

/**
 * Manage interpreters.
 */
public class InterpreterFactory implements InterpreterGroupFactory {
  private static final Logger logger = LoggerFactory.getLogger(InterpreterFactory.class);

  private Map<String, URLClassLoader> cleanCl =
      Collections.synchronizedMap(new HashMap<String, URLClassLoader>());

  private ZeppelinConfiguration conf;

  private final InterpreterSettingManager interpreterSettingManager;

  private Gson gson;

  private AngularObjectRegistryListener angularObjectRegistryListener;
  private final RemoteInterpreterProcessListener remoteInterpreterProcessListener;
  private final ApplicationEventListener appEventListener;

  private boolean shiroEnabled;

  private Map<String, String> env = new HashMap<>();

  private Interpreter devInterpreter;

  public InterpreterFactory(ZeppelinConfiguration conf,
      AngularObjectRegistryListener angularObjectRegistryListener,
      RemoteInterpreterProcessListener remoteInterpreterProcessListener,
      ApplicationEventListener appEventListener, DependencyResolver depResolver,
      boolean shiroEnabled, InterpreterSettingManager interpreterSettingManager)
      throws InterpreterException, IOException, RepositoryException {
    this.conf = conf;
    this.angularObjectRegistryListener = angularObjectRegistryListener;
    this.remoteInterpreterProcessListener = remoteInterpreterProcessListener;
    this.appEventListener = appEventListener;
    this.shiroEnabled = shiroEnabled;

    GsonBuilder builder = new GsonBuilder();
    builder.setPrettyPrinting();
    gson = builder.create();

    this.interpreterSettingManager = interpreterSettingManager;
    //TODO(jl): Fix it not to use InterpreterGroupFactory
    interpreterSettingManager.setInterpreterGroupFactory(this);

    logger.info("shiroEnabled: {}", shiroEnabled);
  }

  /**
   * @param id interpreterGroup id. Combination of interpreterSettingId + noteId/userId/shared
   * depends on interpreter mode
   */
  @Override
  public InterpreterGroup createInterpreterGroup(String id, InterpreterOption option)
      throws InterpreterException, NullArgumentException {

    //When called from REST API without option we receive NPE
    if (option == null) {
      throw new NullArgumentException("option");
    }

    AngularObjectRegistry angularObjectRegistry;

    InterpreterGroup interpreterGroup = new InterpreterGroup(id);
    if (option.isRemote()) {
      angularObjectRegistry =
          new RemoteAngularObjectRegistry(id, angularObjectRegistryListener, interpreterGroup);
    } else {
      angularObjectRegistry = new AngularObjectRegistry(id, angularObjectRegistryListener);

      // TODO(moon) : create distributed resource pool for local interpreters and set
    }

    interpreterGroup.setAngularObjectRegistry(angularObjectRegistry);
    return interpreterGroup;
  }

  public void createInterpretersForNote(InterpreterSetting interpreterSetting, String user,
      String noteId, String interpreterSessionKey) {
    InterpreterGroup interpreterGroup = interpreterSetting.getInterpreterGroup(user, noteId);
    InterpreterOption option = interpreterSetting.getOption();
    Properties properties = (Properties) interpreterSetting.getProperties();
    // if interpreters are already there, wait until they're being removed
    synchronized (interpreterGroup) {
      long interpreterRemovalWaitStart = System.nanoTime();
      // interpreter process supposed to be terminated by RemoteInterpreterProcess.dereference()
      // in ZEPPELIN_INTERPRETER_CONNECT_TIMEOUT msec. However, if termination of the process and
      // removal from interpreter group take too long, throw an error.
      long minTimeout = 10L * 1000 * 1000000; // 10 sec
      long interpreterRemovalWaitTimeout = Math.max(minTimeout,
          conf.getInt(ConfVars.ZEPPELIN_INTERPRETER_CONNECT_TIMEOUT) * 1000000L * 2);
      while (interpreterGroup.containsKey(interpreterSessionKey)) {
        if (System.nanoTime() - interpreterRemovalWaitStart > interpreterRemovalWaitTimeout) {
          throw new InterpreterException("Can not create interpreter");
        }
        try {
          interpreterGroup.wait(1000);
        } catch (InterruptedException e) {
          logger.debug(e.getMessage(), e);
        }
      }
    }

    logger.info("Create interpreter instance {} for note {}", interpreterSetting.getName(), noteId);

    List<InterpreterInfo> interpreterInfos = interpreterSetting.getInterpreterInfos();
    String path = interpreterSetting.getPath();
    InterpreterRunner runner = interpreterSetting.getInterpreterRunner();
    Interpreter interpreter;
    for (InterpreterInfo info : interpreterInfos) {
      if (option.isRemote()) {
        if (option.isExistingProcess()) {
          interpreter =
              connectToRemoteRepl(interpreterSessionKey, info.getClassName(), option.getHost(),
                  option.getPort(), properties, interpreterSetting.getId(), user,
                  option.isUserImpersonate);
        } else {
          interpreter = createRemoteRepl(path, interpreterSessionKey, info.getClassName(),
              properties, interpreterSetting.getId(), user, option.isUserImpersonate(), runner);
        }
      } else {
        interpreter = createRepl(interpreterSetting.getPath(), info.getClassName(), properties);
      }

      synchronized (interpreterGroup) {
        List<Interpreter> interpreters = interpreterGroup.get(interpreterSessionKey);
        if (null == interpreters) {
          interpreters = new ArrayList<>();
          interpreterGroup.put(interpreterSessionKey, interpreters);
        }
        if (info.isDefaultInterpreter()) {
          interpreters.add(0, interpreter);
        } else {
          interpreters.add(interpreter);
        }
      }
      logger.info("Interpreter {} {} created", interpreter.getClassName(), interpreter.hashCode());
      interpreter.setInterpreterGroup(interpreterGroup);
    }
  }

  private Interpreter createRepl(String dirName, String className, Properties property)
      throws InterpreterException {
    logger.info("Create repl {} from {}", className, dirName);

    ClassLoader oldcl = Thread.currentThread().getContextClassLoader();
    try {

      URLClassLoader ccl = cleanCl.get(dirName);
      if (ccl == null) {
        // classloader fallback
        ccl = URLClassLoader.newInstance(new URL[]{}, oldcl);
      }

      boolean separateCL = true;
      try { // check if server's classloader has driver already.
        Class cls = this.getClass().forName(className);
        if (cls != null) {
          separateCL = false;
        }
      } catch (Exception e) {
        logger.error("exception checking server classloader driver", e);
      }

      URLClassLoader cl;

      if (separateCL == true) {
        cl = URLClassLoader.newInstance(new URL[]{}, ccl);
      } else {
        cl = ccl;
      }
      Thread.currentThread().setContextClassLoader(cl);

      Class<Interpreter> replClass = (Class<Interpreter>) cl.loadClass(className);
      Constructor<Interpreter> constructor =
          replClass.getConstructor(new Class[]{Properties.class});
      Interpreter repl = constructor.newInstance(property);
      repl.setClassloaderUrls(ccl.getURLs());
      LazyOpenInterpreter intp = new LazyOpenInterpreter(new ClassloaderInterpreter(repl, cl));
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

  private Interpreter connectToRemoteRepl(String interpreterSessionKey, String className,
      String host, int port, Properties property, String interpreterSettingId, String userName,
      Boolean isUserImpersonate) {
    int connectTimeout = conf.getInt(ConfVars.ZEPPELIN_INTERPRETER_CONNECT_TIMEOUT);
    int maxPoolSize = conf.getInt(ConfVars.ZEPPELIN_INTERPRETER_MAX_POOL_SIZE);
    String localRepoPath = conf.getInterpreterLocalRepoPath() + "/" + interpreterSettingId;
    LazyOpenInterpreter intp = new LazyOpenInterpreter(
        new RemoteInterpreter(property, interpreterSessionKey, className, host, port, localRepoPath,
            connectTimeout, maxPoolSize, remoteInterpreterProcessListener, appEventListener,
            userName, isUserImpersonate, conf.getInt(ConfVars.ZEPPELIN_INTERPRETER_OUTPUT_LIMIT)));
    return intp;
  }

  Interpreter createRemoteRepl(String interpreterPath, String interpreterSessionKey,
      String className, Properties property, String interpreterSettingId,
      String userName, Boolean isUserImpersonate, InterpreterRunner interpreterRunner) {
    int connectTimeout = conf.getInt(ConfVars.ZEPPELIN_INTERPRETER_CONNECT_TIMEOUT);
    String localRepoPath = conf.getInterpreterLocalRepoPath() + "/" + interpreterSettingId;
    int maxPoolSize = conf.getInt(ConfVars.ZEPPELIN_INTERPRETER_MAX_POOL_SIZE);
    String interpreterRunnerPath;
    String interpreterGroupName = interpreterSettingManager.get(interpreterSettingId).getName();
    if (null != interpreterRunner) {
      interpreterRunnerPath = interpreterRunner.getPath();
      Path p = Paths.get(interpreterRunnerPath);
      if (!p.isAbsolute()) {
        interpreterRunnerPath = Joiner.on(File.separator)
            .join(interpreterPath, interpreterRunnerPath);
      }
    } else {
      interpreterRunnerPath = conf.getInterpreterRemoteRunnerPath();
    }

    RemoteInterpreter remoteInterpreter =
        new RemoteInterpreter(property, interpreterSessionKey, className,
            interpreterRunnerPath, interpreterPath, localRepoPath, connectTimeout, maxPoolSize,
            remoteInterpreterProcessListener, appEventListener, userName, isUserImpersonate,
            conf.getInt(ConfVars.ZEPPELIN_INTERPRETER_OUTPUT_LIMIT), interpreterGroupName);
    remoteInterpreter.addEnv(env);

    return new LazyOpenInterpreter(remoteInterpreter);
  }

  private List<Interpreter> createOrGetInterpreterList(String user, String noteId,
      InterpreterSetting setting) {
    InterpreterGroup interpreterGroup = setting.getInterpreterGroup(user, noteId);
    synchronized (interpreterGroup) {
      String interpreterSessionKey =
          interpreterSettingManager.getInterpreterSessionKey(user, noteId, setting);
      if (!interpreterGroup.containsKey(interpreterSessionKey)) {
        createInterpretersForNote(setting, user, noteId, interpreterSessionKey);
      }
      return interpreterGroup.get(interpreterSessionKey);
    }
  }

  private InterpreterSetting getInterpreterSettingByGroup(List<InterpreterSetting> settings,
      String group) {
    Preconditions.checkNotNull(group, "group should be not null");

    for (InterpreterSetting setting : settings) {
      if (group.equals(setting.getName())) {
        return setting;
      }
    }
    return null;
  }

  private String getInterpreterClassFromInterpreterSetting(InterpreterSetting setting,
      String name) {
    Preconditions.checkNotNull(name, "name should be not null");

    for (InterpreterInfo info : setting.getInterpreterInfos()) {
      String infoName = info.getName();
      if (null != info.getName() && name.equals(infoName)) {
        return info.getClassName();
      }
    }
    return null;
  }

  private Interpreter getInterpreter(String user, String noteId, InterpreterSetting setting,
      String name) {
    Preconditions.checkNotNull(noteId, "noteId should be not null");
    Preconditions.checkNotNull(setting, "setting should be not null");
    Preconditions.checkNotNull(name, "name should be not null");

    String className;
    if (null != (className = getInterpreterClassFromInterpreterSetting(setting, name))) {
      List<Interpreter> interpreterGroup = createOrGetInterpreterList(user, noteId, setting);
      for (Interpreter interpreter : interpreterGroup) {
        if (className.equals(interpreter.getClassName())) {
          return interpreter;
        }
      }
    }
    return null;
  }

  public Interpreter getInterpreter(String user, String noteId, String replName) {
    List<InterpreterSetting> settings = interpreterSettingManager.getInterpreterSettings(noteId);
    InterpreterSetting setting;
    Interpreter interpreter;

    if (settings == null || settings.size() == 0) {
      return null;
    }

    if (replName == null || replName.trim().length() == 0) {
      // get default settings (first available)
      // TODO(jl): Fix it in case of returning null
      InterpreterSetting defaultSettings = interpreterSettingManager
          .getDefaultInterpreterSetting(settings);
      return createOrGetInterpreterList(user, noteId, defaultSettings).get(0);
    }

    String[] replNameSplit = replName.split("\\.");
    if (replNameSplit.length == 2) {
      String group = null;
      String name = null;
      group = replNameSplit[0];
      name = replNameSplit[1];

      setting = getInterpreterSettingByGroup(settings, group);

      if (null != setting) {
        interpreter = getInterpreter(user, noteId, setting, name);

        if (null != interpreter) {
          return interpreter;
        }
      }

      throw new InterpreterException(replName + " interpreter not found");

    } else {
      // first assume replName is 'name' of interpreter. ('groupName' is ommitted)
      // search 'name' from first (default) interpreter group
      // TODO(jl): Handle with noteId to support defaultInterpreter per note.
      setting = interpreterSettingManager.getDefaultInterpreterSetting(settings);

      interpreter = getInterpreter(user, noteId, setting, replName);

      if (null != interpreter) {
        return interpreter;
      }

      // next, assume replName is 'group' of interpreter ('name' is ommitted)
      // search interpreter group and return first interpreter.
      setting = getInterpreterSettingByGroup(settings, replName);

      if (null != setting) {
        List<Interpreter> interpreters = createOrGetInterpreterList(user, noteId, setting);
        if (null != interpreters) {
          return interpreters.get(0);
        }
      }

      // Support the legacy way to use it
      for (InterpreterSetting s : settings) {
        if (s.getGroup().equals(replName)) {
          List<Interpreter> interpreters = createOrGetInterpreterList(user, noteId, s);
          if (null != interpreters) {
            return interpreters.get(0);
          }
        }
      }
    }

    return null;
  }

  public Map<String, String> getEnv() {
    return env;
  }

  public void setEnv(Map<String, String> env) {
    this.env = env;
  }


}
