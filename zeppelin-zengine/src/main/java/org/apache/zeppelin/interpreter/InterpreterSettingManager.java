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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Tags;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.zeppelin.cluster.ClusterManagerServer;
import org.apache.zeppelin.cluster.event.ClusterEvent;
import org.apache.zeppelin.cluster.event.ClusterEventListener;
import org.apache.zeppelin.cluster.event.ClusterMessage;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.conf.ZeppelinConfiguration.ConfVars;
import org.apache.zeppelin.dep.Dependency;
import org.apache.zeppelin.dep.DependencyResolver;
import org.apache.zeppelin.display.AngularObjectRegistry;
import org.apache.zeppelin.display.AngularObjectRegistryListener;
import org.apache.zeppelin.helium.ApplicationEventListener;
import org.apache.zeppelin.interpreter.Interpreter.RegisteredInterpreter;
import org.apache.zeppelin.interpreter.recovery.RecoveryStorage;
import org.apache.zeppelin.interpreter.remote.RemoteAngularObjectRegistry;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterProcess;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterProcessListener;
import org.apache.zeppelin.notebook.ApplicationState;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.NoteEventListener;
import org.apache.zeppelin.notebook.Notebook;
import org.apache.zeppelin.notebook.Paragraph;
import org.apache.zeppelin.notebook.ParagraphTextParser;
import org.apache.zeppelin.resource.Resource;
import org.apache.zeppelin.resource.ResourcePool;
import org.apache.zeppelin.resource.ResourceSet;
import org.apache.zeppelin.scheduler.Job;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.apache.zeppelin.util.ReflectionUtils;
import org.apache.zeppelin.storage.ConfigStorage;
import org.eclipse.jetty.util.annotation.ManagedAttribute;
import org.eclipse.jetty.util.annotation.ManagedObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.eclipse.aether.repository.Proxy;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.Authentication;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.apache.zeppelin.cluster.ClusterManagerServer.CLUSTER_INTP_SETTING_EVENT_TOPIC;


/**
 * InterpreterSettingManager is the component which manage all the interpreter settings.
 * (load/create/update/remove/get)
 * TODO(zjffdu) We could move it into another separated component.
 */
@ManagedObject("interpreterSettingManager")
public class InterpreterSettingManager implements NoteEventListener, ClusterEventListener {

  public static final String INTERNAL_SETTING_NAME = "__internal__";

  private static final Pattern VALID_INTERPRETER_NAME = Pattern.compile("^[-_a-zA-Z0-9]+$");
  private static final Logger LOGGER = LoggerFactory.getLogger(InterpreterSettingManager.class);
  private static final Map<String, Object> DEFAULT_EDITOR = ImmutableMap.of(
      "language", (Object) "text",
      "editOnDblClick", false);

  private final ZeppelinConfiguration conf;
  private final Path interpreterDirPath;

  /**
   * This is only InterpreterSetting templates with default name and properties
   * name --> InterpreterSetting
   */
  private final Map<String, InterpreterSetting> interpreterSettingTemplates =
      new ConcurrentHashMap<>();
  /**
   * This is used by creating and running Interpreters
   * id --> InterpreterSetting
   * TODO(zjffdu) change it to name --> InterpreterSetting
   */
  private final Map<String, InterpreterSetting> interpreterSettings = Metrics.gaugeMapSize("interpreter.amount", Tags.empty(),
    new ConcurrentHashMap<>());
  private final Map<String, List<Meter>> interpreterSettingsMeters = new ConcurrentHashMap<>();

  private final List<RemoteRepository> interpreterRepositories;
  private InterpreterOption defaultOption;
  private String defaultInterpreterGroup;
  private final Gson gson;

  private Notebook notebook;
  private AngularObjectRegistryListener angularObjectRegistryListener;
  private RemoteInterpreterProcessListener remoteInterpreterProcessListener;
  private ApplicationEventListener appEventListener;
  private DependencyResolver dependencyResolver;
  private RecoveryStorage recoveryStorage;
  private ConfigStorage configStorage;
  private RemoteInterpreterEventServer interpreterEventServer;
  private Map<String, String> jupyterKernelLanguageMap = new HashMap<>();
  private List<String> includesInterpreters;
  private List<String> excludesInterpreters;

  @Inject
  public InterpreterSettingManager(ZeppelinConfiguration zeppelinConfiguration,
                                   AngularObjectRegistryListener angularObjectRegistryListener,
                                   RemoteInterpreterProcessListener
                                       remoteInterpreterProcessListener,
                                   ApplicationEventListener appEventListener)
      throws IOException {
    this(zeppelinConfiguration, new InterpreterOption(),
        angularObjectRegistryListener,
        remoteInterpreterProcessListener,
        appEventListener,
        ConfigStorage.getInstance(zeppelinConfiguration));
  }

  public InterpreterSettingManager(ZeppelinConfiguration conf,
      InterpreterOption defaultOption,
      AngularObjectRegistryListener angularObjectRegistryListener,
      RemoteInterpreterProcessListener remoteInterpreterProcessListener,
      ApplicationEventListener appEventListener,
      ConfigStorage configStorage)
      throws IOException {
    this.conf = conf;
    this.defaultOption = defaultOption;
    this.interpreterDirPath = Paths.get(conf.getInterpreterDir());
    LOGGER.debug("InterpreterRootPath: {}", interpreterDirPath);
    this.dependencyResolver =
        new DependencyResolver(conf.getString(ConfVars.ZEPPELIN_INTERPRETER_LOCALREPO));
    this.interpreterRepositories = dependencyResolver.getRepos();
    this.defaultInterpreterGroup = conf.getString(ConfVars.ZEPPELIN_INTERPRETER_GROUP_DEFAULT);
    this.gson = new GsonBuilder().setPrettyPrinting().create();

    this.angularObjectRegistryListener = angularObjectRegistryListener;
    this.remoteInterpreterProcessListener = remoteInterpreterProcessListener;
    this.appEventListener = appEventListener;

    this.interpreterEventServer = new RemoteInterpreterEventServer(conf, this);
    this.interpreterEventServer.start();

    this.recoveryStorage =
        ReflectionUtils.createClazzInstance(
            conf.getRecoveryStorageClass(),
            new Class[] {ZeppelinConfiguration.class, InterpreterSettingManager.class},
            new Object[] {conf, this});

    LOGGER.info("Using RecoveryStorage: {}", this.recoveryStorage.getClass().getName());

    this.configStorage = configStorage;
    init();
  }

  public RemoteInterpreterEventServer getInterpreterEventServer() {
    return interpreterEventServer;
  }

  public void refreshInterpreterTemplates() {
    Set<String> installedInterpreters = new HashSet<>(interpreterSettingTemplates.keySet());

    try {
      LOGGER.info("Refreshing interpreter list");
      loadInterpreterSettingFromDefaultDir(false);
      Set<String> newlyAddedInterpreters = new HashSet<>(interpreterSettingTemplates.keySet());
      newlyAddedInterpreters.removeAll(installedInterpreters);
      if(!newlyAddedInterpreters.isEmpty()) {
        saveToFile();
      }
    } catch (IOException e) {
      LOGGER.error("Error while saving interpreter settings.");
    }
  }


  private void initInterpreterSetting(InterpreterSetting interpreterSetting) {
    interpreterSetting.setConf(conf)
        .setInterpreterSettingManager(this)
        .setAngularObjectRegistryListener(angularObjectRegistryListener)
        .setRemoteInterpreterProcessListener(remoteInterpreterProcessListener)
        .setAppEventListener(appEventListener)
        .setDependencyResolver(dependencyResolver)
        .setRecoveryStorage(recoveryStorage)
        .setInterpreterEventServer(interpreterEventServer)
        .postProcessing();
  }

  /**
   * Load interpreter setting from interpreter.json
   */
  private void loadFromFile() throws IOException {
    InterpreterInfoSaving infoSaving =
        configStorage.loadInterpreterSettings();
    if (infoSaving == null) {
      // it is fresh zeppelin instance if there's no interpreter.json, just create interpreter
      // setting from interpreterSettingTemplates
      for (InterpreterSetting interpreterSettingTemplate : interpreterSettingTemplates.values()) {
        InterpreterSetting interpreterSetting = new InterpreterSetting(interpreterSettingTemplate);
        initInterpreterSetting(interpreterSetting);
        if (shouldRegister(interpreterSetting.getGroup())) {
          addInterpreterSetting(interpreterSetting);
        }
      }
      return;
    }

    //TODO(zjffdu) still ugly (should move all to InterpreterInfoSaving)
    for (InterpreterSetting savedInterpreterSetting : infoSaving.interpreterSettings.values()) {
      if (!shouldRegister(savedInterpreterSetting.getGroup())) {
        continue;
      }
      savedInterpreterSetting.setProperties(InterpreterSetting.convertInterpreterProperties(
          savedInterpreterSetting.getProperties()
      ));
      initInterpreterSetting(savedInterpreterSetting);

      InterpreterSetting interpreterSettingTemplate =
          interpreterSettingTemplates.get(savedInterpreterSetting.getGroup());
      // InterpreterSettingTemplate is from interpreter-setting.json which represent the initialized
      // InterpreterSetting, while InterpreterSetting is from interpreter.json which represent
      // the user saved interpreter setting
      if (interpreterSettingTemplate != null) {
        savedInterpreterSetting.sortPropertiesByTemplate(interpreterSettingTemplate.getProperties());
        savedInterpreterSetting.fillPropertyDescription(interpreterSettingTemplate.getProperties());
        // merge InterpreterDir, InterpreterInfo & InterpreterRunner
        savedInterpreterSetting.setInterpreterDir(
            interpreterSettingTemplate.getInterpreterDir());
        savedInterpreterSetting.setInterpreterInfos(
            interpreterSettingTemplate.getInterpreterInfos());
        savedInterpreterSetting.setInterpreterRunner(
            interpreterSettingTemplate.getInterpreterRunner());
      } else {
        LOGGER.warn("No InterpreterSetting Template found for InterpreterSetting: {},"
          + " but it is found in interpreter.json, it would be skipped.", savedInterpreterSetting.getGroup());
        continue;
      }

      // Overwrite the default InterpreterSetting we registered from InterpreterSetting Templates
      // remove it first
      for (InterpreterSetting setting : interpreterSettings.values()) {
        if (setting.getName().equals(savedInterpreterSetting.getName())) {
          removeInterpreterSetting(setting.getId());
        }
      }
      savedInterpreterSetting.postProcessing();
      LOGGER.info("Create interpreter setting {} from interpreter.json",
          savedInterpreterSetting.getName());
      addInterpreterSetting(savedInterpreterSetting);
    }

    for (InterpreterSetting interpreterSettingTemplate : interpreterSettingTemplates.values()) {
      InterpreterSetting interpreterSetting = new InterpreterSetting(interpreterSettingTemplate);
      initInterpreterSetting(interpreterSetting);
      // add newly detected interpreter if it doesn't exist in interpreter.json
      if (!interpreterSettings.containsKey(interpreterSetting.getId())) {
        LOGGER.info("Create interpreter setting: {} from interpreter setting template", interpreterSetting.getId());
        addInterpreterSetting(interpreterSetting);
      }
    }

    if (infoSaving.interpreterRepositories != null) {
      for (RemoteRepository repo : infoSaving.interpreterRepositories) {
        if (!dependencyResolver.getRepos().contains(repo)) {
          this.interpreterRepositories.add(repo);
        }
      }

      // force interpreter dependencies loading once the
      // repositories have been loaded.
      for (InterpreterSetting setting : interpreterSettings.values()) {
        setting.setDependencies(setting.getDependencies());
      }
    }
  }

  private void addInterpreterSetting(InterpreterSetting interpreterSetting) {
    interpreterSettings.put(interpreterSetting.getId(), interpreterSetting);
    List<Meter> meters = new LinkedList<>();
    Gauge size = Gauge
      .builder("interpreter.group.size", () -> interpreterSetting.getAllInterpreterGroups().size())
      .description("Size of all interpreter groups")
      .tags(Tags.of("name", interpreterSetting.getId()))
      .tags(Tags.of("group", interpreterSetting.getGroup()))
      .register(Metrics.globalRegistry);
    meters.add(size);
    interpreterSettingsMeters.put(interpreterSetting.getId(), meters);
  }


  private void removeInterpreterSetting(String id) {
    interpreterSettings.remove(id);
    List<Meter> meters = interpreterSettingsMeters.remove(id);
    for (Meter meter : meters) {
      Metrics.globalRegistry.remove(meter);
    }
  }

  public void saveToFile() throws IOException {
    InterpreterInfoSaving info = new InterpreterInfoSaving();
    info.interpreterSettings = new HashMap<>(interpreterSettings);
    info.interpreterRepositories = interpreterRepositories;
    configStorage.save(info);
  }

  private void initMetrics() {
    Gauge
      .builder("interpreter.group.size.total", () -> getAllInterpreterGroup().size())
      .description("Size of all interpreter groups")
      .tags()
      .register(Metrics.globalRegistry);
  }

  private void init() throws IOException {
    this.includesInterpreters =
            Arrays.asList(conf.getString(ConfVars.ZEPPELIN_INTERPRETER_INCLUDES).split(","))
                    .stream()
                    .filter(t -> !t.isEmpty())
                    .collect(Collectors.toList());
    this.excludesInterpreters =
            Arrays.asList(conf.getString(ConfVars.ZEPPELIN_INTERPRETER_EXCLUDES).split(","))
                    .stream()
                    .filter(t -> !t.isEmpty())
                    .collect(Collectors.toList());
    if (!includesInterpreters.isEmpty() && !excludesInterpreters.isEmpty()) {
      throw new IOException(String.format("%s and %s can not be specified together, only one can be set.",
              ConfVars.ZEPPELIN_INTERPRETER_INCLUDES.getVarName(),
              ConfVars.ZEPPELIN_INTERPRETER_EXCLUDES.getVarName()));
    }
    loadJupyterKernelLanguageMap();
    loadInterpreterSettingFromDefaultDir(true);
    loadFromFile();
    saveToFile();
    initMetrics();

    // must init Recovery after init of InterpreterSettingManager
    recoveryStorage.init();
  }

  /**
   * We should only register interpreterSetting when
   * 1. No setting in 'zeppelin.interpreter.include' and 'zeppelin.interpreter.exclude'
   * 2. It is specified in 'zeppelin.interpreter.include'
   * 3. It is not specified in 'zeppelin.interpreter.exclude'
   * @param group
   * @return
   */
  private boolean shouldRegister(String group) {
    return (includesInterpreters.isEmpty() && excludesInterpreters.isEmpty()) ||
    (!includesInterpreters.isEmpty() && includesInterpreters.contains(group)) ||
            (!excludesInterpreters.isEmpty() && !excludesInterpreters.contains(group));
  }

  private void loadJupyterKernelLanguageMap() throws IOException {
    String kernels = conf.getString(ConfVars.ZEPPELIN_INTERPRETER_JUPYTER_KERNELS);
    for (String kernel : kernels.split(",")) {
      String[] tokens = kernel.split(":");
      if (tokens.length != 2) {
        throw new IOException("Invalid kernel specified in " +
                ConfVars.ZEPPELIN_INTERPRETER_JUPYTER_KERNELS.getVarName() + ", Invalid kernel: " + kernel +
                ", please use format kernel_name:language");
      }
      this.jupyterKernelLanguageMap.put(tokens[0].trim(), tokens[1].trim());
    }
  }

  private void loadInterpreterSettingFromDefaultDir(boolean override) throws IOException {
    // 1. detect interpreter setting via interpreter-setting.json in each interpreter folder
    // 2. detect interpreter setting in interpreter.json that is saved before
    String interpreterJson = conf.getInterpreterJson();
    ClassLoader cl = Thread.currentThread().getContextClassLoader();
    if (Files.exists(interpreterDirPath)) {
      try (DirectoryStream<Path> directoryPaths = Files
        .newDirectoryStream(interpreterDirPath,
          entry -> Files.exists(entry)
                  && Files.isDirectory(entry)
                  && shouldRegister(entry.toFile().getName()))) {
        for (Path interpreterDir : directoryPaths) {

          String interpreterDirString = interpreterDir.toString();
          /**
           * Register interpreter by the following ordering
           * 1. Register it from path {ZEPPELIN_HOME}/interpreter/{interpreter_name}/
           *    interpreter-setting.json
           * 2. Register it from interpreter-setting.json in classpath
           *    {ZEPPELIN_HOME}/interpreter/{interpreter_name}
           */
          if (!registerInterpreterFromPath(interpreterDirString, interpreterJson, override) &&
            !registerInterpreterFromResource(cl, interpreterDirString, interpreterJson, override)) {
            LOGGER.warn("No interpreter-setting.json found in {}", interpreterDirString);
          }
        }
      }
    } else {
      LOGGER.warn("InterpreterDir {} doesn't exist", interpreterDirPath);
    }
  }

  public void setNotebook(Notebook notebook) {
    this.notebook = notebook;
  }

  public Notebook getNotebook() {
    return notebook;
  }

  public RemoteInterpreterProcessListener getRemoteInterpreterProcessListener() {
    return remoteInterpreterProcessListener;
  }

  public ApplicationEventListener getAppEventListener() {
    return appEventListener;
  }

  private boolean registerInterpreterFromResource(ClassLoader cl, String interpreterDir,
                                                  String interpreterJson, boolean override) throws IOException {
    URL[] urls = recursiveBuildLibList(new File(interpreterDir));
    ClassLoader tempClassLoader = new URLClassLoader(urls, null);

    URL url = tempClassLoader.getResource(interpreterJson);
    if (url == null) {
      return false;
    }

    LOGGER.debug("Reading interpreter-setting.json from {} as Resource", url);
    List<RegisteredInterpreter> registeredInterpreterList =
        getInterpreterListFromJson(url.openStream());
    registerInterpreterSetting(registeredInterpreterList, interpreterDir, override);
    return true;
  }

  private boolean registerInterpreterFromPath(String interpreterDir, String interpreterJson,
      boolean override) throws IOException {

    Path interpreterJsonPath = Paths.get(interpreterDir, interpreterJson);
    if (Files.exists(interpreterJsonPath)) {
      LOGGER.debug("Reading interpreter-setting.json from file {}", interpreterJsonPath);
      List<RegisteredInterpreter> registeredInterpreterList =
          getInterpreterListFromJson(new FileInputStream(interpreterJsonPath.toFile()));
      registerInterpreterSetting(registeredInterpreterList, interpreterDir, override);
      return true;
    }
    return false;
  }

  private List<RegisteredInterpreter> getInterpreterListFromJson(InputStream stream) {
    Type registeredInterpreterListType = new TypeToken<List<RegisteredInterpreter>>() {
    }.getType();
    return gson.fromJson(new InputStreamReader(stream), registeredInterpreterListType);
  }

  private void registerInterpreterSetting(List<RegisteredInterpreter> registeredInterpreters,
      String interpreterDir, boolean override) {

    Map<String, DefaultInterpreterProperty> properties = new LinkedHashMap<>();
    List<InterpreterInfo> interpreterInfos = new ArrayList<>();
    InterpreterOption option = defaultOption;
    String group = null;
    InterpreterRunner runner = null;
    for (RegisteredInterpreter registeredInterpreter : registeredInterpreters) {
      //TODO(zjffdu) merge RegisteredInterpreter & InterpreterInfo
      InterpreterInfo interpreterInfo =
          new InterpreterInfo(registeredInterpreter.getClassName(), registeredInterpreter.getName(),
              registeredInterpreter.isDefaultInterpreter(), registeredInterpreter.getEditor(),
              registeredInterpreter.getConfig());
      interpreterInfo.setConfig(registeredInterpreter.getConfig());
      group = registeredInterpreter.getGroup();
      runner = registeredInterpreter.getRunner();
      // use defaultOption if it is not specified in interpreter-setting.json
      if (registeredInterpreter.getOption() != null) {
        option = registeredInterpreter.getOption();
      }
      properties.putAll(registeredInterpreter.getProperties());
      interpreterInfos.add(interpreterInfo);
    }

    InterpreterSetting interpreterSettingTemplate = new InterpreterSetting.Builder()
        .setGroup(group)
        .setName(group)
        .setInterpreterInfos(interpreterInfos)
        .setProperties(properties)
        .setDependencies(new ArrayList<>())
        .setOption(option)
        .setRunner(runner)
        .setInterpreterDir(interpreterDir)
        .setRunner(runner)
        .setConf(conf)
        .setIntepreterSettingManager(this)
        .create();

    String key = interpreterSettingTemplate.getName();
    if(override || !interpreterSettingTemplates.containsKey(key)) {
      LOGGER.info("Register InterpreterSettingTemplate: {}", key);
      interpreterSettingTemplates.put(key, interpreterSettingTemplate);
    }
  }

  public InterpreterSetting getDefaultInterpreterSetting(String noteId) {
    try {
      InterpreterSetting interpreterSetting = notebook.processNote(noteId,
        note -> {
          return interpreterSettings.get(note.getDefaultInterpreterGroup());
        });
      if (interpreterSetting == null) {
        interpreterSetting = get().get(0);
      }
      return interpreterSetting;
    } catch (Exception e) {
      LOGGER.warn("Fail to get note: {}", noteId, e);
      return get().get(0);
    }
  }

  public InterpreterSetting getInterpreterSettingByName(String name) {
    for (InterpreterSetting setting : interpreterSettings.values()) {
      if (setting.getName().equals(name)) {
        return setting;
      }
    }
    return null;
  }

  public ManagedInterpreterGroup getInterpreterGroupById(String groupId) {
    for (InterpreterSetting setting : interpreterSettings.values()) {
      ManagedInterpreterGroup interpreterGroup = setting.getInterpreterGroup(groupId);
      if (interpreterGroup != null) {
        return interpreterGroup;
      }
    }
    return null;
  }

  /**
   * Get editor setting for one paragraph based on its paragraph text and noteId
   *
   * @param paragraphText
   * @param noteId
   * @return
   */
  public Map<String, Object> getEditorSetting(String paragraphText, String noteId) {
    ParagraphTextParser.ParseResult parseResult = ParagraphTextParser.parse(paragraphText);
    if (StringUtils.isBlank(parseResult.getIntpText())) {
      // Use default interpreter setting if no interpreter is specified.
      InterpreterSetting interpreterSetting = getDefaultInterpreterSetting(noteId);
      try {
        return interpreterSetting.getDefaultInterpreterInfo().getEditor();
      } catch (Exception e) {
        LOGGER.warn(e.getMessage());
        return DEFAULT_EDITOR;
      }
    } else {
      String[] replNameSplit = parseResult.getIntpText().split("\\.");
      if (replNameSplit.length == 1) {
        // Either interpreter group or interpreter name is specified.

        // Assume it is interpreter name
        String intpName = replNameSplit[0];
        InterpreterSetting defaultInterpreterSetting = getDefaultInterpreterSetting(noteId);
        InterpreterInfo interpreterInfo = defaultInterpreterSetting.getInterpreterInfo(intpName);
        if (interpreterInfo != null) {
          return interpreterInfo.getEditor();
        }

        // Then assume it is interpreter group name
        String intpGroupName = replNameSplit[0];
        if (intpGroupName.equals("jupyter")) {
          InterpreterSetting interpreterSetting = interpreterSettings.get("jupyter");
          Map<String, Object> jupyterEditorSetting = interpreterSetting.getInterpreterInfos().get(0).getEditor();
          String kernel = parseResult.getLocalProperties().get("kernel");
          if (kernel != null) {
            String language = jupyterKernelLanguageMap.get(kernel);
            if (language != null) {
              jupyterEditorSetting.put("language", language);
            }
          }
          return jupyterEditorSetting;
        } else {
          try {
            InterpreterSetting interpreterSetting = getInterpreterSettingByName(intpGroupName);
            if (interpreterSetting == null) {
              return DEFAULT_EDITOR;
            }
            return interpreterSetting.getDefaultInterpreterInfo().getEditor();
          } catch (Exception e) {
            LOGGER.warn(e.getMessage());
            return DEFAULT_EDITOR;
          }
        }
      } else {
        // Both interpreter group and name are specified. e.g. spark.pyspark
        String intpGroupName = replNameSplit[0];
        String intpName = replNameSplit[1];
        try {
          InterpreterSetting interpreterSetting = getInterpreterSettingByName(intpGroupName);
          return interpreterSetting.getInterpreterInfo(intpName).getEditor();
        } catch (Exception e) {
          LOGGER.warn(e.getMessage());
          return DEFAULT_EDITOR;
        }
      }
    }
  }

  // Get configuration parameters from `interpreter-setting.json`
  // based on the interpreter group ID
  public Map<String, Object> getConfigSetting(String interpreterGroupId) {
    InterpreterSetting interpreterSetting = get(interpreterGroupId);
    if (null != interpreterSetting) {
      List<InterpreterInfo> interpreterInfos = interpreterSetting.getInterpreterInfos();
      int infoSize = interpreterInfos.size();
      for (InterpreterInfo intpInfo : interpreterInfos) {
        if ((intpInfo.isDefaultInterpreter() || (infoSize == 1))
            && (intpInfo.getConfig() != null)) {
          return intpInfo.getConfig();
        }
      }
    }

    return new HashMap<>();
  }

  public List<ManagedInterpreterGroup> getAllInterpreterGroup() {
    List<ManagedInterpreterGroup> interpreterGroups = new ArrayList<>();
    for (InterpreterSetting interpreterSetting : interpreterSettings.values()) {
      interpreterGroups.addAll(interpreterSetting.getAllInterpreterGroups());
    }
    return interpreterGroups;
  }

  // TODO(zjffdu) Current approach is not optimized. we have to iterate all interpreter settings.
  public void removeInterpreterGroup(String intpGroupId) {
    for (InterpreterSetting interpreterSetting : interpreterSettings.values()) {
      interpreterSetting.removeInterpreterGroup(intpGroupId);
    }
  }

  //TODO(zjffdu) move Resource related api to ResourceManager
  public ResourceSet getAllResources() {
    return getAllResourcesExcept(null);
  }

  private ResourceSet getAllResourcesExcept(String interpreterGroupExcludsion) {
    ResourceSet resourceSet = new ResourceSet();
    for (ManagedInterpreterGroup intpGroup : getAllInterpreterGroup()) {
      if (interpreterGroupExcludsion != null &&
          intpGroup.getId().equals(interpreterGroupExcludsion)) {
        continue;
      }

      RemoteInterpreterProcess remoteInterpreterProcess = intpGroup.getRemoteInterpreterProcess();
      if (remoteInterpreterProcess == null) {
        ResourcePool localPool = intpGroup.getResourcePool();
        if (localPool != null) {
          resourceSet.addAll(localPool.getAll());
        }
      } else if (remoteInterpreterProcess.isRunning()) {
        List<String> resourceList = remoteInterpreterProcess.callRemoteFunction(client -> client.resourcePoolGetAll());
        if (resourceList != null) {
          for (String res : resourceList) {
            resourceSet.add(Resource.fromJson(res));
          }
        }
      }
    }
    return resourceSet;
  }

  public RecoveryStorage getRecoveryStorage() {
    return recoveryStorage;
  }

  public void removeResourcesBelongsToParagraph(String noteId, String paragraphId) {
    for (ManagedInterpreterGroup intpGroup : getAllInterpreterGroup()) {
      ResourceSet resourceSet = new ResourceSet();
      RemoteInterpreterProcess remoteInterpreterProcess = intpGroup.getRemoteInterpreterProcess();
      if (remoteInterpreterProcess == null) {
        ResourcePool localPool = intpGroup.getResourcePool();
        if (localPool != null) {
          resourceSet.addAll(localPool.getAll());
        }
        if (noteId != null) {
          resourceSet = resourceSet.filterByNoteId(noteId);
        }
        if (paragraphId != null) {
          resourceSet = resourceSet.filterByParagraphId(paragraphId);
        }

        for (Resource r : resourceSet) {
          localPool.remove(
              r.getResourceId().getNoteId(),
              r.getResourceId().getParagraphId(),
              r.getResourceId().getName());
        }
      } else if (remoteInterpreterProcess.isRunning()) {
        try {
          List<String> resourceList = remoteInterpreterProcess.callRemoteFunction(client -> client.resourcePoolGetAll());
          for (String res : resourceList) {
            resourceSet.add(Resource.fromJson(res));
          }

          if (noteId != null) {
            resourceSet = resourceSet.filterByNoteId(noteId);
          }
          if (paragraphId != null) {
            resourceSet = resourceSet.filterByParagraphId(paragraphId);
          }

          for (final Resource r : resourceSet) {
            remoteInterpreterProcess.callRemoteFunction(client -> {
              client.resourceRemove(r.getResourceId().getNoteId(),
                      r.getResourceId().getParagraphId(),
                      r.getResourceId().getName());
              return null;
            });
          }
        } catch (Exception e){
          LOGGER.error(e.getMessage());
        }
      }
    }
  }

  public void removeResourcesBelongsToNote(String noteId) {
    try {
      removeResourcesBelongsToParagraph(noteId, null);
    } catch (Exception e) {
      LOGGER.warn("Fail to remove resources", e);
    }
  }

  /**
   * Overwrite dependency jar under local-repo/{interpreterId} if jar file in original path is
   * changed
   */
  private void copyDependenciesFromLocalPath(final InterpreterSetting setting) {
    try {
      List<Dependency> deps = setting.getDependencies();
      if (deps != null) {
        LOGGER.info("Start to copy dependencies for interpreter: {}", setting.getName());
        for (Dependency d : deps) {
          File destDir = new File(
              conf.getAbsoluteDir(ConfVars.ZEPPELIN_DEP_LOCALREPO));

          int numSplits = d.getGroupArtifactVersion().split(":").length;
          if (!(numSplits >= 3 && numSplits <= 6)) {
            dependencyResolver.copyLocalDependency(d.getGroupArtifactVersion(),
                new File(destDir, setting.getId()));
          }
        }
        LOGGER.info("Finish copy dependencies for interpreter: {}", setting.getName());
      }
    } catch (Exception e) {
      LOGGER.error(String.format("Error while copying deps for interpreter group : %s," +
              " go to interpreter setting page click on edit and save it again to make " +
              "this interpreter work properly.",
          setting.getGroup()), e);
      setting.setErrorReason(e.getLocalizedMessage());
      setting.setStatus(InterpreterSetting.Status.ERROR);
    }
  }

  /**
   * Return ordered interpreter setting list.
   * The list does not contain more than one setting from the same interpreter class.
   * Order by InterpreterClass (order defined by ZEPPELIN_INTERPRETERS), Interpreter setting name
   */
  public List<String> getInterpreterSettingIds() {
    List<String> settingIdList = new ArrayList<>();
    for (InterpreterSetting interpreterSetting : get()) {
      settingIdList.add(interpreterSetting.getId());
    }
    return settingIdList;
  }

  public InterpreterSetting createNewSetting(String name, String group,
                                             List<Dependency> dependencies,
                                             InterpreterOption option,
                                             Map<String, InterpreterProperty> properties)
      throws IOException {

    InterpreterSetting interpreterSetting = null;
    try {
      interpreterSetting = inlineCreateNewSetting(name, group, dependencies, option, properties);

      broadcastClusterEvent(ClusterEvent.CREATE_INTP_SETTING, interpreterSetting);
    } catch (IOException e) {
      LOGGER.error(e.getMessage(), e);
      throw e;
    }

    return interpreterSetting;
  }

  private InterpreterSetting inlineCreateNewSetting(String name, String group,
                                                    List<Dependency> dependencies,
                                                    InterpreterOption option,
                                                    Map<String, InterpreterProperty> properties)
      throws IOException {
    if (name == null) {
      throw new IOException("Interpreter name shouldn't be empty.");
    }

    // check if name is valid
    Matcher matcher = VALID_INTERPRETER_NAME.matcher(name);
    if(!matcher.find()){
      throw new IOException("Interpreter name shouldn't be empty, and can consist only of: -_a-zA-Z0-9");
    }

    // check if name is existed
    for (InterpreterSetting interpreterSetting : interpreterSettings.values()) {
      if (interpreterSetting.getName().equals(name)) {
        throw new IOException("Interpreter " + name + " already existed");
      }
    }
    InterpreterSetting setting = new InterpreterSetting(interpreterSettingTemplates.get(group));
    setting.setName(name);
    setting.setGroup(group);
    //TODO(zjffdu) Should use setDependencies
    setting.appendDependencies(dependencies);
    setting.setInterpreterOption(option);
    setting.setProperties(properties);
    initInterpreterSetting(setting);
    addInterpreterSetting(setting);
    saveToFile();

    return setting;
  }

  public Map<String, InterpreterSetting> getInterpreterSettingTemplates() {
    return interpreterSettingTemplates;
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
          urls = ArrayUtils.addAll(urls, recursiveBuildLibList(f));
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

  /** restart in interpreter setting page */
  private InterpreterSetting inlineSetPropertyAndRestart(
      String id,
      InterpreterOption option,
      Map<String, InterpreterProperty> properties,
      List<Dependency> dependencies,
      boolean initiator)
      throws InterpreterException, IOException {
    InterpreterSetting intpSetting = interpreterSettings.get(id);
    if (intpSetting != null) {
      try {
        intpSetting.close();
        intpSetting.setOption(option);
        intpSetting.setProperties(properties);
        intpSetting.setDependencies(dependencies);
        intpSetting.postProcessing();
        if (initiator) {
          saveToFile();
        }
      } catch (Exception e) {
        loadFromFile();
        throw new IOException(e);
      }
    } else {
      throw new InterpreterException("Interpreter setting id " + id + " not found");
    }
    return intpSetting;
  }

  /** Change interpreter properties and restart */
  public void setPropertyAndRestart(
      String id,
      InterpreterOption option,
      Map<String, InterpreterProperty> properties,
      List<Dependency> dependencies)
      throws InterpreterException, IOException {
    try {
      InterpreterSetting intpSetting = inlineSetPropertyAndRestart(id, option, properties, dependencies, true);
      // broadcast cluster event
      broadcastClusterEvent(ClusterEvent.UPDATE_INTP_SETTING, intpSetting);
    } catch (Exception e) {
      throw e;
    }
  }

  // restart in note page
  public void restart(String settingId, String user, String noteId) throws InterpreterException {
    try {
      ExecutionContext executionContext = notebook.processNote(noteId,
        note -> {
          if (note == null) {
            throw new IOException("No such note: " + noteId);
          }
          return note.getExecutionContext();
        });
      executionContext.setUser(user);
      restart(settingId, executionContext);
    } catch (IOException e) {
      LOGGER.warn("Fail to restart interpreter", e);
    }
  }

  // restart in note page
  public void restart(String settingId, ExecutionContext executionContext) throws InterpreterException {
    if (settingId == null || settingId.startsWith("__internal__")) {
      return;
    }
    InterpreterSetting intpSetting = interpreterSettings.get(settingId);
    Preconditions.checkNotNull(intpSetting);
    intpSetting = interpreterSettings.get(settingId);
    // Check if dependency in specified path is changed
    // If it did, overwrite old dependency jar with new one
    if (intpSetting != null) {
      copyDependenciesFromLocalPath(intpSetting);
      intpSetting.closeInterpreters(executionContext);
    } else {
      throw new InterpreterException("Interpreter setting id " + settingId + " not found");
    }
  }

  @VisibleForTesting
  public void restart(String id) throws InterpreterException {
    InterpreterSetting setting = interpreterSettings.get(id);
    copyDependenciesFromLocalPath(setting);
    setting.close();
  }

  public InterpreterSetting get(String id) {
    return interpreterSettings.get(id);
  }

  @VisibleForTesting
  public InterpreterSetting getByName(String name) {
    for (InterpreterSetting interpreterSetting : interpreterSettings.values()) {
      if (interpreterSetting.getName().equals(name)) {
        return interpreterSetting;
      }
    }
    return null;
  }

  public void remove(String id) throws IOException {
    boolean removed = inlineRemove(id, true);
    if (removed) {
      // broadcast cluster event
      InterpreterSetting intpSetting = new InterpreterSetting();
      intpSetting.setId(id);
      broadcastClusterEvent(ClusterEvent.DELETE_INTP_SETTING, intpSetting);
    }
  }

  private boolean inlineRemove(String id, boolean initiator) throws IOException {
    boolean removed = false;
    // 1. close interpreter groups of this interpreter setting
    // 2. remove this interpreter setting
    // 3. remove this interpreter setting from note binding
    // 4. clean local repo directory
    LOGGER.info("Remove interpreter setting: {}", id);
    if (interpreterSettings.containsKey(id)) {
      InterpreterSetting intp = interpreterSettings.get(id);
      intp.close();
      removeInterpreterSetting(id);
      if (initiator) {
        // Event initiator saves the file
        // Cluster event accepting nodes do not need to save files repeatedly
        saveToFile();
      }
      File localRepoPath = new File(conf.getInterpreterLocalRepoPath());
      FileUtils.deleteDirectory(new File(localRepoPath, id));
      removed = true;
    }

    return removed;
  }

  /**
   * Get interpreter settings
   */
  public List<InterpreterSetting> get() {
    List<InterpreterSetting> orderedSettings = new ArrayList<>(interpreterSettings.values());
    Collections.sort(orderedSettings, new Comparator<InterpreterSetting>() {
      @Override
      public int compare(InterpreterSetting o1, InterpreterSetting o2) {
        if (o1.getName().equals(defaultInterpreterGroup)) {
          return -1;
        } else if (o2.getName().equals(defaultInterpreterGroup)) {
          return 1;
        } else {
          return o1.getName().compareTo(o2.getName());
        }
      }
    });
    return orderedSettings;
  }

  public InterpreterSetting getDefaultInterpreterSetting() {
    InterpreterSetting setting =
        getByName(conf.getString(ConfVars.ZEPPELIN_INTERPRETER_GROUP_DEFAULT));
    if (setting != null) {
      return setting;
    } else {
      return get().get(0);
    }
  }

  @VisibleForTesting
  public List<String> getSettingIds() {
    List<String> settingIds = new ArrayList<>();
    for (InterpreterSetting interpreterSetting : get()) {
      settingIds.add(interpreterSetting.getId());
    }
    return settingIds;
  }

  public void close(String settingId) {
    get(settingId).close();
  }

  public void close() {
    List<Thread> closeThreads = interpreterSettings.values().stream()
            .map(intpSetting-> new Thread(intpSetting::close, intpSetting.getId() + "-close"))
            .peek(t -> t.setUncaughtExceptionHandler((th, e) ->
                    LOGGER.error("interpreterGroup close error", e)))
            .peek(Thread::start)
            .collect(Collectors.toList());

    for (Thread t : closeThreads) {
      try {
        t.join();
      } catch (InterruptedException e) {
        LOGGER.error("Can't wait close interpreterGroup threads", e);
        Thread.currentThread().interrupt();
        break;
      }
    }
  }

  @ManagedAttribute
  public Set<String> getRunningInterpreters() {
    Set<String> runningInterpreters = new HashSet<>();
    for (Map.Entry<String, InterpreterSetting> entry : interpreterSettings.entrySet()) {
      for (ManagedInterpreterGroup mig : entry.getValue().getAllInterpreterGroups()) {
        if (null != mig.getRemoteInterpreterProcess()) {
          runningInterpreters.add(entry.getKey());
        }
      }
    }
    return runningInterpreters;
  }

  @Override
  public void onNoteRemove(Note note, AuthenticationInfo subject) {
    // stop all associated interpreters
    if (note.getParagraphs() != null) {
      for (Paragraph paragraph : note.getParagraphs()) {
        try {
          Interpreter interpreter = paragraph.getInterpreter();
          if (interpreter != null) {
            InterpreterSetting interpreterSetting =
                    ((ManagedInterpreterGroup) interpreter.getInterpreterGroup()).getInterpreterSetting();
            ExecutionContext executionContext = note.getExecutionContext();
            executionContext.setUser(subject.getUser());
            restart(interpreterSetting.getId(), executionContext);
          }
        } catch (InterpreterNotFoundException e) {

        } catch (InterpreterException e) {
          LOGGER.warn("Fail to stop interpreter setting", e);
        }
      }
    }

    // remove from all interpreter instance's angular object registry
    for (InterpreterSetting settings : interpreterSettings.values()) {
      InterpreterGroup interpreterGroup = settings.getInterpreterGroup(note.getExecutionContext());
      if (interpreterGroup != null) {
        AngularObjectRegistry registry = interpreterGroup.getAngularObjectRegistry();
        if (registry instanceof RemoteAngularObjectRegistry) {
          // remove paragraph scope object
          for (Paragraph p : note.getParagraphs()) {
            ((RemoteAngularObjectRegistry) registry).removeAllAndNotifyRemoteProcess(note.getId(), p.getId());

            // remove app scope object
            List<ApplicationState> appStates = p.getAllApplicationStates();
            if (appStates != null) {
              for (ApplicationState app : appStates) {
                ((RemoteAngularObjectRegistry) registry)
                    .removeAllAndNotifyRemoteProcess(note.getId(), app.getId());
              }
            }
          }
          // remove note scope object
          ((RemoteAngularObjectRegistry) registry).removeAllAndNotifyRemoteProcess(note.getId(), null);
        } else {
          // remove paragraph scope object
          for (Paragraph p : note.getParagraphs()) {
            registry.removeAll(note.getId(), p.getId());

            // remove app scope object
            List<ApplicationState> appStates = p.getAllApplicationStates();
            if (appStates != null) {
              for (ApplicationState app : appStates) {
                registry.removeAll(note.getId(), app.getId());
              }
            }
          }
          // remove note scope object
          registry.removeAll(note.getId(), null);
        }
      }
    }

    removeResourcesBelongsToNote(note.getId());
  }

  @Override
  public void onNoteCreate(Note note, AuthenticationInfo subject) {
    // do nothing
  }

  @Override
  public void onNoteUpdate(Note note, AuthenticationInfo subject) {
    // do nothing
  }

  @Override
  public void onParagraphRemove(Paragraph p) {
    // do nothing
  }

  @Override
  public void onParagraphCreate(Paragraph p) {
    // do nothing
  }

  @Override
  public void onParagraphUpdate(Paragraph p) {
    // do nothing
  }

  @Override
  public void onParagraphStatusChange(Paragraph p, Job.Status status) {
    // do nothing
  }

  @Override
  public void onClusterEvent(String msg) {
    LOGGER.debug("onClusterEvent : {}", msg);

    try {
      ClusterMessage message = ClusterMessage.deserializeMessage(msg);
      String jsonIntpSetting = message.get("intpSetting");
      InterpreterSetting intpSetting = InterpreterSetting.fromJson(jsonIntpSetting);
      String id = intpSetting.getId();
      String name = intpSetting.getName();
      String group = intpSetting.getGroup();
      InterpreterOption option = intpSetting.getOption();
      HashMap<String, InterpreterProperty> properties
          = (HashMap<String, InterpreterProperty>) InterpreterSetting
          .convertInterpreterProperties(intpSetting.getProperties());
      List<Dependency> dependencies = intpSetting.getDependencies();

      switch (message.clusterEvent) {
        case CREATE_INTP_SETTING:
          inlineCreateNewSetting(name, group, dependencies, option, properties);
          break;
        case UPDATE_INTP_SETTING:
          inlineSetPropertyAndRestart(id, option, properties, dependencies, false);
          break;
        case DELETE_INTP_SETTING:
          inlineRemove(id, false);
          break;
        default:
          LOGGER.error("Unknown clusterEvent:{}, msg:{} ", message.clusterEvent, msg);
          break;
      }
    } catch (IOException e) {
      LOGGER.error(e.getMessage(), e);
    } catch (InterpreterException e) {
      LOGGER.error(e.getMessage(), e);
    }
  }

  // broadcast cluster event
  private void broadcastClusterEvent(ClusterEvent event, InterpreterSetting intpSetting) {
    if (!conf.isClusterMode()) {
      return;
    }

    String jsonIntpSetting = InterpreterSetting.toJson(intpSetting);

    ClusterMessage message = new ClusterMessage(event);
    message.put("intpSetting", jsonIntpSetting);
    String msg = ClusterMessage.serializeMessage(message);
    ClusterManagerServer.getInstance(conf).broadcastClusterEvent(
        CLUSTER_INTP_SETTING_EVENT_TOPIC, msg);
  }
}
