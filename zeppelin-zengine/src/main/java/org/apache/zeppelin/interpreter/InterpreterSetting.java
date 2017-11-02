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
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
import com.google.gson.internal.StringMap;
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
import org.apache.zeppelin.interpreter.launcher.ShellScriptLauncher;
import org.apache.zeppelin.interpreter.launcher.SparkInterpreterLauncher;
import org.apache.zeppelin.interpreter.lifecycle.NullLifecycleManager;
import org.apache.zeppelin.interpreter.remote.RemoteAngularObjectRegistry;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreter;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterEventPoller;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterProcess;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterProcessListener;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

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

  private Status status;
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

  private transient Map<String, String> infos;

  // Map of the note and paragraphs which has runtime infos generated by this interpreter setting.
  // This map is used to clear the infos in paragraph when the interpretersetting is restarted
  private transient Map<String, Set<String>> runtimeInfosToBeCleared;

  private transient ZeppelinConfiguration conf = new ZeppelinConfiguration();

  // TODO(zjffdu) ShellScriptLauncher is the only launcher implemention for now. It could be other
  // launcher in future when we have other launcher implementation. e.g. third party launcher
  // service like livy
  private transient InterpreterLauncher launcher;
  ///////////////////////////////////////////////////////////////////////////////////////////

  private transient LifecycleManager lifecycleManager;

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
    this.status = Status.READY;
    if (this.lifecycleManager == null) {
      this.lifecycleManager = new NullLifecycleManager(conf);
    }
  }

  /**
   * Create interpreter from InterpreterSettingTemplate
   *
   * @param o interpreterSetting from InterpreterSettingTemplate
   */
  public InterpreterSetting(InterpreterSetting o) {
    this();
    this.id = generateId();
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

  private void createLauncher() {
    if (group.equals("spark")) {
      this.launcher = new SparkInterpreterLauncher(this.conf);
    } else {
      this.launcher = new ShellScriptLauncher(this.conf);
    }
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

  public void setAngularObjectRegistryListener(AngularObjectRegistryListener
                                                   angularObjectRegistryListener) {
    this.angularObjectRegistryListener = angularObjectRegistryListener;
  }

  public void setAppEventListener(ApplicationEventListener appEventListener) {
    this.appEventListener = appEventListener;
  }

  public void setRemoteInterpreterProcessListener(RemoteInterpreterProcessListener
                                                      remoteInterpreterProcessListener) {
    this.remoteInterpreterProcessListener = remoteInterpreterProcessListener;
  }

  public void setDependencyResolver(DependencyResolver dependencyResolver) {
    this.dependencyResolver = dependencyResolver;
  }

  public void setInterpreterSettingManager(InterpreterSettingManager interpreterSettingManager) {
    this.interpreterSettingManager = interpreterSettingManager;
  }

  public void setLifecycleManager(LifecycleManager lifecycleManager) {
    this.lifecycleManager = lifecycleManager;
  }

  public LifecycleManager getLifecycleManager() {
    return lifecycleManager;
  }

  public String getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public String getGroup() {
    return group;
  }

  private String getInterpreterGroupId(String user, String noteId) {
    String key;
    if (option.isExistingProcess) {
      key = Constants.EXISTING_PROCESS;
    } else if (getOption().isProcess()) {
      key = (option.perUserIsolated() ? user : "") + ":" + (option.perNoteIsolated() ? noteId : "");
    } else {
      key = SHARED_PROCESS;
    }

    //TODO(zjffdu) we encode interpreter setting id into groupId, this is not a good design
    return id + ":" + key;
  }

  private String getInterpreterSessionId(String user, String noteId) {
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

    return key;
  }

  public ManagedInterpreterGroup getOrCreateInterpreterGroup(String user, String noteId) {
    String groupId = getInterpreterGroupId(user, noteId);
    try {
      interpreterGroupWriteLock.lock();
      if (!interpreterGroups.containsKey(groupId)) {
        LOGGER.info("Create InterpreterGroup with groupId: {} for user: {} and note: {}",
            groupId, user, noteId);
        ManagedInterpreterGroup intpGroup = createInterpreterGroup(groupId);
        interpreterGroups.put(groupId, intpGroup);
      }
      return interpreterGroups.get(groupId);
    } finally {
      interpreterGroupWriteLock.unlock();;
    }
  }

  void removeInterpreterGroup(String groupId) {
    this.interpreterGroups.remove(groupId);
  }

  public ManagedInterpreterGroup getInterpreterGroup(String user, String noteId) {
    String groupId = getInterpreterGroupId(user, noteId);
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

  @VisibleForTesting
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

  void closeInterpreters(String user, String noteId) {
    ManagedInterpreterGroup interpreterGroup = getInterpreterGroup(user, noteId);
    if (interpreterGroup != null) {
      String sessionId = getInterpreterSessionId(user, noteId);
      interpreterGroup.close(sessionId);
    }
  }

  public void close() {
    LOGGER.info("Close InterpreterSetting: " + name);
    for (ManagedInterpreterGroup intpGroup : interpreterGroups.values()) {
      intpGroup.close();
    }
    interpreterGroups.clear();
    this.runtimeInfosToBeCleared = null;
    this.infos = null;
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
        jProperties.setProperty(entry.getKey(), entry.getValue().getValue().toString());
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

  public void setConf(ZeppelinConfiguration conf) {
    this.conf = conf;
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

  public void setInfos(Map<String, String> infos) {
    this.infos = infos;
  }

  public Map<String, String> getInfos() {
    return infos;
  }

  public InterpreterRunner getInterpreterRunner() {
    return interpreterRunner;
  }

  public void setInterpreterRunner(InterpreterRunner interpreterRunner) {
    this.interpreterRunner = interpreterRunner;
  }

  public void addNoteToPara(String noteId, String paraId) {
    if (runtimeInfosToBeCleared == null) {
      runtimeInfosToBeCleared = new HashMap<>();
    }
    Set<String> paraIdSet = runtimeInfosToBeCleared.get(noteId);
    if (paraIdSet == null) {
      paraIdSet = new HashSet<>();
      runtimeInfosToBeCleared.put(noteId, paraIdSet);
    }
    paraIdSet.add(paraId);
  }

  public Map<String, Set<String>> getNoteIdAndParaMap() {
    return runtimeInfosToBeCleared;
  }

  public void clearNoteIdAndParaMap() {
    runtimeInfosToBeCleared = null;
  }


  //////////////////////////// IMPORTANT ////////////////////////////////////////////////
  ///////////////////////////////////////////////////////////////////////////////////////
  ///////////////////////////////////////////////////////////////////////////////////////
  // This is the only place to create interpreters. For now we always create multiple interpreter
  // together (one session). We don't support to create single interpreter yet.
  List<Interpreter> createInterpreters(String user, String sessionId) {
    List<Interpreter> interpreters = new ArrayList<>();
    List<InterpreterInfo> interpreterInfos = getInterpreterInfos();
    for (InterpreterInfo info : interpreterInfos) {
      Interpreter interpreter = null;
      interpreter = new RemoteInterpreter(getJavaProperties(), sessionId,
          info.getClassName(), user, lifecycleManager);
      if (info.isDefaultInterpreter()) {
        interpreters.add(0, interpreter);
      } else {
        interpreters.add(interpreter);
      }
      LOGGER.info("Interpreter {} created for user: {}, sessionId: {}",
          interpreter.getClassName(), user, sessionId);
    }
    return interpreters;
  }

  synchronized RemoteInterpreterProcess createInterpreterProcess() throws IOException {
    if (launcher == null) {
      createLauncher();
    }
    InterpreterLaunchContext launchContext = new
        InterpreterLaunchContext(getJavaProperties(), option, interpreterRunner, id, group);
    RemoteInterpreterProcess process = (RemoteInterpreterProcess) launcher.launch(launchContext);
    process.setRemoteInterpreterEventPoller(
        new RemoteInterpreterEventPoller(remoteInterpreterProcessListener, appEventListener));
    return process;
  }

  List<Interpreter> getOrCreateSession(String user, String noteId) {
    ManagedInterpreterGroup interpreterGroup = getOrCreateInterpreterGroup(user, noteId);
    Preconditions.checkNotNull(interpreterGroup, "No InterpreterGroup existed for user {}, " +
        "noteId {}", user, noteId);
    String sessionId = getInterpreterSessionId(user, noteId);
    return interpreterGroup.getOrCreateSession(user, sessionId);
  }

  public Interpreter getDefaultInterpreter(String user, String noteId) {
    return getOrCreateSession(user, noteId).get(0);
  }

  public Interpreter getInterpreter(String user, String noteId, String replName) {
    Preconditions.checkNotNull(noteId, "noteId should be not null");
    Preconditions.checkNotNull(replName, "replName should be not null");

    String className = getInterpreterClassFromInterpreterSetting(replName);
    if (className == null) {
      return null;
    }
    List<Interpreter> interpreters = getOrCreateSession(user, noteId);
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

  private void loadInterpreterDependencies() {
    setStatus(Status.DOWNLOADING_DEPENDENCIES);
    setErrorReason(null);
    Thread t = new Thread() {
      public void run() {
        try {
          // dependencies to prevent library conflict
          File localRepoDir = new File(conf.getInterpreterLocalRepoPath() + "/" + getId());
          if (localRepoDir.exists()) {
            try {
              FileUtils.forceDelete(localRepoDir);
            } catch (FileNotFoundException e) {
              LOGGER.info("A file that does not exist cannot be deleted, nothing to worry", e);
            }
          }

          // load dependencies
          List<Dependency> deps = getDependencies();
          if (deps != null) {
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
      Map<String, InterpreterProperty> newProperties = new HashMap<>();
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
          // already converted
          return (Map<String, InterpreterProperty>) properties;
        }
      }
      return newProperties;

    } else if (properties instanceof Map) {
      Map<String, Object> dProperties =
          (Map<String, Object>) properties;
      Map<String, InterpreterProperty> newProperties = new HashMap<>();
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
              dProperty.getType() != null ? dProperty.getType() : "string"
              // in case user forget to specify type in interpreter-setting.json
          );
          newProperties.put(key, property);
        } else {
          throw new RuntimeException("Can not convert this type of property: " +
              value.getClass());
        }
      }
      return newProperties;
    }
    throw new RuntimeException("Can not convert this type: " + properties.getClass());
  }

  public void waitForReady() throws InterruptedException {
    while (getStatus().equals(
        org.apache.zeppelin.interpreter.InterpreterSetting.Status.DOWNLOADING_DEPENDENCIES)) {
      Thread.sleep(200);
    }
  }
}
