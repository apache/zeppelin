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
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
import com.google.gson.internal.StringMap;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.dep.Dependency;
import org.apache.zeppelin.dep.DependencyResolver;
import org.apache.zeppelin.display.AngularObjectRegistry;
import org.apache.zeppelin.display.AngularObjectRegistryListener;
import org.apache.zeppelin.helium.ApplicationEventListener;
import org.apache.zeppelin.interpreter.launcher.InterpreterLaunchContext;
import org.apache.zeppelin.interpreter.launcher.InterpreterLauncher;
import org.apache.zeppelin.interpreter.lifecycle.NullLifecycleManager;
import org.apache.zeppelin.interpreter.recovery.NullRecoveryStorage;
import org.apache.zeppelin.interpreter.recovery.RecoveryStorage;
import org.apache.zeppelin.interpreter.remote.RemoteAngularObjectRegistry;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreter;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterProcess;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterProcessListener;
import org.apache.zeppelin.plugin.PluginManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

import static org.apache.zeppelin.conf.ZeppelinConfiguration.ConfVars.ZEPPELIN_INTERPRETER_MAX_POOL_SIZE;
import static org.apache.zeppelin.conf.ZeppelinConfiguration.ConfVars.ZEPPELIN_INTERPRETER_OUTPUT_LIMIT;
import static org.apache.zeppelin.util.IdHashes.generateId;

/**
 * Represent one InterpreterSetting in the interpreter setting page
 */
public class InterpreterSetting {

  private static final Logger LOGGER = LoggerFactory.getLogger(InterpreterSetting.class);
  private static final String SHARED_PROCESS = "shared_process";
  private static final String SHARED_SESSION = "shared_session";
  private static final Map<String, Object> DEFAULT_EDITOR = ImmutableMap.of(
      "language", (Object) "text",
      "editOnDblClick", false);

  public static String  PARAGRAPH_CONFIG_RUNONSELECTIONCHANGE = "runOnSelectionChange";
  public static String  PARAGRAPH_CONFIG_TITLE = "title";
  public static String  PARAGRAPH_CONFIG_CHECK_EMTPY = "checkEmpty";

  private String id;
  private String name;
  // the original interpreter setting template name where it is created from
  private String group;

  //TODO(zjffdu) make the interpreter.json consistent with interpreter-setting.json
  /**
   * properties can be either Properties or Map<String, InterpreterProperty>
   * properties should be:
   * - Properties when Interpreter instances are saved to `conf/interpreter.json` file
   * - Map<String, InterpreterProperty> when Interpreters are registered
   * : this is needed after https://github.com/apache/zeppelin/pull/1145
   * which changed the way of getting default interpreter setting AKA interpreterSettingsRef
   * Note(mina): In order to simplify the implementation, I chose to change properties
   * from Properties to Object instead of creating new classes.
   */
  private Object properties = new Properties();

  private Status status = Status.READY;
  private String errorReason;

  @SerializedName("interpreterGroup")
  private List<InterpreterInfo> interpreterInfos;

  private List<Dependency> dependencies = new ArrayList<>();
  private InterpreterOption option = new InterpreterOption();

  @SerializedName("runner")
  private InterpreterRunner interpreterRunner;

  ///////////////////////////////////////////////////////////////////////////////////////////
  private transient InterpreterSettingManager interpreterSettingManager;
  private transient String interpreterDir;
  private final transient Map<String, ManagedInterpreterGroup> interpreterGroups =
      new ConcurrentHashMap<>();

  private final transient ReentrantReadWriteLock.ReadLock interpreterGroupReadLock;
  private final transient ReentrantReadWriteLock.WriteLock interpreterGroupWriteLock;

  private transient AngularObjectRegistryListener angularObjectRegistryListener;
  private transient RemoteInterpreterProcessListener remoteInterpreterProcessListener;
  private transient ApplicationEventListener appEventListener;
  private transient DependencyResolver dependencyResolver;

  private transient ZeppelinConfiguration conf = new ZeppelinConfiguration();

  private transient LifecycleManager lifecycleManager;
  private transient RecoveryStorage recoveryStorage;
  private transient RemoteInterpreterEventServer interpreterEventServer;

  public static final String CLUSTER_INTERPRETER_LAUNCHER_NAME = "ClusterInterpreterLauncher";

  ///////////////////////////////////////////////////////////////////////////////////////////

  /**
   * Builder class for InterpreterSetting
   */
  public static class Builder {
    private InterpreterSetting interpreterSetting;

    public Builder() {
      this.interpreterSetting = new InterpreterSetting();
    }

    public Builder setId(String id) {
      interpreterSetting.id = id;
      return this;
    }

    public Builder setName(String name) {
      interpreterSetting.name = name;
      return this;
    }

    public Builder setGroup(String group) {
      interpreterSetting.group = group;
      return this;
    }

    public Builder setInterpreterInfos(List<InterpreterInfo> interpreterInfos) {
      interpreterSetting.interpreterInfos = interpreterInfos;
      return this;
    }

    public Builder setProperties(Object properties) {
      interpreterSetting.properties = properties;
      return this;
    }

    public Builder setOption(InterpreterOption option) {
      interpreterSetting.option = option;
      return this;
    }

    public Builder setInterpreterDir(String interpreterDir) {
      interpreterSetting.interpreterDir = interpreterDir;
      return this;
    }

    public Builder setRunner(InterpreterRunner runner) {
      interpreterSetting.interpreterRunner = runner;
      return this;
    }

    public Builder setDependencies(List<Dependency> dependencies) {
      interpreterSetting.dependencies = dependencies;
      return this;
    }

    public Builder setConf(ZeppelinConfiguration conf) {
      interpreterSetting.conf = conf;
      return this;
    }

    public Builder setDependencyResolver(DependencyResolver dependencyResolver) {
      interpreterSetting.dependencyResolver = dependencyResolver;
      return this;
    }

    public Builder setInterpreterRunner(InterpreterRunner runner) {
      interpreterSetting.interpreterRunner = runner;
      return this;
    }

    public Builder setIntepreterSettingManager(
        InterpreterSettingManager interpreterSettingManager) {
      interpreterSetting.interpreterSettingManager = interpreterSettingManager;
      return this;
    }

    public Builder setRemoteInterpreterEventServer(RemoteInterpreterEventServer interpreterEventServer) {
      interpreterSetting.interpreterEventServer = interpreterEventServer;
      return this;
    }

    public Builder setRemoteInterpreterProcessListener(RemoteInterpreterProcessListener
                                                       remoteInterpreterProcessListener) {
      interpreterSetting.remoteInterpreterProcessListener = remoteInterpreterProcessListener;
      return this;
    }

    public Builder setAngularObjectRegistryListener(
        AngularObjectRegistryListener angularObjectRegistryListener) {
      interpreterSetting.angularObjectRegistryListener = angularObjectRegistryListener;
      return this;
    }

    public Builder setApplicationEventListener(ApplicationEventListener applicationEventListener) {
      interpreterSetting.appEventListener = applicationEventListener;
      return this;
    }

    public Builder setLifecycleManager(LifecycleManager lifecycleManager) {
      interpreterSetting.lifecycleManager = lifecycleManager;
      return this;
    }

    public Builder setRecoveryStorage(RecoveryStorage recoveryStorage) {
      interpreterSetting.recoveryStorage = recoveryStorage;
      return this;
    }

    public InterpreterSetting create() {
      // post processing
      interpreterSetting.postProcessing();
      return interpreterSetting;
    }
  }

  public InterpreterSetting() {
    ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    this.id = generateId();
    interpreterGroupReadLock = lock.readLock();
    interpreterGroupWriteLock = lock.writeLock();
  }

  void postProcessing() {
    this.id = this.name;
    if (this.lifecycleManager == null) {
      this.lifecycleManager = new NullLifecycleManager(conf);
    }
    if (this.recoveryStorage == null) {
      try {
        this.recoveryStorage = new NullRecoveryStorage(conf, interpreterSettingManager);
      } catch (IOException e) {
        // ignore this exception as NullRecoveryStorage will do nothing.
      }
    }
  }

  /**
   * Create interpreter from InterpreterSettingTemplate
   *
   * @param o interpreterSetting from InterpreterSettingTemplate
   */
  public InterpreterSetting(InterpreterSetting o) {
    this();
    this.id = o.name;
    this.name = o.name;
    this.group = o.group;
    this.properties = convertInterpreterProperties(
        (Map<String, DefaultInterpreterProperty>) o.getProperties());
    this.interpreterInfos = new ArrayList<>(o.getInterpreterInfos());
    this.option = InterpreterOption.fromInterpreterOption(o.getOption());
    this.dependencies = new ArrayList<>(o.getDependencies());
    this.interpreterDir = o.getInterpreterDir();
    this.interpreterRunner = o.getInterpreterRunner();
    this.conf = o.getConf();
  }

  private InterpreterLauncher createLauncher(Properties properties) throws IOException {
    return PluginManager.get().loadInterpreterLauncher(
        getLauncherPlugin(properties), recoveryStorage);
  }

  public AngularObjectRegistryListener getAngularObjectRegistryListener() {
    return angularObjectRegistryListener;
  }

  public RemoteInterpreterProcessListener getRemoteInterpreterProcessListener() {
    return remoteInterpreterProcessListener;
  }

  public ApplicationEventListener getAppEventListener() {
    return appEventListener;
  }

  public DependencyResolver getDependencyResolver() {
    return dependencyResolver;
  }

  public InterpreterSettingManager getInterpreterSettingManager() {
    return interpreterSettingManager;
  }

  public InterpreterSetting setAngularObjectRegistryListener(AngularObjectRegistryListener
                                                   angularObjectRegistryListener) {
    this.angularObjectRegistryListener = angularObjectRegistryListener;
    return this;
  }

  public InterpreterSetting setAppEventListener(ApplicationEventListener appEventListener) {
    this.appEventListener = appEventListener;
    return this;
  }

  public InterpreterSetting setRemoteInterpreterProcessListener(RemoteInterpreterProcessListener
                                                      remoteInterpreterProcessListener) {
    this.remoteInterpreterProcessListener = remoteInterpreterProcessListener;
    return this;
  }

  public InterpreterSetting setDependencyResolver(DependencyResolver dependencyResolver) {
    this.dependencyResolver = dependencyResolver;
    return this;
  }

  public InterpreterSetting setInterpreterSettingManager(
      InterpreterSettingManager interpreterSettingManager) {
    this.interpreterSettingManager = interpreterSettingManager;
    return this;
  }

  public InterpreterSetting setLifecycleManager(LifecycleManager lifecycleManager) {
    this.lifecycleManager = lifecycleManager;
    return this;
  }

  public InterpreterSetting setRecoveryStorage(RecoveryStorage recoveryStorage) {
    this.recoveryStorage = recoveryStorage;
    return this;
  }

  public InterpreterSetting setInterpreterEventServer(
      RemoteInterpreterEventServer interpreterEventServer) {
    this.interpreterEventServer = interpreterEventServer;
    return this;
  }

  public InterpreterInfo getInterpreterInfo(String name) {
    Iterator it = this.interpreterInfos.iterator();
    while (it.hasNext()) {
      InterpreterInfo info = (InterpreterInfo) it.next();
      if (StringUtils.equals(info.getName(), name)) {
        return info;
      }
    }

    return null;
  }

  public Map<String, Object> getConfig(String className) {
    Map<String, Object> configSetting = new HashMap<>();
    for (InterpreterInfo intpInfo : interpreterInfos) {
      if (className.equals(intpInfo.getClassName())) {
         if (intpInfo.getConfig() != null) {
           configSetting = intpInfo.getConfig();
         }
         break;
      }
    }
    return configSetting;
  }

  public RecoveryStorage getRecoveryStorage() {
    return recoveryStorage;
  }

  public LifecycleManager getLifecycleManager() {
    return lifecycleManager;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public String getGroup() {
    return group;
  }

  private String getInterpreterGroupId(ExecutionContext executionContext) {
    if (executionContext.isInIsolatedMode()) {
      return name + "-isolated-" + executionContext.getNoteId() + "-" +
              executionContext.getStartTime();
    }

    List<String> keys = new ArrayList<>();
    if (option.isExistingProcess) {
      keys.add(Constants.EXISTING_PROCESS);
    } else if (getOption().isIsolated()) {
      if (option.perUserIsolated()) {
        keys.add(executionContext.getUser());
      }
      if (option.perNoteIsolated()) {
        keys.add(executionContext.getNoteId());
      }
    } else {
      keys.add(SHARED_PROCESS);
    }

    //TODO(zjffdu) we encode interpreter setting id into groupId, this is not a good design
    return id + "-" + StringUtils.join(keys, "-");
  }

  private String getInterpreterSessionId(ExecutionContext executionContext) {
    String key;
    if (option.isExistingProcess()) {
      key = Constants.EXISTING_PROCESS;
    } else if (option.perNoteScoped() && option.perUserScoped()) {
      key = executionContext.getUser() + ":" + executionContext.getNoteId();
    } else if (option.perUserScoped()) {
      key = executionContext.getUser();
    } else if (option.perNoteScoped()) {
      key = executionContext.getNoteId();
    } else {
      key = SHARED_SESSION;
    }

    return key;
  }

  public ManagedInterpreterGroup getOrCreateInterpreterGroup(String user, String noteId) {
    return getOrCreateInterpreterGroup(new ExecutionContextBuilder().setUser(user).setNoteId(noteId).createExecutionContext());
  }

  public ManagedInterpreterGroup getOrCreateInterpreterGroup(ExecutionContext executionContext) {
    String groupId = getInterpreterGroupId(executionContext);
    try {
      interpreterGroupWriteLock.lock();
      if (!interpreterGroups.containsKey(groupId)) {
        LOGGER.info("Create InterpreterGroup with groupId: {} for {}",
            groupId, executionContext);
        ManagedInterpreterGroup intpGroup = createInterpreterGroup(groupId);
        interpreterGroups.put(groupId, intpGroup);
      }
      return interpreterGroups.get(groupId);
    } finally {
      interpreterGroupWriteLock.unlock();;
    }
  }

  void removeInterpreterGroup(String groupId) {
    try {
      interpreterGroupWriteLock.lock();
      this.interpreterGroups.remove(groupId);
    } finally {
      interpreterGroupWriteLock.unlock();
    }
  }

  public ManagedInterpreterGroup getInterpreterGroup(String user, String noteId) {
    return getInterpreterGroup(new ExecutionContextBuilder().setUser(user).setNoteId(noteId).createExecutionContext());
  }

  public ManagedInterpreterGroup getInterpreterGroup(ExecutionContext executionContext) {
    String groupId = getInterpreterGroupId(executionContext);
    try {
      interpreterGroupReadLock.lock();
      return interpreterGroups.get(groupId);
    } finally {
      interpreterGroupReadLock.unlock();;
    }
  }

  ManagedInterpreterGroup getInterpreterGroup(String groupId) {
    return interpreterGroups.get(groupId);
  }

  public ArrayList<ManagedInterpreterGroup> getAllInterpreterGroups() {
    try {
      interpreterGroupReadLock.lock();
      return new ArrayList(interpreterGroups.values());
    } finally {
      interpreterGroupReadLock.unlock();
    }
  }

  Map<String, Object> getEditorFromSettingByClassName(String className) {
    for (InterpreterInfo intpInfo : interpreterInfos) {
      if (className.equals(intpInfo.getClassName())) {
        if (intpInfo.getEditor() == null) {
          break;
        }
        return intpInfo.getEditor();
      }
    }
    return DEFAULT_EDITOR;
  }

  public void closeInterpreters(String user, String noteId) {
    closeInterpreters(new ExecutionContextBuilder().setUser(user).setNoteId(noteId).createExecutionContext());
  }

  public void closeInterpreters(ExecutionContext executionContext) {
    ManagedInterpreterGroup interpreterGroup = getInterpreterGroup(executionContext);
    if (interpreterGroup != null) {
      String sessionId = getInterpreterSessionId(executionContext);
      interpreterGroup.close(sessionId);
    }
  }

  public void close() {
    LOGGER.info("Close InterpreterSetting: " + name);
    List<Thread> closeThreads = interpreterGroups.values().stream()
            .map(g -> new Thread(g::close, name + "-close"))
            .peek(t -> t.setUncaughtExceptionHandler((th, e) ->
                    LOGGER.error("InterpreterSetting close error", e)))
            .peek(Thread::start)
            .collect(Collectors.toList());
    interpreterGroups.clear();
    for (Thread t : closeThreads) {
      try {
        t.join();
      } catch (InterruptedException e) {
        LOGGER.error("Can't wait InterpreterSetting close threads", e);
        Thread.currentThread().interrupt();
        break;
      }
    }
  }

  public void setProperties(Object object) {
    if (object instanceof StringMap) {
      StringMap<String> map = (StringMap) properties;
      Properties newProperties = new Properties();
      for (String key : map.keySet()) {
        newProperties.put(key, map.get(key));
      }
      this.properties = newProperties;
    } else {
      this.properties = object;
    }
  }

  /**
   * This is just to fix the issue of ZEPPELIN-4672.
   * (TODO zjffdu), we should remove these ungly code after we unify the interpreter properties in
   * interpreter.json & interpreter-setting.json
   * @param propertiesInTemplate
   */
  public void fillPropertyDescription(Object propertiesInTemplate) {
    if (propertiesInTemplate instanceof LinkedHashMap) {
      LinkedHashMap<String, DefaultInterpreterProperty> propertiesInTemplate2 =
              (LinkedHashMap<String, DefaultInterpreterProperty>) propertiesInTemplate;
      if (this.properties instanceof LinkedHashMap) {
        LinkedHashMap<String, InterpreterProperty> newInterpreterProperties = (LinkedHashMap)this.properties;
        for (Map.Entry<String, InterpreterProperty> entry : newInterpreterProperties.entrySet()) {
          if (propertiesInTemplate2.containsKey(entry.getKey())) {
            entry.getValue().setDescription(propertiesInTemplate2.get(entry.getKey()).getDescription());
          }
        }
        this.properties = newInterpreterProperties;
      }
    }
  }

  /**
   * This method will sort the properties by the order defined in template.
   * It is because when interpreter setting is loaded in interpreter-setting.json, it is
   * still not in correct order.
   * @param propertiesInTemplate
   */
  public void sortPropertiesByTemplate(Object propertiesInTemplate) {
    if (propertiesInTemplate instanceof LinkedHashMap) {
      List<String> sortedKeys = new ArrayList(((LinkedHashMap) propertiesInTemplate).keySet());
      if (this.properties instanceof LinkedHashMap) {
        LinkedHashMap<String, InterpreterProperty> unSortedProperties = (LinkedHashMap) this.properties;
        List<String> keys = new ArrayList(unSortedProperties.keySet());
        keys.sort((o1, o2) -> {
          int i1 = sortedKeys.indexOf(o1);
          int i2 = sortedKeys.indexOf(o2);
          if (i1 != -1 && i2 != -1) {
            if (i1 < i2) {
              return -1;
            } else if (i1 > i2) {
              return 1;
            } else {
              return 0;
            }
          } else {
            if (i1 == -1) {
              return 1;
            } else {
              return -1;
            }
          }
        });

        LinkedHashMap<String, InterpreterProperty> sortedProperties = new LinkedHashMap<>();
        for (String key : keys) {
          sortedProperties.put(key, unSortedProperties.get(key));
        }
        this.properties = sortedProperties;
      }
    }
  }

  public Object getProperties() {
    return properties;
  }

  @VisibleForTesting
  public void setProperty(String name, String value) {
    ((Map<String, InterpreterProperty>) properties).put(name, new InterpreterProperty(name, value));
  }

  // This method is supposed to be only called by InterpreterSetting
  // but not InterpreterSetting Template
  public Properties getJavaProperties() {
    Properties jProperties = new Properties();
    Map<String, InterpreterProperty> iProperties = (Map<String, InterpreterProperty>) properties;
    for (Map.Entry<String, InterpreterProperty> entry : iProperties.entrySet()) {
      if (entry.getValue().getValue() != null) {
        jProperties.setProperty(entry.getKey().trim(),
            entry.getValue().getValue().toString().trim());
      }
    }

    if (!jProperties.containsKey("zeppelin.interpreter.output.limit")) {
      jProperties.setProperty("zeppelin.interpreter.output.limit",
          conf.getInt(ZEPPELIN_INTERPRETER_OUTPUT_LIMIT) + "");
    }

    if (!jProperties.containsKey("zeppelin.interpreter.max.poolsize")) {
      jProperties.setProperty("zeppelin.interpreter.max.poolsize",
          conf.getInt(ZEPPELIN_INTERPRETER_MAX_POOL_SIZE) + "");
    }

    String interpreterLocalRepoPath = conf.getInterpreterLocalRepoPath();
    //TODO(zjffdu) change it to interpreterDir/{interpreter_name}
    jProperties.setProperty("zeppelin.interpreter.localRepo",
        interpreterLocalRepoPath + "/" + id);
    return jProperties;
  }

  public ZeppelinConfiguration getConf() {
    return conf;
  }

  public InterpreterSetting setConf(ZeppelinConfiguration conf) {
    this.conf = conf;
    return this;
  }

  public List<Dependency> getDependencies() {
    return dependencies;
  }

  public void setDependencies(List<Dependency> dependencies) {
    this.dependencies = dependencies;
    loadInterpreterDependencies();
  }

  public InterpreterOption getOption() {
    return option;
  }

  public void setOption(InterpreterOption option) {
    this.option = option;
  }

  public String getInterpreterDir() {
    return interpreterDir;
  }

  public void setInterpreterDir(String interpreterDir) {
    this.interpreterDir = interpreterDir;
  }

  public List<InterpreterInfo> getInterpreterInfos() {
    return interpreterInfos;
  }

  void appendDependencies(List<Dependency> dependencies) {
    for (Dependency dependency : dependencies) {
      if (!this.dependencies.contains(dependency)) {
        this.dependencies.add(dependency);
      }
    }
    loadInterpreterDependencies();
  }

  void setInterpreterOption(InterpreterOption interpreterOption) {
    this.option = interpreterOption;
  }

  public void setProperties(Properties p) {
    this.properties = p;
  }

  void setGroup(String group) {
    this.group = group;
  }

  void setName(String name) {
    this.name = name;
  }

  /***
   * Interpreter status
   */
  public enum Status {
    DOWNLOADING_DEPENDENCIES,
    ERROR,
    READY
  }

  public Status getStatus() {
    return status;
  }

  public void setStatus(Status status) {
    LOGGER.info(String.format("Set interpreter %s status to %s", name, status.name()));
    this.status = status;
  }

  public String getErrorReason() {
    return errorReason;
  }

  public void setErrorReason(String errorReason) {
    this.errorReason = errorReason;
  }

  public void setInterpreterInfos(List<InterpreterInfo> interpreterInfos) {
    this.interpreterInfos = interpreterInfos;
  }

  public InterpreterRunner getInterpreterRunner() {
    return interpreterRunner;
  }

  public void setInterpreterRunner(InterpreterRunner interpreterRunner) {
    this.interpreterRunner = interpreterRunner;
  }

  public String getLauncherPlugin(Properties properties) {
    if (isRunningOnKubernetes()) {
      return "K8sStandardInterpreterLauncher";
    } else if (isRunningOnCluster()) {
      return InterpreterSetting.CLUSTER_INTERPRETER_LAUNCHER_NAME;
    } if (isRunningOnDocker()) {
      return "DockerInterpreterLauncher";
    } else {
      String launcher = properties.getProperty("zeppelin.interpreter.launcher");
      LOGGER.debug("zeppelin.interpreter.launcher: " + launcher);
      if (group.equals("spark")) {
        return "SparkInterpreterLauncher";
      } else if (group.equals("flink")) {
        if ("yarn".equals(launcher)) {
          return "YarnInterpreterLauncher";
        }
        return "FlinkInterpreterLauncher";
      } else {
        if ("yarn".equals(launcher)) {
          return "YarnInterpreterLauncher";
        }
        return "StandardInterpreterLauncher";
      }
    }
  }

  private boolean isRunningOnKubernetes() {
    return conf.getRunMode() == ZeppelinConfiguration.RUN_MODE.K8S;
  }


  private boolean isRunningOnCluster() {
    return conf.isClusterMode();
  }

  private boolean isRunningOnDocker() {
    return conf.getRunMode() == ZeppelinConfiguration.RUN_MODE.DOCKER;
  }

  public boolean isUserAuthorized(List<String> userAndRoles) {
    if (!option.permissionIsSet()) {
      return true;
    }
    Set<String> intersection = new HashSet<>(userAndRoles);
    intersection.retainAll(option.getOwners());
    return !intersection.isEmpty();
  }

  //////////////////////////// IMPORTANT ////////////////////////////////////////////////
  ///////////////////////////////////////////////////////////////////////////////////////
  ///////////////////////////////////////////////////////////////////////////////////////
  // This is the only place to create interpreters. For now we always create multiple interpreter
  // together (one session). We don't support to create single interpreter yet.
  List<Interpreter> createInterpreters(String user, String interpreterGroupId, String sessionId) {
    List<Interpreter> interpreters = new ArrayList<>();
    List<InterpreterInfo> interpreterInfos = getInterpreterInfos();
    Properties intpProperties = getJavaProperties();
    for (InterpreterInfo info : interpreterInfos) {
      Interpreter interpreter = new RemoteInterpreter(intpProperties, sessionId,
          info.getClassName(), user, lifecycleManager);
      if (info.isDefaultInterpreter()) {
        interpreters.add(0, interpreter);
      } else {
        interpreters.add(interpreter);
      }
      LOGGER.info("Interpreter {} created for user: {}, sessionId: {}",
          interpreter.getClassName(), user, sessionId);
    }

    // TODO(zjffdu) this kind of hardcode is ugly. For now SessionConfInterpreter is used
    // for livy, we could add new property in interpreter-setting.json when there's new interpreter
    // require SessionConfInterpreter
    if (group.equals("livy")) {
      interpreters.add(
          new SessionConfInterpreter(intpProperties, sessionId, interpreterGroupId, this));
    } else {
      interpreters.add(new ConfInterpreter(intpProperties, sessionId, interpreterGroupId, this));
    }
    return interpreters;
  }

  synchronized RemoteInterpreterProcess createInterpreterProcess(String interpreterGroupId,
                                                                 String userName,
                                                                 Properties properties)
      throws IOException {
    InterpreterLauncher launcher = createLauncher(properties);
    InterpreterLaunchContext launchContext = new
        InterpreterLaunchContext(properties, option, interpreterRunner, userName,
        interpreterGroupId, id, group, name, interpreterEventServer.getPort(), interpreterEventServer.getHost());
    RemoteInterpreterProcess process = (RemoteInterpreterProcess) launcher.launch(launchContext);
    recoveryStorage.onInterpreterClientStart(process);
    return process;
  }

  List<Interpreter> getOrCreateSession(String user, String noteId) {
    return getOrCreateSession(new ExecutionContextBuilder().setUser(user).setNoteId(noteId).createExecutionContext());
  }

  List<Interpreter> getOrCreateSession(ExecutionContext executionContext) {
    ManagedInterpreterGroup interpreterGroup = getOrCreateInterpreterGroup(executionContext);
    Preconditions.checkNotNull(interpreterGroup, "No InterpreterGroup existed for {}", executionContext);
    String sessionId = getInterpreterSessionId(executionContext);
    return interpreterGroup.getOrCreateSession(executionContext.getUser(), sessionId);
  }

  public Interpreter getDefaultInterpreter(String user, String noteId) {
    return getOrCreateSession(new ExecutionContextBuilder().setUser(user).setNoteId(noteId).createExecutionContext()).get(0);
  }

  public Interpreter getDefaultInterpreter(ExecutionContext executionContext) {
    return getOrCreateSession(executionContext).get(0);
  }

  public Interpreter getInterpreter(String user, String noteId, String replName) {
    return getInterpreter(new ExecutionContextBuilder().setUser(user).setNoteId(noteId).createExecutionContext(), replName);
  }

  public Interpreter getInterpreter(ExecutionContext executionContext, String replName) {
    Preconditions.checkNotNull(replName, "replName should be not null");

    String className = getInterpreterClassFromInterpreterSetting(replName);
    if (className == null) {
      return null;
    }
    List<Interpreter> interpreters = getOrCreateSession(executionContext);
    for (Interpreter interpreter : interpreters) {
      if (className.equals(interpreter.getClassName())) {
        return interpreter;
      }
    }
    return null;
  }

  private String getInterpreterClassFromInterpreterSetting(String replName) {
    Preconditions.checkNotNull(replName, "replName should be not null");

    for (InterpreterInfo info : interpreterInfos) {
      String infoName = info.getName();
      if (null != info.getName() && replName.equals(infoName)) {
        return info.getClassName();
      }
    }
    //TODO(zjffdu) It requires user can not create interpreter with name `conf`,
    // conf is a reserved word of interpreter name
    if (replName.equals("conf")) {
      if (group.equals("livy")) {
        return SessionConfInterpreter.class.getName();
      } else {
        return ConfInterpreter.class.getName();
      }
    }
    return null;
  }

  private ManagedInterpreterGroup createInterpreterGroup(String groupId) {
    AngularObjectRegistry angularObjectRegistry;
    ManagedInterpreterGroup interpreterGroup = new ManagedInterpreterGroup(groupId, this);
    angularObjectRegistry =
        new RemoteAngularObjectRegistry(groupId, angularObjectRegistryListener, interpreterGroup);
    interpreterGroup.setAngularObjectRegistry(angularObjectRegistry);
    return interpreterGroup;
  }

  /**
   * Throw exception when interpreter process has already launched
   *
   * @param interpreterGroupId
   * @param properties
   * @throws IOException
   */
  public void setInterpreterGroupProperties(String interpreterGroupId, Properties properties)
      throws IOException {
    ManagedInterpreterGroup interpreterGroup = this.interpreterGroups.get(interpreterGroupId);
    for (List<Interpreter> session : interpreterGroup.sessions.values()) {
      for (Interpreter intp : session) {
        if (!intp.getProperties().equals(properties) &&
            interpreterGroup.getRemoteInterpreterProcess() != null &&
            interpreterGroup.getRemoteInterpreterProcess().isRunning()) {
          throw new IOException("Can not change interpreter properties when interpreter process " +
              "has already been launched");
        }
        intp.setProperties(properties);
      }
    }
  }

  private void loadInterpreterDependencies() {
    setStatus(Status.DOWNLOADING_DEPENDENCIES);
    setErrorReason(null);
    Thread t = new Thread() {
      public void run() {
        try {
          // dependencies to prevent library conflict
          File localRepoDir = new File(conf.getInterpreterLocalRepoPath() + "/" + id);
          if (localRepoDir.exists()) {
            try {
              FileUtils.forceDelete(localRepoDir);
            } catch (FileNotFoundException e) {
              LOGGER.info("A file that does not exist cannot be deleted, nothing to worry", e);
            }
          }

          // load dependencies
          List<Dependency> deps = getDependencies();
          if (deps != null && !deps.isEmpty()) {
            LOGGER.info("Start to download dependencies for interpreter: " + name);
            for (Dependency d : deps) {
              File destDir = new File(
                  conf.getRelativeDir(ZeppelinConfiguration.ConfVars.ZEPPELIN_DEP_LOCALREPO));

              if (d.getExclusions() != null) {
                dependencyResolver.load(d.getGroupArtifactVersion(), d.getExclusions(),
                    new File(destDir, id));
              } else {
                dependencyResolver
                    .load(d.getGroupArtifactVersion(), new File(destDir, id));
              }
            }
            LOGGER.info("Finish downloading dependencies for interpreter: " + name);
          }

          setStatus(Status.READY);
          setErrorReason(null);
        } catch (Exception e) {
          LOGGER.error(String.format("Error while downloading repos for interpreter group : %s," +
                  " go to interpreter setting page click on edit and save it again to make " +
                  "this interpreter work properly. : %s",
              getGroup(), e.getLocalizedMessage()), e);
          setErrorReason(e.getLocalizedMessage());
          setStatus(Status.ERROR);
        }
      }
    };

    t.start();
  }

  //TODO(zjffdu) ugly code, should not use JsonObject as parameter. not readable
  public void convertPermissionsFromUsersToOwners(JsonObject jsonObject) {
    if (jsonObject != null) {
      JsonObject option = jsonObject.getAsJsonObject("option");
      if (option != null) {
        JsonArray users = option.getAsJsonArray("users");
        if (users != null) {
          if (this.option.getOwners() == null) {
            this.option.owners = new LinkedList<>();
          }
          for (JsonElement user : users) {
            this.option.getOwners().add(user.getAsString());
          }
        }
      }
    }
  }

  // For backward compatibility of interpreter.json format after ZEPPELIN-2403
  static Map<String, InterpreterProperty> convertInterpreterProperties(Object properties) {
    if (properties != null && properties instanceof StringMap) {
      Map<String, InterpreterProperty> newProperties = new LinkedHashMap<>();
      StringMap p = (StringMap) properties;
      for (Object o : p.entrySet()) {
        Map.Entry entry = (Map.Entry) o;
        if (!(entry.getValue() instanceof StringMap)) {
          InterpreterProperty newProperty = new InterpreterProperty(
              entry.getKey().toString(),
              entry.getValue(),
              InterpreterPropertyType.STRING.getValue());
          newProperties.put(entry.getKey().toString(), newProperty);
        } else {
          StringMap stringMap = (StringMap) entry.getValue();
          InterpreterProperty newProperty = new InterpreterProperty(
                  entry.getKey().toString(),
                  stringMap.get("value"),
                  stringMap.containsKey("type") ? stringMap.get("type").toString() : "string");
          newProperties.put(newProperty.getName(), newProperty);
        }
      }
      return newProperties;

    } else if (properties instanceof Map) {
      Map<String, Object> dProperties =
          (Map<String, Object>) properties;
      Map<String, InterpreterProperty> newProperties = new LinkedHashMap<>();
      for (String key : dProperties.keySet()) {
        Object value = dProperties.get(key);
        if (value instanceof InterpreterProperty) {
          return (Map<String, InterpreterProperty>) properties;
        } else if (value instanceof StringMap) {
          StringMap stringMap = (StringMap) value;
          InterpreterProperty newProperty = new InterpreterProperty(
              key,
              stringMap.get("value"),
              stringMap.containsKey("type") ? stringMap.get("type").toString() : "string");

          newProperties.put(newProperty.getName(), newProperty);
        } else if (value instanceof DefaultInterpreterProperty){
          DefaultInterpreterProperty dProperty = (DefaultInterpreterProperty) value;
          InterpreterProperty property = new InterpreterProperty(
              key,
              dProperty.getValue(),
              dProperty.getType() != null ? dProperty.getType() : "string",
              dProperty.getDescription()
              // in case user forget to specify type in interpreter-setting.json
          );
          newProperties.put(key, property);
        } else if (value instanceof String) {
          InterpreterProperty newProperty = new InterpreterProperty(
              key,
              value,
              "string");

          newProperties.put(newProperty.getName(), newProperty);
        } else {
          throw new RuntimeException("Can not convert this type of property: " +
              value.getClass());
        }
      }
      return newProperties;
    }
    throw new RuntimeException("Can not convert this type: " + properties.getClass());
  }

  public void waitForReady(long timeout) throws InterpreterException {
    long start = System.currentTimeMillis();
    while(status != Status.READY) {
      try {
        Thread.sleep(1000);
      } catch (InterruptedException e) {
        throw new InterpreterException(e);
      }
      long now = System.currentTimeMillis();
      if ((now - start) > timeout) {
        throw new InterpreterException("Fail to download dependencies in " + timeout / 1000
                + " seconds");
      }
    }
  }

  public void waitForReady() throws InterpreterException {
    waitForReady(Long.MAX_VALUE);
  }

  public InterpreterInfo getDefaultInterpreterInfo() throws Exception {
    for (InterpreterInfo interpreterInfo : interpreterInfos) {
      if (interpreterInfo.isDefaultInterpreter()) {
        return interpreterInfo;
      }
    }
    if (interpreterInfos.size() == 1) {
      return interpreterInfos.get(0);
    }
    throw new Exception("No default interpreter info found in interpreter setting: " + name);
  }

  public static String toJson(InterpreterSetting intpSetting) {
    Gson gson = new GsonBuilder().setPrettyPrinting().create();

    StringWriter stringWriter = new StringWriter();
    JsonWriter jsonWriter = new JsonWriter(stringWriter);
    try {
      // id
      jsonWriter.beginObject();
      jsonWriter.name("id");
      jsonWriter.value(intpSetting.getId());

      // name
      jsonWriter.name("name");
      jsonWriter.value(intpSetting.getName());

      // group
      jsonWriter.name("group");
      jsonWriter.value(intpSetting.getGroup());

      // dependencies
      jsonWriter.name("dependencies");
      String jsonDep = gson.toJson(intpSetting.getDependencies(), new TypeToken<List<Dependency>>() {
      }.getType());
      jsonWriter.value(jsonDep);

      // properties
      jsonWriter.name("properties");
      String jsonProps = gson.toJson(intpSetting.getProperties(), new TypeToken<Map<String, InterpreterProperty>>() {
      }.getType());
      jsonWriter.value(jsonProps);

      // interpreterOption
      jsonWriter.name("interpreterOption");
      String jsonOption = gson.toJson(intpSetting.getOption(), new TypeToken<InterpreterOption>() {
      }.getType());
      jsonWriter.value(jsonOption);

      // interpreterGroup
      jsonWriter.name("interpreterGroup");
      String jsonIntpInfos = gson.toJson(intpSetting.getInterpreterInfos(), new TypeToken<List<InterpreterInfo>>() {
      }.getType());
      jsonWriter.value(jsonIntpInfos);

      jsonWriter.endObject();
      jsonWriter.flush();
    } catch (IOException e) {
      LOGGER.error(e.getMessage(), e);
    }

    return stringWriter.getBuffer().toString();
  }

  public static InterpreterSetting fromJson(String json) {
    Gson gson = new GsonBuilder().setPrettyPrinting().create();

    StringReader stringReader = new StringReader(json);
    JsonReader jsonReader = new JsonReader(stringReader);
    InterpreterSetting intpSetting = new InterpreterSetting();
    try {
      jsonReader.beginObject();
      while (jsonReader.hasNext()) {
        String tag = jsonReader.nextName();
        if (tag.equals("id")) {
          String id = jsonReader.nextString();
          intpSetting.setId(id);
        } else if (tag.equals("name")) {
          String name = jsonReader.nextString();
          intpSetting.setName(name);
        } else if (tag.equals("group")) {
          String group = jsonReader.nextString();
          intpSetting.setGroup(group);
        } else if (tag.equals("dependencies")) {
          String strDep = jsonReader.nextString();
          List<Dependency> dependencies = gson.fromJson(strDep, new TypeToken<List<Dependency>>() {}.getType());
          intpSetting.setDependencies(dependencies);
        } else if (tag.equals("properties")) {
          String strProp = jsonReader.nextString();
          Map<String, InterpreterProperty> properties = gson.fromJson(strProp,
              new TypeToken<Map<String, InterpreterProperty>>() {}.getType());
          intpSetting.setProperties(properties);
        } else if (tag.equals("interpreterOption")) {
          String strOption = jsonReader.nextString();
          InterpreterOption intpOption = gson.fromJson(strOption, new TypeToken<InterpreterOption>() {}.getType());
          intpSetting.setOption(intpOption);
        } else if (tag.equals("interpreterGroup")) {
          String strIntpInfos = jsonReader.nextString();
          List<InterpreterInfo> intpInfos = gson.fromJson(strIntpInfos, new TypeToken<List<InterpreterInfo>>() {}.getType());
          intpSetting.setInterpreterInfos(intpInfos);
        } else {
          LOGGER.error("Error data type!");
        }
      }
      jsonReader.endObject();
      jsonReader.close();
    } catch (IOException e) {
      LOGGER.error(e.getMessage(), e);
    }

    return intpSetting;
  }
}
