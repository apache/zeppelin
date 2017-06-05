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

import static java.nio.file.attribute.PosixFilePermission.OWNER_READ;
import static java.nio.file.attribute.PosixFilePermission.OWNER_WRITE;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.internal.StringMap;
import com.google.gson.reflect.TypeToken;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.reflect.Type;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream.Filter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.zeppelin.conf.ZeppelinConfiguration.ConfVars;
import org.apache.zeppelin.dep.Dependency;
import org.apache.zeppelin.dep.DependencyResolver;
import org.apache.zeppelin.interpreter.Interpreter.RegisteredInterpreter;
import org.apache.zeppelin.scheduler.Job;
import org.apache.zeppelin.scheduler.Job.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.sonatype.aether.RepositoryException;
import org.sonatype.aether.repository.Authentication;
import org.sonatype.aether.repository.Proxy;
import org.sonatype.aether.repository.RemoteRepository;

/**
 * TBD
 */
public class InterpreterSettingManager {

  private static final Logger logger = LoggerFactory.getLogger(InterpreterSettingManager.class);
  private static final String SHARED_SESSION = "shared_session";
  private static final Map<String, Object> DEFAULT_EDITOR = ImmutableMap.of(
      "language", (Object) "text",
      "editOnDblClick", false);

  private final ZeppelinConfiguration zeppelinConfiguration;
  private final Path interpreterDirPath;
  private final Path interpreterBindingPath;

  /**
   * This is only references with default settings, name and properties
   * key: InterpreterSetting.name
   */
  private final Map<String, InterpreterSetting> interpreterSettingsRef;
  /**
   * This is used by creating and running Interpreters
   * key: InterpreterSetting.id <- This is becuase backward compatibility
   */
  private final Map<String, InterpreterSetting> interpreterSettings;
  private final Map<String, List<String>> interpreterBindings;

  private final DependencyResolver dependencyResolver;
  private final List<RemoteRepository> interpreterRepositories;

  private final InterpreterOption defaultOption;

  private final Map<String, URLClassLoader> cleanCl;

  @Deprecated
  private String[] interpreterClassList;
  private String[] interpreterGroupOrderList;
  private InterpreterGroupFactory interpreterGroupFactory;

  private final Gson gson;

  public InterpreterSettingManager(ZeppelinConfiguration zeppelinConfiguration,
      DependencyResolver dependencyResolver, InterpreterOption interpreterOption)
      throws IOException, RepositoryException {
    this.zeppelinConfiguration = zeppelinConfiguration;
    this.interpreterDirPath = Paths.get(zeppelinConfiguration.getInterpreterDir());
    logger.debug("InterpreterRootPath: {}", interpreterDirPath);
    this.interpreterBindingPath = Paths.get(zeppelinConfiguration.getInterpreterSettingPath());
    logger.debug("InterpreterBindingPath: {}", interpreterBindingPath);

    this.interpreterSettingsRef = Maps.newConcurrentMap();
    this.interpreterSettings = Maps.newConcurrentMap();
    this.interpreterBindings = Maps.newConcurrentMap();

    this.dependencyResolver = dependencyResolver;
    this.interpreterRepositories = dependencyResolver.getRepos();

    this.defaultOption = interpreterOption;

    this.cleanCl = Collections.synchronizedMap(new HashMap<String, URLClassLoader>());

    String replsConf = zeppelinConfiguration.getString(ConfVars.ZEPPELIN_INTERPRETERS);
    this.interpreterClassList = replsConf.split(",");
    String groupOrder = zeppelinConfiguration.getString(ConfVars.ZEPPELIN_INTERPRETER_GROUP_ORDER);
    this.interpreterGroupOrderList = groupOrder.split(",");

    GsonBuilder gsonBuilder = new GsonBuilder();
    gsonBuilder.setPrettyPrinting();
    this.gson = gsonBuilder.create();

    init();
  }

  /**
   * Remember this method doesn't keep current connections after being called
   */
  private void  loadFromFile() {
    if (!Files.exists(interpreterBindingPath)) {
      // nothing to read
      return;
    }
    InterpreterInfoSaving infoSaving;
    try (BufferedReader json =
        Files.newBufferedReader(interpreterBindingPath, StandardCharsets.UTF_8)) {
      infoSaving = gson.fromJson(json, InterpreterInfoSaving.class);

      for (String k : infoSaving.interpreterSettings.keySet()) {
        InterpreterSetting setting = infoSaving.interpreterSettings.get(k);
        List<InterpreterInfo> infos = setting.getInterpreterInfos();

        // Convert json StringMap to Properties
        StringMap<String> p = (StringMap<String>) setting.getProperties();
        Properties properties = new Properties();
        for (String key : p.keySet()) {
          properties.put(key, p.get(key));
        }
        setting.setProperties(properties);

        // Always use separate interpreter process
        // While we decided to turn this feature on always (without providing
        // enable/disable option on GUI).
        // previously created setting should turn this feature on here.
        setting.getOption().setRemote(true);

        // Update transient information from InterpreterSettingRef
        InterpreterSetting interpreterSettingObject =
            interpreterSettingsRef.get(setting.getGroup());
        if (interpreterSettingObject == null) {
          logger.warn("can't get InterpreterSetting " +
              "Information From loaded Interpreter Setting Ref - {} ", setting.getGroup());
          continue;
        }
        String depClassPath = interpreterSettingObject.getPath();
        setting.setPath(depClassPath);

        for (InterpreterInfo info : infos) {
          if (info.getEditor() == null) {
            Map<String, Object> editor = getEditorFromSettingByClassName(interpreterSettingObject,
                info.getClassName());
            info.setEditor(editor);
          }
        }

        setting.setInterpreterGroupFactory(interpreterGroupFactory);

        loadInterpreterDependencies(setting);
        interpreterSettings.put(k, setting);
      }

      interpreterBindings.putAll(infoSaving.interpreterBindings);

      if (infoSaving.interpreterRepositories != null) {
        for (RemoteRepository repo : infoSaving.interpreterRepositories) {
          if (!dependencyResolver.getRepos().contains(repo)) {
            this.interpreterRepositories.add(repo);
          }
        }
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public void saveToFile() throws IOException {
    String jsonString;

    synchronized (interpreterSettings) {
      InterpreterInfoSaving info = new InterpreterInfoSaving();
      info.interpreterBindings = interpreterBindings;
      info.interpreterSettings = interpreterSettings;
      info.interpreterRepositories = interpreterRepositories;

      jsonString = gson.toJson(info);
    }

    if (!Files.exists(interpreterBindingPath)) {
      Files.createFile(interpreterBindingPath);

      try {
        Set<PosixFilePermission> permissions = EnumSet.of(OWNER_READ, OWNER_WRITE);
        Files.setPosixFilePermissions(interpreterBindingPath, permissions);
      } catch (UnsupportedOperationException e) {
        // File system does not support Posix file permissions (likely windows) - continue anyway.
        logger.warn("unable to setPosixFilePermissions on '{}'.", interpreterBindingPath);
      };
    }

    FileOutputStream fos = new FileOutputStream(interpreterBindingPath.toFile(), false);
    OutputStreamWriter out = new OutputStreamWriter(fos);
    out.append(jsonString);
    out.close();
    fos.close();
  }

  //TODO(jl): Fix it to remove InterpreterGroupFactory
  public void setInterpreterGroupFactory(InterpreterGroupFactory interpreterGroupFactory) {
    for (InterpreterSetting setting : interpreterSettings.values()) {
      setting.setInterpreterGroupFactory(interpreterGroupFactory);
    }
    this.interpreterGroupFactory = interpreterGroupFactory;
  }

  private void init() throws InterpreterException, IOException, RepositoryException {
    String interpreterJson = zeppelinConfiguration.getInterpreterJson();
    ClassLoader cl = Thread.currentThread().getContextClassLoader();

    if (Files.exists(interpreterDirPath)) {
      for (Path interpreterDir : Files
          .newDirectoryStream(interpreterDirPath, new Filter<Path>() {
            @Override
            public boolean accept(Path entry) throws IOException {
              return Files.exists(entry) && Files.isDirectory(entry);
            }
          })) {
        String interpreterDirString = interpreterDir.toString();

        /**
         * Register interpreter by the following ordering
         * 1. Register it from path {ZEPPELIN_HOME}/interpreter/{interpreter_name}/
         *    interpreter-setting.json
         * 2. Register it from interpreter-setting.json in classpath
         *    {ZEPPELIN_HOME}/interpreter/{interpreter_name}
         * 3. Register it by Interpreter.register
         */
        if (!registerInterpreterFromPath(interpreterDirString, interpreterJson)) {
          if (!registerInterpreterFromResource(cl, interpreterDirString, interpreterJson)) {
            /*
             * TODO(jongyoul)
             * - Remove these codes below because of legacy code
             * - Support ThreadInterpreter
            */
            URLClassLoader ccl = new URLClassLoader(
                recursiveBuildLibList(interpreterDir.toFile()), cl);
            for (String className : interpreterClassList) {
              try {
                // Load classes
                Class.forName(className, true, ccl);
                Set<String> interpreterKeys = Interpreter.registeredInterpreters.keySet();
                for (String interpreterKey : interpreterKeys) {
                  if (className
                      .equals(Interpreter.registeredInterpreters.get(interpreterKey)
                          .getClassName())) {
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
          new InterpreterInfo(r.getClassName(), r.getName(), r.isDefaultInterpreter(),
              r.getEditor());
      add(r.getGroup(), interpreterInfo, r.getProperties(), defaultOption, r.getPath(),
          r.getRunner());
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

  private boolean registerInterpreterFromResource(ClassLoader cl, String interpreterDir,
      String interpreterJson) throws IOException, RepositoryException {
    URL[] urls = recursiveBuildLibList(new File(interpreterDir));
    ClassLoader tempClassLoader = new URLClassLoader(urls, cl);

    Enumeration<URL> interpreterSettings = tempClassLoader.getResources(interpreterJson);
    if (!interpreterSettings.hasMoreElements()) {
      return false;
    }
    for (URL url : Collections.list(interpreterSettings)) {
      try (InputStream inputStream = url.openStream()) {
        logger.debug("Reading {} from {}", interpreterJson, url);
        List<RegisteredInterpreter> registeredInterpreterList =
            getInterpreterListFromJson(inputStream);
        registerInterpreters(registeredInterpreterList, interpreterDir);
      }
    }
    return true;
  }

  private boolean registerInterpreterFromPath(String interpreterDir, String interpreterJson)
      throws IOException, RepositoryException {

    Path interpreterJsonPath = Paths.get(interpreterDir, interpreterJson);
    if (Files.exists(interpreterJsonPath)) {
      logger.debug("Reading {}", interpreterJsonPath);
      List<RegisteredInterpreter> registeredInterpreterList =
          getInterpreterListFromJson(interpreterJsonPath);
      registerInterpreters(registeredInterpreterList, interpreterDir);
      return true;
    }
    return false;
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
              registeredInterpreter.isDefaultInterpreter(), registeredInterpreter.getEditor());
      // use defaultOption if it is not specified in interpreter-setting.json
      InterpreterOption option = registeredInterpreter.getOption() == null ? defaultOption :
          registeredInterpreter.getOption();
      add(registeredInterpreter.getGroup(), interpreterInfo, registeredInterpreter.getProperties(),
          option, absolutePath, registeredInterpreter.getRunner());
    }

  }

  public InterpreterSetting getDefaultInterpreterSetting(List<InterpreterSetting> settings) {
    if (settings == null || settings.isEmpty()) {
      return null;
    }
    return settings.get(0);
  }

  public InterpreterSetting getDefaultInterpreterSetting(String noteId) {
    return getDefaultInterpreterSetting(getInterpreterSettings(noteId));
  }

  public List<InterpreterSetting> getInterpreterSettings(String noteId) {
    List<String> interpreterSettingIds = getNoteInterpreterSettingBinding(noteId);
    LinkedList<InterpreterSetting> settings = new LinkedList<>();

    Iterator<String> iter = interpreterSettingIds.iterator();
    while (iter.hasNext()) {
      String id = iter.next();
      InterpreterSetting setting = get(id);
      if (setting == null) {
        // interpreter setting is removed from factory. remove id from here, too
        iter.remove();
      } else {
        settings.add(setting);
      }
    }
    return settings;
  }

  private List<String> getNoteInterpreterSettingBinding(String noteId) {
    LinkedList<String> bindings = new LinkedList<>();
    List<String> settingIds = interpreterBindings.get(noteId);
    if (settingIds != null) {
      bindings.addAll(settingIds);
    }
    return bindings;
  }

  private InterpreterSetting createFromInterpreterSettingRef(String name) {
    Preconditions.checkNotNull(name, "reference name should be not null");
    InterpreterSetting settingRef = interpreterSettingsRef.get(name);
    return createFromInterpreterSettingRef(settingRef);
  }

  private InterpreterSetting createFromInterpreterSettingRef(InterpreterSetting o) {
    // should return immutable objects
    List<InterpreterInfo> infos = (null == o.getInterpreterInfos()) ?
        new ArrayList<InterpreterInfo>() : new ArrayList<>(o.getInterpreterInfos());
    List<Dependency> deps = (null == o.getDependencies()) ?
        new ArrayList<Dependency>() : new ArrayList<>(o.getDependencies());
    Properties props =
        convertInterpreterProperties((Map<String, InterpreterProperty>) o.getProperties());
    InterpreterOption option = InterpreterOption.fromInterpreterOption(o.getOption());

    InterpreterSetting setting = new InterpreterSetting(o.getName(), o.getName(),
        infos, props, deps, option, o.getPath(), o.getInterpreterRunner());
    setting.setInterpreterGroupFactory(interpreterGroupFactory);
    return setting;
  }

  private Properties convertInterpreterProperties(Map<String, InterpreterProperty> p) {
    Properties properties = new Properties();
    for (String key : p.keySet()) {
      properties.put(key, p.get(key).getValue());
    }
    return properties;
  }

  public Map<String, Object> getEditorSetting(Interpreter interpreter, String user, String noteId,
      String replName) {
    Map<String, Object> editor = DEFAULT_EDITOR;
    String group = StringUtils.EMPTY;
    try {
      String defaultSettingName = getDefaultInterpreterSetting(noteId).getName();
      List<InterpreterSetting> intpSettings = getInterpreterSettings(noteId);
      for (InterpreterSetting intpSetting : intpSettings) {
        String[] replNameSplit = replName.split("\\.");
        if (replNameSplit.length == 2) {
          group = replNameSplit[0];
        }
        // when replName is 'name' of interpreter
        if (defaultSettingName.equals(intpSetting.getName())) {
          editor = getEditorFromSettingByClassName(intpSetting, interpreter.getClassName());
        }
        // when replName is 'alias name' of interpreter or 'group' of interpreter
        if (replName.equals(intpSetting.getName()) || group.equals(intpSetting.getName())) {
          editor = getEditorFromSettingByClassName(intpSetting, interpreter.getClassName());
          break;
        }
      }
    } catch (NullPointerException e) {
      // Use `debug` level because this log occurs frequently
      logger.debug("Couldn't get interpreter editor setting");
    }
    return editor;
  }

  public Map<String, Object> getEditorFromSettingByClassName(InterpreterSetting intpSetting,
      String className) {
    List<InterpreterInfo> intpInfos = intpSetting.getInterpreterInfos();
    for (InterpreterInfo intpInfo : intpInfos) {

      if (className.equals(intpInfo.getClassName())) {
        if (intpInfo.getEditor() == null) {
          break;
        }
        return intpInfo.getEditor();
      }
    }
    return DEFAULT_EDITOR;
  }

  private void loadInterpreterDependencies(final InterpreterSetting setting) {
    setting.setStatus(InterpreterSetting.Status.DOWNLOADING_DEPENDENCIES);
    setting.setErrorReason(null);
    interpreterSettings.put(setting.getId(), setting);
    synchronized (interpreterSettings) {
      final Thread t = new Thread() {
        public void run() {
          try {
            // dependencies to prevent library conflict
            File localRepoDir = new File(zeppelinConfiguration.getInterpreterLocalRepoPath() + "/" +
                setting.getId());
            if (localRepoDir.exists()) {
              try {
                FileUtils.forceDelete(localRepoDir);
              } catch (FileNotFoundException e) {
                logger.info("A file that does not exist cannot be deleted, nothing to worry", e);
              }
            }

            // load dependencies
            List<Dependency> deps = setting.getDependencies();
            if (deps != null) {
              for (Dependency d : deps) {
                File destDir = new File(
                    zeppelinConfiguration.getRelativeDir(ConfVars.ZEPPELIN_DEP_LOCALREPO));

                if (d.getExclusions() != null) {
                  dependencyResolver.load(d.getGroupArtifactVersion(), d.getExclusions(),
                      new File(destDir, setting.getId()));
                } else {
                  dependencyResolver
                      .load(d.getGroupArtifactVersion(), new File(destDir, setting.getId()));
                }
              }
            }

            setting.setStatus(InterpreterSetting.Status.READY);
            setting.setErrorReason(null);
          } catch (Exception e) {
            logger.error(String.format("Error while downloading repos for interpreter group : %s," +
                    " go to interpreter setting page click on edit and save it again to make " +
                    "this interpreter work properly. : %s",
                setting.getGroup(), e.getLocalizedMessage()), e);
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

  /**
   * Overwrite dependency jar under local-repo/{interpreterId}
   * if jar file in original path is changed
   */
  private void copyDependenciesFromLocalPath(final InterpreterSetting setting) {
    setting.setStatus(InterpreterSetting.Status.DOWNLOADING_DEPENDENCIES);
    interpreterSettings.put(setting.getId(), setting);
    synchronized (interpreterSettings) {
      final Thread t = new Thread() {
        public void run() {
          try {
            List<Dependency> deps = setting.getDependencies();
            if (deps != null) {
              for (Dependency d : deps) {
                File destDir = new File(
                    zeppelinConfiguration.getRelativeDir(ConfVars.ZEPPELIN_DEP_LOCALREPO));

                int numSplits = d.getGroupArtifactVersion().split(":").length;
                if (!(numSplits >= 3 && numSplits <= 6)) {
                  dependencyResolver.copyLocalDependency(d.getGroupArtifactVersion(),
                      new File(destDir, setting.getId()));
                }
              }
            }
            setting.setStatus(InterpreterSetting.Status.READY);
          } catch (Exception e) {
            logger.error(String.format("Error while copying deps for interpreter group : %s," +
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

  /**
   * Return ordered interpreter setting list.
   * The list does not contain more than one setting from the same interpreter class.
   * Order by InterpreterClass (order defined by ZEPPELIN_INTERPRETERS), Interpreter setting name
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
      if (interpreterInfo.isDefaultInterpreter()) {
        return true;
      }
    }
    return false;
  }

  public InterpreterSetting createNewSetting(String name, String group,
      List<Dependency> dependencies, InterpreterOption option, Properties p) throws IOException {
    if (name.indexOf(".") >= 0) {
      throw new IOException("'.' is invalid for InterpreterSetting name.");
    }
    InterpreterSetting setting = createFromInterpreterSettingRef(group);
    setting.setName(name);
    setting.setGroup(group);
    setting.appendDependencies(dependencies);
    setting.setInterpreterOption(option);
    setting.setProperties(p);
    setting.setInterpreterGroupFactory(interpreterGroupFactory);
    interpreterSettings.put(setting.getId(), setting);
    loadInterpreterDependencies(setting);
    saveToFile();
    return setting;
  }

  private InterpreterSetting add(String group, InterpreterInfo interpreterInfo,
      Map<String, InterpreterProperty> interpreterProperties, InterpreterOption option, String path,
      InterpreterRunner runner)
      throws InterpreterException, IOException, RepositoryException {
    ArrayList<InterpreterInfo> infos = new ArrayList<>();
    infos.add(interpreterInfo);
    return add(group, infos, new ArrayList<Dependency>(), option, interpreterProperties, path,
        runner);
  }

  /**
   * @param group InterpreterSetting reference name
   */
  public InterpreterSetting add(String group, ArrayList<InterpreterInfo> interpreterInfos,
      List<Dependency> dependencies, InterpreterOption option,
      Map<String, InterpreterProperty> interpreterProperties, String path,
      InterpreterRunner runner) {
    Preconditions.checkNotNull(group, "name should not be null");
    Preconditions.checkNotNull(interpreterInfos, "interpreterInfos should not be null");
    Preconditions.checkNotNull(dependencies, "dependencies should not be null");
    Preconditions.checkNotNull(option, "option should not be null");
    Preconditions.checkNotNull(interpreterProperties, "properties should not be null");

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
        Map<String, InterpreterProperty> properties =
            (Map<String, InterpreterProperty>) interpreterSetting.getProperties();
        for (String key : interpreterProperties.keySet()) {
          if (!properties.containsKey(key)) {
            properties.put(key, interpreterProperties.get(key));
          }
        }

      } else {
        interpreterSetting =
            new InterpreterSetting(group, null, interpreterInfos, interpreterProperties,
                dependencies, option, path, runner);
        interpreterSettingsRef.put(group, interpreterSetting);
      }
    }

    if (dependencies.size() > 0) {
      loadInterpreterDependencies(interpreterSetting);
    }

    interpreterSetting.setInterpreterGroupFactory(interpreterGroupFactory);
    return interpreterSetting;
  }

  /**
   * map interpreter ids into noteId
   *
   * @param noteId note id
   * @param ids InterpreterSetting id list
   */
  public void setInterpreters(String user, String noteId, List<String> ids) throws IOException {
    putNoteInterpreterSettingBinding(user, noteId, ids);
  }

  private void putNoteInterpreterSettingBinding(String user, String noteId,
      List<String> settingList) throws IOException {
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
        removeInterpretersForNote(setting, user, noteId);
      }
    }
  }

  public void removeInterpretersForNote(InterpreterSetting interpreterSetting, String user,
      String noteId) {
    //TODO(jl): This is only for hotfix. You should fix it as a beautiful way
    InterpreterOption interpreterOption = interpreterSetting.getOption();
    if (!(InterpreterOption.SHARED.equals(interpreterOption.perNote)
        && InterpreterOption.SHARED.equals(interpreterOption.perUser))) {
      interpreterSetting.closeAndRemoveInterpreterGroup(noteId, "");
    }
  }

  public String getInterpreterSessionKey(String user, String noteId, InterpreterSetting setting) {
    InterpreterOption option = setting.getOption();
    String key;
    if (option.isExistingProcess()) {
      key = Constants.EXISTING_PROCESS;
    } else if (option.perNoteScoped() && option.perUserScoped()) {
      key = user + ":" + noteId;
    } else if (option.perUserScoped()) {
      key = user;
    } else if (option.perNoteScoped()) {
      key = noteId;
    } else {
      key = SHARED_SESSION;
    }

    logger.debug("Interpreter session key: {}, for note: {}, user: {}, InterpreterSetting Name: " +
        "{}", key, noteId, user, setting.getName());
    return key;
  }


  public List<String> getInterpreters(String noteId) {
    return getNoteInterpreterSettingBinding(noteId);
  }

  public void closeNote(String user, String noteId) {
    // close interpreters in this note session
    List<InterpreterSetting> settings = getInterpreterSettings(noteId);
    if (settings == null || settings.size() == 0) {
      return;
    }

    logger.info("closeNote: {}", noteId);
    for (InterpreterSetting setting : settings) {
      removeInterpretersForNote(setting, user, noteId);
    }
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
      return new URL[]{path.toURI().toURL()};
    }
  }

  public List<RemoteRepository> getRepositories() {
    return this.interpreterRepositories;
  }

  public void addRepository(String id, String url, boolean snapshot, Authentication auth,
      Proxy proxy) throws IOException {
    dependencyResolver.addRepo(id, url, snapshot, auth, proxy);
    saveToFile();
  }

  public void removeRepository(String id) throws IOException {
    dependencyResolver.delRepo(id);
    saveToFile();
  }

  public void removeNoteInterpreterSettingBinding(String user, String noteId) throws IOException {
    List<String> settingIds = interpreterBindings.remove(noteId);
    if (settingIds != null) {
      for (String settingId : settingIds) {
        InterpreterSetting setting = get(settingId);
        if (setting != null) {
          this.removeInterpretersForNote(setting, user, noteId);
        }
      }
    }
    saveToFile();
  }

  /**
   * Change interpreter property and restart
   */
  public void setPropertyAndRestart(String id, InterpreterOption option, Properties properties,
      List<Dependency> dependencies) throws IOException {
    synchronized (interpreterSettings) {
      InterpreterSetting intpSetting = interpreterSettings.get(id);
      if (intpSetting != null) {
        try {
          stopJobAllInterpreter(intpSetting);

          intpSetting.closeAndRemoveAllInterpreterGroups();
          intpSetting.setOption(option);
          intpSetting.setProperties(properties);
          intpSetting.setDependencies(dependencies);
          loadInterpreterDependencies(intpSetting);

          saveToFile();
        } catch (Exception e) {
          loadFromFile();
          throw e;
        }
      } else {
        throw new InterpreterException("Interpreter setting id " + id + " not found");
      }
    }
  }

  public void restart(String settingId, String noteId, String user) {
    InterpreterSetting intpSetting = interpreterSettings.get(settingId);
    Preconditions.checkNotNull(intpSetting);
    synchronized (interpreterSettings) {
      intpSetting = interpreterSettings.get(settingId);
      // Check if dependency in specified path is changed
      // If it did, overwrite old dependency jar with new one
      if (intpSetting != null) {
        //clean up metaInfos
        intpSetting.setInfos(null);
        copyDependenciesFromLocalPath(intpSetting);

        stopJobAllInterpreter(intpSetting);
        if (user.equals("anonymous")) {
          intpSetting.closeAndRemoveAllInterpreterGroups();
        } else {
          intpSetting.closeAndRemoveInterpreterGroup(noteId, user);
        }

      } else {
        throw new InterpreterException("Interpreter setting id " + settingId + " not found");
      }
    }
  }

  public void restart(String id) {
    restart(id, "", "anonymous");
  }

  private void stopJobAllInterpreter(InterpreterSetting intpSetting) {
    if (intpSetting != null) {
      for (InterpreterGroup intpGroup : intpSetting.getAllInterpreterGroups()) {
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

  public InterpreterSetting get(String name) {
    synchronized (interpreterSettings) {
      return interpreterSettings.get(name);
    }
  }

  public void remove(String id) throws IOException {
    synchronized (interpreterSettings) {
      if (interpreterSettings.containsKey(id)) {
        InterpreterSetting intp = interpreterSettings.get(id);
        intp.closeAndRemoveAllInterpreterGroups();

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

    File localRepoDir = new File(zeppelinConfiguration.getInterpreterLocalRepoPath() + "/" + id);
    FileUtils.deleteDirectory(localRepoDir);
  }

  /**
   * Get interpreter settings
   */
  public List<InterpreterSetting> get() {
    synchronized (interpreterSettings) {
      List<InterpreterSetting> orderedSettings = new LinkedList<>();

      Map<String, List<InterpreterSetting>> nameInterpreterSettingMap = new HashMap<>();
      for (InterpreterSetting interpreterSetting : interpreterSettings.values()) {
        String group = interpreterSetting.getGroup();
        if (!nameInterpreterSettingMap.containsKey(group)) {
          nameInterpreterSettingMap.put(group, new ArrayList<InterpreterSetting>());
        }
        nameInterpreterSettingMap.get(group).add(interpreterSetting);
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

  public void close(InterpreterSetting interpreterSetting) {
    interpreterSetting.closeAndRemoveAllInterpreterGroups();
  }

  public void close() {
    List<Thread> closeThreads = new LinkedList<>();
    synchronized (interpreterSettings) {
      Collection<InterpreterSetting> intpSettings = interpreterSettings.values();
      for (final InterpreterSetting intpSetting : intpSettings) {
        Thread t = new Thread() {
          public void run() {
            intpSetting.closeAndRemoveAllInterpreterGroups();
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

  public void shutdown() {
    List<Thread> closeThreads = new LinkedList<>();
    synchronized (interpreterSettings) {
      Collection<InterpreterSetting> intpSettings = interpreterSettings.values();
      for (final InterpreterSetting intpSetting : intpSettings) {
        Thread t = new Thread() {
          public void run() {
            intpSetting.shutdownAndRemoveAllInterpreterGroups();
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
}
