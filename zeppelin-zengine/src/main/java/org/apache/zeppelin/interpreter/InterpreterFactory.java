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
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Type;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
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

import com.google.common.base.Preconditions;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.NullArgumentException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonatype.aether.RepositoryException;
import org.sonatype.aether.repository.Authentication;
import org.sonatype.aether.repository.RemoteRepository;

import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.conf.ZeppelinConfiguration.ConfVars;
import org.apache.zeppelin.dep.Dependency;
import org.apache.zeppelin.dep.DependencyResolver;
import org.apache.zeppelin.display.AngularObjectRegistry;
import org.apache.zeppelin.display.AngularObjectRegistryListener;
import org.apache.zeppelin.helium.ApplicationEventListener;
import org.apache.zeppelin.interpreter.Interpreter.RegisteredInterpreter;
import org.apache.zeppelin.interpreter.dev.DevInterpreter;
import org.apache.zeppelin.interpreter.dev.ZeppelinDevServer;
import org.apache.zeppelin.interpreter.remote.RemoteAngularObjectRegistry;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreter;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterProcessListener;
import org.apache.zeppelin.scheduler.Job;
import org.apache.zeppelin.scheduler.Job.Status;

/**
 * Manage interpreters.
 */
public class InterpreterFactory implements InterpreterGroupFactory {
  private static Logger logger = LoggerFactory.getLogger(InterpreterFactory.class);

  private static final String SHARED_SESSION = "shared_session";

  private Map<String, URLClassLoader> cleanCl =
      Collections.synchronizedMap(new HashMap<String, URLClassLoader>());

  private ZeppelinConfiguration conf;
  @Deprecated private String[] interpreterClassList;
  private String[] interpreterGroupOrderList;

  /**
   * This is only references with default settings, name and properties
   * key: InterpreterSetting.name
   */
  private Map<String, InterpreterSetting> interpreterSettingsRef = new HashMap<>();

  /**
   * This is used by creating and running Interpreters
   * key: InterpreterSetting.id <- This is becuase backward compatibility
   */
  private Map<String, InterpreterSetting> interpreterSettings = new HashMap<>();

  private Map<String, List<String>> interpreterBindings = new HashMap<>();
  private List<RemoteRepository> interpreterRepositories;

  private Gson gson;

  private InterpreterOption defaultOption;

  private AngularObjectRegistryListener angularObjectRegistryListener;
  private final RemoteInterpreterProcessListener remoteInterpreterProcessListener;
  private final ApplicationEventListener appEventListener;

  private DependencyResolver depResolver;

  private Map<String, String> env = new HashMap<>();

  private Interpreter devInterpreter;

  public InterpreterFactory(ZeppelinConfiguration conf,
      AngularObjectRegistryListener angularObjectRegistryListener,
      RemoteInterpreterProcessListener remoteInterpreterProcessListener,
      ApplicationEventListener appEventListener, DependencyResolver depResolver)
      throws InterpreterException, IOException, RepositoryException {
    this(conf, new InterpreterOption(true), angularObjectRegistryListener,
        remoteInterpreterProcessListener, appEventListener, depResolver);
  }


  public InterpreterFactory(ZeppelinConfiguration conf, InterpreterOption defaultOption,
      AngularObjectRegistryListener angularObjectRegistryListener,
      RemoteInterpreterProcessListener remoteInterpreterProcessListener,
      ApplicationEventListener appEventListener, DependencyResolver depResolver)
      throws InterpreterException, IOException, RepositoryException {
    this.conf = conf;
    this.defaultOption = defaultOption;
    this.angularObjectRegistryListener = angularObjectRegistryListener;
    this.depResolver = depResolver;
    this.interpreterRepositories = depResolver.getRepos();
    this.remoteInterpreterProcessListener = remoteInterpreterProcessListener;
    this.appEventListener = appEventListener;
    String replsConf = conf.getString(ConfVars.ZEPPELIN_INTERPRETERS);
    interpreterClassList = replsConf.split(",");
    String groupOrder = conf.getString(ConfVars.ZEPPELIN_INTERPRETER_GROUP_ORDER);
    interpreterGroupOrderList = groupOrder.split(",");

    GsonBuilder builder = new GsonBuilder();
    builder.setPrettyPrinting();
    gson = builder.create();

    init();
  }

  private void init() throws InterpreterException, IOException, RepositoryException {
    String interpreterJson = conf.getInterpreterJson();
    ClassLoader cl = Thread.currentThread().getContextClassLoader();

    Path interpretersDir = Paths.get(conf.getInterpreterDir());
    if (Files.exists(interpretersDir)) {
      for (Path interpreterDir : Files
          .newDirectoryStream(interpretersDir, new DirectoryStream.Filter<Path>() {
            @Override
            public boolean accept(Path entry) throws IOException {
              return Files.exists(entry) && Files.isDirectory(entry);
            }
          })) {
        String interpreterDirString = interpreterDir.toString();

        registerInterpreterFromPath(interpreterDirString, interpreterJson);

        registerInterpreterFromResource(cl, interpreterDirString, interpreterJson);

        /**
         * TODO(jongyoul)
         * - Remove these codes below because of legacy code
         * - Support ThreadInterpreter
         */
        URLClassLoader ccl = new URLClassLoader(recursiveBuildLibList(interpreterDir.toFile()), cl);
        for (String className : interpreterClassList) {
          try {
            // Load classes
            Class.forName(className, true, ccl);
            Set<String> interpreterKeys = Interpreter.registeredInterpreters.keySet();
            for (String interpreterKey : interpreterKeys) {
              if (className
                  .equals(Interpreter.registeredInterpreters.get(interpreterKey).getClassName())) {
                Interpreter.registeredInterpreters.get(interpreterKey)
                    .setPath(interpreterDirString);
                logger.info("Interpreter " + interpreterKey + " found. class=" + className);
                cleanCl.put(interpreterDirString, ccl);
              }
            }
          } catch (Throwable t) {
            // nothing to do
          }
        }
      }
    }

    for (RegisteredInterpreter registeredInterpreter : Interpreter.registeredInterpreters
        .values()) {
      logger
          .debug("Registered: {} -> {}. Properties: {}", registeredInterpreter.getInterpreterKey(),
              registeredInterpreter.getClassName(), registeredInterpreter.getProperties());
    }

    // RegisteredInterpreters -> interpreterSettingRef
    InterpreterInfo interpreterInfo;
    for (RegisteredInterpreter r : Interpreter.registeredInterpreters.values()) {
      interpreterInfo =
          new InterpreterInfo(r.getClassName(), r.getName(), r.isDefaultInterpreter());
      add(r.getGroup(), interpreterInfo, convertInterpreterProperties(r.getProperties()),
          r.getPath());
    }

    for (String settingId : interpreterSettingsRef.keySet()) {
      InterpreterSetting setting = interpreterSettingsRef.get(settingId);
      logger.info("InterpreterSettingRef name {}", setting.getName());
    }

    loadFromFile();

    // if no interpreter settings are loaded, create default set
    if (0 == interpreterSettings.size()) {
      Map<String, InterpreterSetting> temp = new HashMap<>();
      InterpreterSetting interpreterSetting;
      for (InterpreterSetting setting : interpreterSettingsRef.values()) {
        interpreterSetting = createFromInterpreterSettingRef(setting);
        temp.put(setting.getName(), interpreterSetting);
      }

      for (String group : interpreterGroupOrderList) {
        if (null != (interpreterSetting = temp.remove(group))) {
          interpreterSettings.put(interpreterSetting.getId(), interpreterSetting);
        }
      }

      for (InterpreterSetting setting : temp.values()) {
        interpreterSettings.put(setting.getId(), setting);
      }

      saveToFile();
    }


    for (String settingId : interpreterSettings.keySet()) {
      InterpreterSetting setting = interpreterSettings.get(settingId);
      logger.info("InterpreterSetting group {} : id={}, name={}", setting.getGroup(), settingId,
          setting.getName());
    }
  }

  private InterpreterSetting createFromInterpreterSettingRef(String name) {
    Preconditions.checkNotNull(name, "reference name should be not null");
    InterpreterSetting settingRef = interpreterSettingsRef.get(name);
    return createFromInterpreterSettingRef(settingRef);
  }

  private InterpreterSetting createFromInterpreterSettingRef(InterpreterSetting o) {
    InterpreterSetting setting =
        new InterpreterSetting(o.getName(), o.getName(), o.getInterpreterInfos(), o.getProperties(),
            o.getDependencies(), o.getOption(), o.getPath());
    setting.setInterpreterGroupFactory(this);
    return setting;
  }

  private Properties convertInterpreterProperties(Map<String, InterpreterProperty> p) {
    Properties properties = new Properties();
    for (String key : p.keySet()) {
      properties.put(key, p.get(key).getValue());
    }
    return properties;
  }

  private void registerInterpreterFromResource(ClassLoader cl, String interpreterDir,
      String interpreterJson) throws IOException, RepositoryException {
    URL[] urls = recursiveBuildLibList(new File(interpreterDir));
    ClassLoader tempClassLoader = new URLClassLoader(urls, cl);

    InputStream inputStream = tempClassLoader.getResourceAsStream(interpreterJson);

    if (null != inputStream) {
      logger.debug("Reading {} from resources in {}", interpreterJson, interpreterDir);
      List<RegisteredInterpreter> registeredInterpreterList =
          getInterpreterListFromJson(inputStream);
      registerInterpreters(registeredInterpreterList, interpreterDir);
    }
  }

  private void registerInterpreterFromPath(String interpreterDir, String interpreterJson)
      throws IOException, RepositoryException {

    Path interpreterJsonPath = Paths.get(interpreterDir, interpreterJson);
    if (Files.exists(interpreterJsonPath)) {
      logger.debug("Reading {}", interpreterJsonPath);
      List<RegisteredInterpreter> registeredInterpreterList =
          getInterpreterListFromJson(interpreterJsonPath);
      registerInterpreters(registeredInterpreterList, interpreterDir);
    }
  }

  private List<RegisteredInterpreter> getInterpreterListFromJson(Path filename)
      throws FileNotFoundException {
    return getInterpreterListFromJson(new FileInputStream(filename.toFile()));
  }

  private List<RegisteredInterpreter> getInterpreterListFromJson(InputStream stream) {
    Type registeredInterpreterListType = new TypeToken<List<RegisteredInterpreter>>() {
    }.getType();
    return gson.fromJson(new InputStreamReader(stream), registeredInterpreterListType);
  }

  private void registerInterpreters(List<RegisteredInterpreter> registeredInterpreters,
      String absolutePath) throws IOException, RepositoryException {

    for (RegisteredInterpreter registeredInterpreter : registeredInterpreters) {
      InterpreterInfo interpreterInfo =
          new InterpreterInfo(registeredInterpreter.getClassName(), registeredInterpreter.getName(),
              registeredInterpreter.isDefaultInterpreter());
      Properties properties = new Properties();
      Map<String, InterpreterProperty> p = registeredInterpreter.getProperties();

      if (null != p) {
        for (String key : p.keySet()) {
          properties.setProperty(key, p.get(key).getValue());
        }
      }

      add(registeredInterpreter.getGroup(), interpreterInfo, properties, absolutePath);
    }

  }

  private void loadFromFile() throws IOException {
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

      // Update transient information from InterpreterSettingRef
      // TODO(jl): Check if reference of setting is null

      setting.setPath(interpreterSettingsRef.get(setting.getGroup()).getPath());

      setting.setInterpreterGroupFactory(this);
      loadInterpreterDependencies(setting);
      interpreterSettings.put(k, setting);
    }

    this.interpreterBindings = info.interpreterBindings;

    if (info.interpreterRepositories != null) {
      for (RemoteRepository repo : info.interpreterRepositories) {
        if (!depResolver.getRepos().contains(repo)) {
          this.interpreterRepositories.add(repo);
        }
      }
    }
  }

  private void loadInterpreterDependencies(final InterpreterSetting setting) {

    setting.setStatus(InterpreterSetting.Status.DOWNLOADING_DEPENDENCIES);
    interpreterSettings.put(setting.getId(), setting);
    synchronized (interpreterSettings) {
      final Thread t = new Thread() {
        public void run() {
          try {
            // dependencies to prevent library conflict
            File localRepoDir = new File(conf.getInterpreterLocalRepoPath() + "/" +
                setting.getId());
            if (localRepoDir.exists()) {
              FileUtils.cleanDirectory(localRepoDir);
            }

            // load dependencies
            List<Dependency> deps = setting.getDependencies();
            if (deps != null) {
              for (Dependency d : deps) {
                File destDir = new File(conf.getRelativeDir(ConfVars.ZEPPELIN_DEP_LOCALREPO));

                if (d.getExclusions() != null) {
                  depResolver.load(d.getGroupArtifactVersion(), d.getExclusions(),
                      new File(destDir, setting.getId()));
                } else {
                  depResolver.load(d.getGroupArtifactVersion(), new File(destDir, setting.getId()));
                }
              }
            }

            setting.setStatus(InterpreterSetting.Status.READY);
          } catch (Exception e) {
            logger.error(String.format("Error while downloading repos for interpreter group : %s," +
                    " go to interpreter setting page click on edit and save it again to make " +
                    "this interpreter work properly.",
                setting.getGroup()), e);
            setting.setErrorReason(e.getLocalizedMessage());
            setting.setStatus(InterpreterSetting.Status.ERROR);
          } finally {
            interpreterSettings.put(setting.getId(), setting);
          }
        }
      };
      t.start();
    }
  }

  private void saveToFile() throws IOException {
    String jsonString;

    synchronized (interpreterSettings) {
      InterpreterInfoSaving info = new InterpreterInfoSaving();
      info.interpreterBindings = interpreterBindings;
      info.interpreterSettings = interpreterSettings;
      info.interpreterRepositories = interpreterRepositories;

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

  /**
   * Return ordered interpreter setting list.
   * The list does not contain more than one setting from the same interpreter class.
   * Order by InterpreterClass (order defined by ZEPPELIN_INTERPRETERS), Interpreter setting name
   *
   * @return
   */
  public List<String> getDefaultInterpreterSettingList() {
    // this list will contain default interpreter setting list
    List<String> defaultSettings = new LinkedList<>();

    // to ignore the same interpreter group
    Map<String, Boolean> interpreterGroupCheck = new HashMap<>();

    List<InterpreterSetting> sortedSettings = get();

    for (InterpreterSetting setting : sortedSettings) {
      if (defaultSettings.contains(setting.getId())) {
        continue;
      }

      if (!interpreterGroupCheck.containsKey(setting.getName())) {
        defaultSettings.add(setting.getId());
        interpreterGroupCheck.put(setting.getName(), true);
      }
    }
    return defaultSettings;
  }

  List<RegisteredInterpreter> getRegisteredInterpreterList() {
    return new ArrayList<>(Interpreter.registeredInterpreters.values());
  }


  private boolean findDefaultInterpreter(List<InterpreterInfo> infos) {
    for (InterpreterInfo interpreterInfo : infos) {
      if (interpreterInfo.isDefaultInterpreter())
        return true;
    }
    return false;
  }

  public InterpreterSetting createNewSetting(String name, String group,
      List<Dependency> dependencies, InterpreterOption option, Properties p) throws IOException {
    InterpreterSetting setting = createFromInterpreterSettingRef(group);
    setting.setName(name);
    setting.setGroup(group);
    setting.appendDependencies(dependencies);
    setting.setInterpreterOption(option);
    setting.updateProperties(p);
    setting.setInterpreterGroupFactory(this);
    interpreterSettings.put(setting.getId(), setting);
    saveToFile();
    return setting;
  }

  private InterpreterSetting add(String group, InterpreterInfo interpreterInfo,
      Properties properties, String path)
      throws InterpreterException, IOException, RepositoryException {
    ArrayList<InterpreterInfo> infos = new ArrayList<>();
    infos.add(interpreterInfo);
    return add(group, infos, new ArrayList<Dependency>(), defaultOption, properties, path);
  }

  /**
   * @param group    InterpreterSetting reference name
   * @param properties
   * @return
   */
  public InterpreterSetting add(String group, ArrayList<InterpreterInfo> interpreterInfos,
      List<Dependency> dependencies, InterpreterOption option, Properties properties, String path) {
    Preconditions.checkNotNull(group, "name should not be null");
    Preconditions.checkNotNull(interpreterInfos, "interpreterInfos should not be null");
    Preconditions.checkNotNull(dependencies, "dependencies should not be null");
    Preconditions.checkNotNull(option, "option should not be null");
    Preconditions.checkNotNull(properties, "properties should not be null");

    InterpreterSetting interpreterSetting;

    synchronized (interpreterSettingsRef) {
      if (interpreterSettingsRef.containsKey(group)) {
        interpreterSetting = interpreterSettingsRef.get(group);

        // Append InterpreterInfo
        List<InterpreterInfo> infos = interpreterSetting.getInterpreterInfos();
        boolean hasDefaultInterpreter = findDefaultInterpreter(infos);
        for (InterpreterInfo interpreterInfo : interpreterInfos) {
          if (!infos.contains(interpreterInfo)) {
            if (!hasDefaultInterpreter && interpreterInfo.isDefaultInterpreter()) {
              hasDefaultInterpreter = true;
              infos.add(0, interpreterInfo);
            } else {
              infos.add(interpreterInfo);
            }
          }
        }

        // Append dependencies
        List<Dependency> dependencyList = interpreterSetting.getDependencies();
        for (Dependency dependency : dependencies) {
          if (!dependencyList.contains(dependency)) {
            dependencyList.add(dependency);
          }
        }

        // Append properties
        Properties interpreterProperties = interpreterSetting.getProperties();
        for (String key : properties.stringPropertyNames()) {
          if (!interpreterProperties.containsKey(key)) {
            interpreterProperties.setProperty(key, properties.getProperty(key));
          }
        }

      } else {
        interpreterSetting =
            new InterpreterSetting(group, null, interpreterInfos, properties, dependencies,
                option, path);
        interpreterSettingsRef.put(group, interpreterSetting);
      }
    }


    if (dependencies.size() > 0) {
      loadInterpreterDependencies(interpreterSetting);
    }

    interpreterSetting.setInterpreterGroupFactory(this);
    return interpreterSetting;
  }

  @Override
  public InterpreterGroup createInterpreterGroup(String id, InterpreterOption option)
      throws InterpreterException, NullArgumentException {

    //When called from REST API without option we receive NPE
    if (option == null)
      throw new NullArgumentException("option");

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

  public void removeInterpretersForNote(InterpreterSetting interpreterSetting, String noteId) {
    if (interpreterSetting.getOption().isPerNoteProcess()) {
      interpreterSetting.closeAndRemoveInterpreterGroup(noteId);
    } else if (interpreterSetting.getOption().isPerNoteSession()) {
      InterpreterGroup interpreterGroup = interpreterSetting.getInterpreterGroup(noteId);

      interpreterGroup.close(noteId);
      interpreterGroup.destroy(noteId);
      synchronized (interpreterGroup) {
        interpreterGroup.remove(noteId);
        interpreterGroup.notifyAll(); // notify createInterpreterForNote()
      }
      logger.info("Interpreter instance {} for note {} is removed", interpreterSetting.getName(),
          noteId);
    }
  }

  public void createInterpretersForNote(InterpreterSetting interpreterSetting, String noteId,
      String key) {
    InterpreterGroup interpreterGroup = interpreterSetting.getInterpreterGroup(noteId);
    InterpreterOption option = interpreterSetting.getOption();
    Properties properties = interpreterSetting.getProperties();
    if (option.isExistingProcess) {
      properties.put(Constants.ZEPPELIN_INTERPRETER_HOST, option.getHost());
      properties.put(Constants.ZEPPELIN_INTERPRETER_PORT, option.getPort());
    }
    // if interpreters are already there, wait until they're being removed
    synchronized (interpreterGroup) {
      long interpreterRemovalWaitStart = System.nanoTime();
      // interpreter process supposed to be terminated by RemoteInterpreterProcess.dereference()
      // in ZEPPELIN_INTERPRETER_CONNECT_TIMEOUT msec. However, if termination of the process and
      // removal from interpreter group take too long, throw an error.
      long minTimeout = 10L * 1000 * 1000000; // 10 sec
      long interpreterRemovalWaitTimeout = Math.max(minTimeout,
          conf.getInt(ConfVars.ZEPPELIN_INTERPRETER_CONNECT_TIMEOUT) * 1000000L * 2);
      while (interpreterGroup.containsKey(key)) {
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
    Interpreter interpreter;
    for (InterpreterInfo info : interpreterInfos) {
      if (option.isRemote()) {
        if (option.isConnectExistingProcess()) {
          interpreter =
              connectToRemoteRepl(noteId, info.getClassName(), option.getHost(), option.getPort(),
                  properties);
        } else {
          interpreter = createRemoteRepl(path, key, info.getClassName(), properties,
              interpreterSetting.getId());
        }
      } else {
        interpreter = createRepl(interpreterSetting.getPath(), info.getClassName(), properties);
      }

      synchronized (interpreterGroup) {
        List<Interpreter> interpreters = interpreterGroup.get(key);
        if (null == interpreters) {
          interpreters = new ArrayList<>();
          interpreterGroup.put(key, interpreters);
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


  public void remove(String id) throws IOException {
    synchronized (interpreterSettings) {
      if (interpreterSettings.containsKey(id)) {
        InterpreterSetting intp = interpreterSettings.get(id);
        intp.closeAndRmoveAllInterpreterGroups();

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

    File localRepoDir = new File(conf.getInterpreterLocalRepoPath() + "/" + id);
    FileUtils.deleteDirectory(localRepoDir);
  }

  /**
   * Get interpreter settings
   *
   * @return
   */
  public List<InterpreterSetting> get() {
    synchronized (interpreterSettings) {
      List<InterpreterSetting> orderedSettings = new LinkedList<>();

      Map<String, List<InterpreterSetting>> nameInterpreterSettingMap = new HashMap<>();
      for (InterpreterSetting interpreterSetting : interpreterSettings.values()) {
        String name = interpreterSetting.getName();
        if (!nameInterpreterSettingMap.containsKey(name)) {
          nameInterpreterSettingMap.put(name, new ArrayList<InterpreterSetting>());
        }
        nameInterpreterSettingMap.get(name).add(interpreterSetting);
      }

      for (String groupName : interpreterGroupOrderList) {
        List<InterpreterSetting> interpreterSettingList =
            nameInterpreterSettingMap.remove(groupName);
        if (null != interpreterSettingList) {
          for (InterpreterSetting interpreterSetting : interpreterSettingList) {
            orderedSettings.add(interpreterSetting);
          }
        }
      }

      List<InterpreterSetting> settings = new ArrayList<>();

      for (List<InterpreterSetting> interpreterSettingList : nameInterpreterSettingMap.values()) {
        for (InterpreterSetting interpreterSetting : interpreterSettingList) {
          settings.add(interpreterSetting);
        }
      }

      Collections.sort(settings, new Comparator<InterpreterSetting>() {
        @Override
        public int compare(InterpreterSetting o1, InterpreterSetting o2) {
          return o1.getName().compareTo(o2.getName());
        }
      });

      orderedSettings.addAll(settings);

      return orderedSettings;
    }
  }

  public InterpreterSetting get(String name) {
    synchronized (interpreterSettings) {
      return interpreterSettings.get(name);
    }
  }

  private void putNoteInterpreterSettingBinding(String noteId, List<String> settingList)
      throws IOException {
    List<String> unBindedSettings = new LinkedList<>();

    synchronized (interpreterSettings) {
      List<String> oldSettings = interpreterBindings.get(noteId);
      if (oldSettings != null) {
        for (String oldSettingId : oldSettings) {
          if (!settingList.contains(oldSettingId)) {
            unBindedSettings.add(oldSettingId);
          }
        }
      }
      interpreterBindings.put(noteId, settingList);
      saveToFile();

      for (String settingId : unBindedSettings) {
        InterpreterSetting setting = get(settingId);
        removeInterpretersForNote(setting, noteId);
      }
    }
  }

  public void removeNoteInterpreterSettingBinding(String noteId) {
    synchronized (interpreterSettings) {
      List<String> settingIds = (interpreterBindings.containsKey(noteId) ?
          interpreterBindings.remove(noteId) :
          Collections.<String>emptyList());
      for (String settingId : settingIds) {
        this.removeInterpretersForNote(get(settingId), noteId);
      }
    }
  }

  private List<String> getNoteInterpreterSettingBinding(String noteId) {
    LinkedList<String> bindings = new LinkedList<>();
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
   *
   * @param id
   * @param option
   * @param properties
   * @throws IOException
   */
  public void setPropertyAndRestart(String id, InterpreterOption option, Properties properties,
      List<Dependency> dependencies) throws IOException {
    synchronized (interpreterSettings) {
      InterpreterSetting intpsetting = interpreterSettings.get(id);
      if (intpsetting != null) {

        stopJobAllInterpreter(intpsetting);

        intpsetting.closeAndRmoveAllInterpreterGroups();

        intpsetting.setOption(option);
        intpsetting.updateProperties(properties);
        intpsetting.setDependencies(dependencies);

        loadInterpreterDependencies(intpsetting);
        saveToFile();
      } else {
        throw new InterpreterException("Interpreter setting id " + id + " not found");
      }
    }
  }

  public void restart(String id) {
    synchronized (interpreterSettings) {
      InterpreterSetting intpsetting = interpreterSettings.get(id);
      if (intpsetting != null) {

        stopJobAllInterpreter(intpsetting);

        intpsetting.closeAndRmoveAllInterpreterGroups();

      } else {
        throw new InterpreterException("Interpreter setting id " + id + " not found");
      }
    }
  }

  private void stopJobAllInterpreter(InterpreterSetting intpsetting) {
    if (intpsetting != null) {
      for (InterpreterGroup intpGroup : intpsetting.getAllInterpreterGroups()) {
        for (List<Interpreter> interpreters : intpGroup.values()) {
          for (Interpreter intp : interpreters) {
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
    }
  }

  public void close() {
    List<Thread> closeThreads = new LinkedList<>();
    synchronized (interpreterSettings) {
      Collection<InterpreterSetting> intpsettings = interpreterSettings.values();
      for (final InterpreterSetting intpsetting : intpsettings) {
        Thread t = new Thread() {
          public void run() {
            intpsetting.closeAndRmoveAllInterpreterGroups();
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

  private Interpreter createRepl(String dirName, String className, Properties property)
      throws InterpreterException {
    logger.info("Create repl {} from {}", className, dirName);

    updatePropertiesFromRegisteredInterpreter(property, className);

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
        logger.error("exception checking server classloader driver", e);
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

  private Interpreter connectToRemoteRepl(String noteId, String className, String host, int port,
      Properties property) {
    int connectTimeout = conf.getInt(ConfVars.ZEPPELIN_INTERPRETER_CONNECT_TIMEOUT);
    int maxPoolSize = conf.getInt(ConfVars.ZEPPELIN_INTERPRETER_MAX_POOL_SIZE);
    LazyOpenInterpreter intp = new LazyOpenInterpreter(
        new RemoteInterpreter(property, noteId, className, host, port, connectTimeout, maxPoolSize,
            remoteInterpreterProcessListener, appEventListener));
    return intp;
  }

  private Interpreter createRemoteRepl(String interpreterPath, String noteId, String className,
      Properties property, String interpreterSettingId) {
    int connectTimeout = conf.getInt(ConfVars.ZEPPELIN_INTERPRETER_CONNECT_TIMEOUT);
    String localRepoPath = conf.getInterpreterLocalRepoPath() + "/" + interpreterSettingId;
    int maxPoolSize = conf.getInt(ConfVars.ZEPPELIN_INTERPRETER_MAX_POOL_SIZE);

    updatePropertiesFromRegisteredInterpreter(property, className);


    RemoteInterpreter remoteInterpreter =
        new RemoteInterpreter(property, noteId, className, conf.getInterpreterRemoteRunnerPath(),
            interpreterPath, localRepoPath, connectTimeout, maxPoolSize,
            remoteInterpreterProcessListener, appEventListener);
    remoteInterpreter.setEnv(env);

    return new LazyOpenInterpreter(remoteInterpreter);
  }

  private Properties updatePropertiesFromRegisteredInterpreter(Properties properties,
      String className) {
    RegisteredInterpreter registeredInterpreter =
        Interpreter.findRegisteredInterpreterByClassName(className);
    if (null != registeredInterpreter) {
      Map<String, InterpreterProperty> defaultProperties = registeredInterpreter.getProperties();
      for (String key : defaultProperties.keySet()) {
        if (!properties.containsKey(key) && null != defaultProperties.get(key).getValue()) {
          properties.setProperty(key, defaultProperties.get(key).getValue());
        }
      }
    }

    return properties;
  }

  /**
   * map interpreter ids into noteId
   *
   * @param noteId note id
   * @param ids    InterpreterSetting id list
   * @throws IOException
   */
  public void setInterpreters(String noteId, List<String> ids) throws IOException {
    putNoteInterpreterSettingBinding(noteId, ids);
  }

  public List<String> getInterpreters(String noteId) {
    return getNoteInterpreterSettingBinding(noteId);
  }

  public List<InterpreterSetting> getInterpreterSettings(String noteId) {
    List<String> interpreterSettingIds = getNoteInterpreterSettingBinding(noteId);
    LinkedList<InterpreterSetting> settings = new LinkedList<>();
    synchronized (interpreterSettingIds) {
      for (String id : interpreterSettingIds) {
        InterpreterSetting setting = get(id);
        if (setting == null) {
          // interpreter setting is removed from factory. remove id from here, too
          interpreterSettingIds.remove(id);
        } else {
          settings.add(setting);
        }
      }
    }
    return settings;
  }

  public void closeNote(String noteId) {
    // close interpreters in this note session
    List<InterpreterSetting> settings = getInterpreterSettings(noteId);
    if (settings == null || settings.size() == 0) {
      return;
    }

    logger.info("closeNote: {}", noteId);
    for (InterpreterSetting setting : settings) {
      removeInterpretersForNote(setting, noteId);
    }
  }

  private String getInterpreterInstanceKey(String noteId, InterpreterSetting setting) {
    if (setting.getOption().isExistingProcess()) {
      return Constants.EXISTING_PROCESS;
    } else if (setting.getOption().isPerNoteSession() || setting.getOption().isPerNoteProcess()) {
      return noteId;
    } else {
      return SHARED_SESSION;
    }
  }

  private List<Interpreter> createOrGetInterpreterList(String noteId, InterpreterSetting setting) {
    InterpreterGroup interpreterGroup = setting.getInterpreterGroup(noteId);
    synchronized (interpreterGroup) {
      String key = getInterpreterInstanceKey(noteId, setting);
      if (!interpreterGroup.containsKey(key)) {
        createInterpretersForNote(setting, noteId, key);
      }
      return interpreterGroup.get(getInterpreterInstanceKey(noteId, setting));
    }
  }

  private InterpreterSetting getDefaultInterpreterSetting(List<InterpreterSetting> settings) {
    if (settings == null || settings.isEmpty()) {
      return null;
    }
    return settings.get(0);
  }

  public InterpreterSetting getDefaultInterpreterSetting(String noteId) {
    return getDefaultInterpreterSetting(getInterpreterSettings(noteId));
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

  private Interpreter getInterpreter(String noteId, InterpreterSetting setting, String name) {
    Preconditions.checkNotNull(noteId, "noteId should be not null");
    Preconditions.checkNotNull(setting, "setting should be not null");
    Preconditions.checkNotNull(name, "name should be not null");

    String className;
    if (null != (className = getInterpreterClassFromInterpreterSetting(setting, name))) {
      List<Interpreter> interpreterGroup = createOrGetInterpreterList(noteId, setting);
      for (Interpreter interpreter : interpreterGroup) {
        if (className.equals(interpreter.getClassName())) {
          return interpreter;
        }
      }
    }
    return null;
  }

  public Interpreter getInterpreter(String noteId, String replName) {
    List<InterpreterSetting> settings = getInterpreterSettings(noteId);
    InterpreterSetting setting;
    Interpreter interpreter;

    if (settings == null || settings.size() == 0) {
      return null;
    }

    if (replName == null || replName.trim().length() == 0) {
      // get default settings (first available)
      // TODO(jl): Fix it in case of returning null
      InterpreterSetting defaultSettings = getDefaultInterpreterSetting(settings);
      return createOrGetInterpreterList(noteId, defaultSettings).get(0);
    }

    String[] replNameSplit = replName.split("\\.");
    if (replNameSplit.length == 2) {
      String group = null;
      String name = null;
      group = replNameSplit[0];
      name = replNameSplit[1];

      setting = getInterpreterSettingByGroup(settings, group);

      if (null != setting) {
        interpreter = getInterpreter(noteId, setting, name);

        if (null != interpreter) {
          return interpreter;
        }
      }

      throw new InterpreterException(replName + " interpreter not found");

    } else {
      // first assume replName is 'name' of interpreter. ('groupName' is ommitted)
      // search 'name' from first (default) interpreter group
      // TODO(jl): Handle with noteId to support defaultInterpreter per note.
      setting = getDefaultInterpreterSetting(settings);

      interpreter = getInterpreter(noteId, setting, replName);

      if (null != interpreter) {
        return interpreter;
      }

      // next, assume replName is 'group' of interpreter ('name' is ommitted)
      // search interpreter group and return first interpreter.
      setting = getInterpreterSettingByGroup(settings, replName);

      if (null != setting) {
        List<Interpreter> interpreters = createOrGetInterpreterList(noteId, setting);
        if (null != interpreters) {
          return interpreters.get(0);
        }
      }
    }

    // dev interpreter
    if (DevInterpreter.isInterpreterName(replName)) {
      return getDevInterpreter();
    }

    return null;
  }

  public Map<String, InterpreterSetting> getAvailableInterpreterSettings() {
    return interpreterSettingsRef;
  }

  private URL[] recursiveBuildLibList(File path) throws MalformedURLException {
    URL[] urls = new URL[0];
    if (path == null || !path.exists()) {
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

  public List<RemoteRepository> getRepositories() {
    return this.interpreterRepositories;
  }

  public void addRepository(String id, String url, boolean snapshot, Authentication auth)
      throws IOException {
    depResolver.addRepo(id, url, snapshot, auth);
    saveToFile();
  }

  public void removeRepository(String id) throws IOException {
    depResolver.delRepo(id);
    saveToFile();
  }

  public Map<String, String> getEnv() {
    return env;
  }

  public void setEnv(Map<String, String> env) {
    this.env = env;
  }


  private Interpreter getDevInterpreter() {
    if (devInterpreter == null) {
      InterpreterOption option = new InterpreterOption();
      option.setRemote(true);

      InterpreterGroup interpreterGroup = createInterpreterGroup("dev", option);

      devInterpreter = connectToRemoteRepl("dev", DevInterpreter.class.getName(), "localhost",
          ZeppelinDevServer.DEFAULT_TEST_INTERPRETER_PORT, new Properties());

      LinkedList<Interpreter> intpList = new LinkedList<>();
      intpList.add(devInterpreter);
      interpreterGroup.put("dev", intpList);

      devInterpreter.setInterpreterGroup(interpreterGroup);
    }
    return devInterpreter;
  }
}
