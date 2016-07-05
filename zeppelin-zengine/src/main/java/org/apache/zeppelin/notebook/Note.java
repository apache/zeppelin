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

package org.apache.zeppelin.notebook;

import java.io.IOException;
import java.io.Serializable;
import java.util.*;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.lang.StringUtils;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.display.AngularObject;
import org.apache.zeppelin.display.AngularObjectRegistry;
import org.apache.zeppelin.display.Input;
import org.apache.zeppelin.helium.HeliumApplicationFactory;
import org.apache.zeppelin.interpreter.*;
import org.apache.zeppelin.interpreter.remote.RemoteAngularObjectRegistry;
import org.apache.zeppelin.interpreter.thrift.InterpreterCompletion;
import org.apache.zeppelin.notebook.repo.NotebookRepo;
import org.apache.zeppelin.notebook.utility.IdHashes;
import org.apache.zeppelin.resource.ResourcePoolUtils;
import org.apache.zeppelin.scheduler.Job;
import org.apache.zeppelin.scheduler.Job.Status;
import org.apache.zeppelin.scheduler.JobListener;
import org.apache.zeppelin.search.SearchService;

import com.google.common.base.Optional;
import com.google.gson.Gson;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.apache.zeppelin.user.Credentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Binded interpreters for a note
 */
public class Note implements Serializable, ParagraphJobListener {
  static Logger logger = LoggerFactory.getLogger(Note.class);
  private static final long serialVersionUID = 7920699076577612429L;

  // threadpool for delayed persist of note
  private static final ScheduledThreadPoolExecutor delayedPersistThreadPool =
          new ScheduledThreadPoolExecutor(0);
  static {
    delayedPersistThreadPool.setRemoveOnCancelPolicy(true);
  }

  final List<Paragraph> paragraphs = new LinkedList<>();

  private String name = "";
  private String id;

  private AtomicReference<String> lastReplName = new AtomicReference<>(StringUtils.EMPTY);
  private transient ZeppelinConfiguration conf = ZeppelinConfiguration.create();

  @SuppressWarnings("rawtypes")
  Map<String, List<AngularObject>> angularObjects = new HashMap<>();

  private transient InterpreterFactory factory;
  private transient JobListenerFactory jobListenerFactory;
  private transient NotebookRepo repo;
  private transient SearchService index;
  private transient ScheduledFuture delayedPersist;
  private transient NoteEventListener noteEventListener;
  private transient Credentials credentials;

  /**
   * note configurations.
   *
   * - looknfeel - cron
   */
  private Map<String, Object> config = new HashMap<>();

  /**
   * note information.
   *
   * - cron : cron expression validity.
   */
  private Map<String, Object> info = new HashMap<>();


  public Note() {}

  public Note(NotebookRepo repo, InterpreterFactory factory,
      JobListenerFactory jlFactory, SearchService noteIndex, Credentials credentials,
      NoteEventListener noteEventListener) {
    this.repo = repo;
    this.factory = factory;
    this.jobListenerFactory = jlFactory;
    this.index = noteIndex;
    this.noteEventListener = noteEventListener;
    this.credentials = credentials;
    generateId();
  }

  private void generateId() {
    id = IdHashes.encode(System.currentTimeMillis() + new Random().nextInt());
  }

  private String getDefaultInterpreterName() {
    InterpreterSetting setting = factory.getDefaultInterpreterSetting(getId());
    return null != setting ? setting.getGroup() : StringUtils.EMPTY;
  }

  void putDefaultReplName() {
    String defaultInterpreterName = getDefaultInterpreterName();
    logger.info("defaultInterpreterName is '{}'", defaultInterpreterName);
    lastReplName.set(defaultInterpreterName);
  }

  public String id() {
    return id;
  }

  public String getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  private String normalizeNoteName(String name){
    name = name.trim();
    name = name.replace("\\", "/");
    while (name.indexOf("///") >= 0) {
      name = name.replaceAll("///", "/");
    }
    name = name.replaceAll("//", "/");
    if (name.length() == 0) {
      name = "/";
    }
    return name;
  }

  public void setName(String name) {
    if (name.indexOf('/') >= 0 || name.indexOf('\\') >= 0) {
      name = normalizeNoteName(name);
    }
    this.name = name;
  }

  public void setInterpreterFactory(InterpreterFactory factory) {
    this.factory = factory;
  }

  public JobListenerFactory getJobListenerFactory() {
    return jobListenerFactory;
  }

  public void setJobListenerFactory(JobListenerFactory jobListenerFactory) {
    this.jobListenerFactory = jobListenerFactory;
  }

  public NotebookRepo getNotebookRepo() {
    return repo;
  }

  public void setNotebookRepo(NotebookRepo repo) {
    this.repo = repo;
  }

  public void setIndex(SearchService index) {
    this.index = index;
  }

  public Credentials getCredentials() {
    return credentials;
  };

  public void setCredentials(Credentials credentials) {
    this.credentials = credentials;
  }


  @SuppressWarnings("rawtypes")
  public Map<String, List<AngularObject>> getAngularObjects() {
    return angularObjects;
  }

  /**
   * Add paragraph last.
   */

  public Paragraph addParagraph() {
    Paragraph p = new Paragraph(this, this, factory);
    addLastReplNameIfEmptyText(p);
    synchronized (paragraphs) {
      paragraphs.add(p);
    }
    if (noteEventListener != null) {
      noteEventListener.onParagraphCreate(p);
    }
    return p;
  }

  /**
   * Clone paragraph and add it to note.
   *
   * @param srcParagraph
   */
  public void addCloneParagraph(Paragraph srcParagraph) {

    // Keep paragraph original ID
    final Paragraph newParagraph = new Paragraph(srcParagraph.getId(), this, this, factory);

    Map<String, Object> config = new HashMap<>(srcParagraph.getConfig());
    Map<String, Object> param = new HashMap<>(srcParagraph.settings.getParams());
    Map<String, Input> form = new HashMap<>(srcParagraph.settings.getForms());

    newParagraph.setConfig(config);
    newParagraph.settings.setParams(param);
    newParagraph.settings.setForms(form);
    newParagraph.setText(srcParagraph.getText());
    newParagraph.setTitle(srcParagraph.getTitle());

    try {
      Gson gson = new Gson();
      String resultJson = gson.toJson(srcParagraph.getReturn());
      InterpreterResult result = gson.fromJson(resultJson, InterpreterResult.class);
      newParagraph.setReturn(result, null);
    } catch (Exception e) {
      // 'result' part of Note consists of exception, instead of actual interpreter results
      logger.warn("Paragraph " + srcParagraph.getId() + " has a result with exception. "
              + e.getMessage());
    }

    synchronized (paragraphs) {
      paragraphs.add(newParagraph);
    }
    if (noteEventListener != null) {
      noteEventListener.onParagraphCreate(newParagraph);
    }
  }

  /**
   * Insert paragraph in given index.
   *
   * @param index
   */
  public Paragraph insertParagraph(int index) {
    Paragraph p = new Paragraph(this, this, factory);
    addLastReplNameIfEmptyText(p);
    synchronized (paragraphs) {
      paragraphs.add(index, p);
    }
    if (noteEventListener != null) {
      noteEventListener.onParagraphCreate(p);
    }
    return p;
  }

  /**
   * Add Last Repl name If Paragraph has empty text
   *
   * @param p Paragraph
   */
  private void addLastReplNameIfEmptyText(Paragraph p) {
    String replName = lastReplName.get();
    if (StringUtils.isEmpty(p.getText()) && StringUtils.isNotEmpty(replName)) {
      p.setText(getInterpreterName(replName) + " ");
    }
  }

  private String getInterpreterName(String replName) {
    return StringUtils.isBlank(replName) ? StringUtils.EMPTY : "%" + replName;
  }

  /**
   * Remove paragraph by id.
   *
   * @param paragraphId
   * @return a paragraph that was deleted, or <code>null</code> otherwise
   */
  public Paragraph removeParagraph(String paragraphId) {
    removeAllAngularObjectInParagraph(paragraphId);
    ResourcePoolUtils.removeResourcesBelongsToParagraph(id(), paragraphId);
    synchronized (paragraphs) {
      Iterator<Paragraph> i = paragraphs.iterator();
      while (i.hasNext()) {
        Paragraph p = i.next();
        if (p.getId().equals(paragraphId)) {
          index.deleteIndexDoc(this, p);
          i.remove();

          if (noteEventListener != null) {
            noteEventListener.onParagraphRemove(p);
          }
          return p;
        }
      }
    }
    return null;
  }

  /**
   * Clear paragraph output by id.
   *
   * @param paragraphId
   * @return
   */
  public Paragraph clearParagraphOutput(String paragraphId) {
    synchronized (paragraphs) {
      for (int i = 0; i < paragraphs.size(); i++) {
        Paragraph p = paragraphs.get(i);
        if (p.getId().equals(paragraphId)) {
          p.setReturn(null, null);
          return p;
        }
      }
    }
    return null;
  }

  /**
   * Move paragraph into the new index (order from 0 ~ n-1).
   *
   * @param paragraphId
   * @param index new index
   */
  public void moveParagraph(String paragraphId, int index) {
    moveParagraph(paragraphId, index, false);
  }

  /**
   * Move paragraph into the new index (order from 0 ~ n-1).
   *
   * @param paragraphId
   * @param index new index
   * @param throwWhenIndexIsOutOfBound whether throw IndexOutOfBoundException
   *                                   when index is out of bound
   */
  public void moveParagraph(String paragraphId, int index, boolean throwWhenIndexIsOutOfBound) {
    synchronized (paragraphs) {
      int oldIndex;
      Paragraph p = null;

      if (index < 0 || index >= paragraphs.size()) {
        if (throwWhenIndexIsOutOfBound) {
          throw new IndexOutOfBoundsException("paragraph size is " + paragraphs.size() +
              " , index is " + index);
        } else {
          return;
        }
      }

      for (int i = 0; i < paragraphs.size(); i++) {
        if (paragraphs.get(i).getId().equals(paragraphId)) {
          oldIndex = i;
          if (oldIndex == index) {
            return;
          }
          p = paragraphs.remove(i);
        }
      }

      if (p != null) {
        paragraphs.add(index, p);
      }
    }
  }

  public boolean isLastParagraph(String paragraphId) {
    if (!paragraphs.isEmpty()) {
      synchronized (paragraphs) {
        if (paragraphId.equals(paragraphs.get(paragraphs.size() - 1).getId())) {
          return true;
        }
      }
      return false;
    }
    /** because empty list, cannot remove nothing right? */
    return true;
  }

  public Paragraph getParagraph(String paragraphId) {
    synchronized (paragraphs) {
      for (Paragraph p : paragraphs) {
        if (p.getId().equals(paragraphId)) {
          return p;
        }
      }
    }
    return null;
  }

  public Paragraph getLastParagraph() {
    synchronized (paragraphs) {
      return paragraphs.get(paragraphs.size() - 1);
    }
  }

  public List<Map<String, String>> generateParagraphsInfo (){
    List<Map<String, String>> paragraphsInfo = new LinkedList<>();
    synchronized (paragraphs) {
      for (Paragraph p : paragraphs) {
        Map<String, String> info = new HashMap<>();
        info.put("id", p.getId());
        info.put("status", p.getStatus().toString());
        if (p.getDateStarted() != null) {
          info.put("started", p.getDateStarted().toString());
        }
        if (p.getDateFinished() != null) {
          info.put("finished", p.getDateFinished().toString());
        }
        if (p.getStatus().isRunning()) {
          info.put("progress", String.valueOf(p.progress()));
        }
        paragraphsInfo.add(info);
      }
    }
    return paragraphsInfo;
  }

  /**
   * Run all paragraphs sequentially.
   */
  public void runAll() {
    String cronExecutingUser = (String) getConfig().get("cronExecutingUser");
    synchronized (paragraphs) {
      if (!paragraphs.isEmpty()) {
        setLastReplName(paragraphs.get(paragraphs.size() - 1));
      }
      for (Paragraph p : paragraphs) {
        if (!p.isEnabled()) {
          continue;
        }
        AuthenticationInfo authenticationInfo = new AuthenticationInfo();
        authenticationInfo.setUser(cronExecutingUser);
        p.setAuthenticationInfo(authenticationInfo);

        p.setInterpreterFactory(factory);
        p.setListener(jobListenerFactory.getParagraphJobListener(this));
        Interpreter intp = factory.getInterpreter(getId(), p.getRequiredReplName());

        intp.getScheduler().submit(p);
      }
    }
  }

  /**
   * Run a single paragraph.
   *
   * @param paragraphId
   */
  public void run(String paragraphId) {
    Paragraph p = getParagraph(paragraphId);
    p.setInterpreterFactory(factory);
    p.setListener(jobListenerFactory.getParagraphJobListener(this));
    String requiredReplName = p.getRequiredReplName();
    Interpreter intp = factory.getInterpreter(getId(), requiredReplName);

    if (intp == null) {
      // TODO(jongyoul): Make "%jdbc" configurable from JdbcInterpreter
      if (conf.getUseJdbcAlias() && null != (intp = factory.getInterpreter(getId(), "jdbc"))) {
        String pText = p.getText().replaceFirst(requiredReplName, "jdbc(" + requiredReplName + ")");
        logger.debug("New paragraph: {}", pText);
        p.setEffectiveText(pText);
      } else {
        throw new InterpreterException("Interpreter " + requiredReplName + " not found");
      }
    }
    if (p.getConfig().get("enabled") == null || (Boolean) p.getConfig().get("enabled")) {
      intp.getScheduler().submit(p);
    }
  }

  /**
   * Check whether all paragraphs belongs to this note has terminated
   * @return
   */
  public boolean isTerminated() {
    synchronized (paragraphs) {
      for (Paragraph p : paragraphs) {
        if (!p.isTerminated()) {
          return false;
        }
      }
    }

    return true;
  }

  public List<InterpreterCompletion> completion(String paragraphId, String buffer, int cursor) {
    Paragraph p = getParagraph(paragraphId);
    p.setInterpreterFactory(factory);
    p.setListener(jobListenerFactory.getParagraphJobListener(this));
    List completion = p.completion(buffer, cursor);

    return completion;
  }

  public List<Paragraph> getParagraphs() {
    synchronized (paragraphs) {
      return new LinkedList<Paragraph>(paragraphs);
    }
  }

  private void snapshotAngularObjectRegistry() {
    angularObjects = new HashMap<>();

    List<InterpreterSetting> settings = factory.getInterpreterSettings(getId());
    if (settings == null || settings.size() == 0) {
      return;
    }

    for (InterpreterSetting setting : settings) {
      InterpreterGroup intpGroup = setting.getInterpreterGroup(id);
      AngularObjectRegistry registry = intpGroup.getAngularObjectRegistry();
      angularObjects.put(intpGroup.getId(), registry.getAllWithGlobal(id));
    }
  }

  private void removeAllAngularObjectInParagraph(String paragraphId) {
    angularObjects = new HashMap<String, List<AngularObject>>();

    List<InterpreterSetting> settings = factory.getInterpreterSettings(getId());
    if (settings == null || settings.size() == 0) {
      return;
    }

    for (InterpreterSetting setting : settings) {
      InterpreterGroup intpGroup = setting.getInterpreterGroup(id);
      AngularObjectRegistry registry = intpGroup.getAngularObjectRegistry();

      if (registry instanceof RemoteAngularObjectRegistry) {
        // remove paragraph scope object
        ((RemoteAngularObjectRegistry) registry).removeAllAndNotifyRemoteProcess(id, paragraphId);

        // remove app scope object
        List<ApplicationState> appStates = getParagraph(paragraphId).getAllApplicationStates();
        if (appStates != null) {
          for (ApplicationState app : appStates) {
            ((RemoteAngularObjectRegistry) registry).removeAllAndNotifyRemoteProcess(
                id, app.getId());
          }
        }
      } else {
        registry.removeAll(id, paragraphId);

        // remove app scope object
        List<ApplicationState> appStates = getParagraph(paragraphId).getAllApplicationStates();
        if (appStates != null) {
          for (ApplicationState app : appStates) {
            registry.removeAll(id, app.getId());
          }
        }
      }
    }
  }

  public void persist(AuthenticationInfo subject) throws IOException {
    stopDelayedPersistTimer();
    snapshotAngularObjectRegistry();
    index.updateIndexDoc(this);
    repo.save(this, subject);
  }

  private void setLastReplName(Paragraph lastParagraphStarted) {
    if (StringUtils.isNotEmpty(lastParagraphStarted.getRequiredReplName())) {
      lastReplName.set(lastParagraphStarted.getRequiredReplName());
    }
  }

  public void setLastReplName(String paragraphId) {
    setLastReplName(getParagraph(paragraphId));
  }

  /**
   * Persist this note with maximum delay.
   * @param maxDelaySec
   */
  public void persist(int maxDelaySec, AuthenticationInfo subject) {
    startDelayedPersistTimer(maxDelaySec, subject);
  }

  public void unpersist(AuthenticationInfo subject) throws IOException {
    repo.remove(id(), subject);
  }


  private void startDelayedPersistTimer(int maxDelaySec, final AuthenticationInfo subject) {
    synchronized (this) {
      if (delayedPersist != null) {
        return;
      }

      delayedPersist = delayedPersistThreadPool.schedule(new Runnable() {

        @Override
        public void run() {
          try {
            persist(subject);
          } catch (IOException e) {
            logger.error(e.getMessage(), e);
          }
        }
      }, maxDelaySec, TimeUnit.SECONDS);
    }
  }

  private void stopDelayedPersistTimer() {
    synchronized (this) {
      if (delayedPersist == null) {
        return;
      }

      delayedPersist.cancel(false);
    }
  }

  public Map<String, Object> getConfig() {
    if (config == null) {
      config = new HashMap<>();
    }
    return config;
  }

  public void setConfig(Map<String, Object> config) {
    this.config = config;
  }

  public Map<String, Object> getInfo() {
    if (info == null) {
      info = new HashMap<>();
    }
    return info;
  }

  public void setInfo(Map<String, Object> info) {
    this.info = info;
  }

  public String getLastReplName() {
    return lastReplName.get();
  }

  public String getLastInterpreterName() {
    return getInterpreterName(getLastReplName());
  }

  @Override
  public void beforeStatusChange(Job job, Status before, Status after) {
    if (jobListenerFactory != null) {
      ParagraphJobListener listener = jobListenerFactory.getParagraphJobListener(this);
      if (listener != null) {
        listener.beforeStatusChange(job, before, after);
      }
    }
  }

  @Override
  public void afterStatusChange(Job job, Status before, Status after) {
    if (jobListenerFactory != null) {
      ParagraphJobListener listener = jobListenerFactory.getParagraphJobListener(this);
      if (listener != null) {
        listener.afterStatusChange(job, before, after);
      }
    }

    if (noteEventListener != null) {
      noteEventListener.onParagraphStatusChange((Paragraph) job, after);
    }
  }

  @Override
  public void onProgressUpdate(Job job, int progress) {
    if (jobListenerFactory != null) {
      ParagraphJobListener listener = jobListenerFactory.getParagraphJobListener(this);
      if (listener != null) {
        listener.onProgressUpdate(job, progress);
      }
    }
  }


  @Override
  public void onOutputAppend(Paragraph paragraph, InterpreterOutput out, String output) {
    if (jobListenerFactory != null) {
      ParagraphJobListener listener = jobListenerFactory.getParagraphJobListener(this);
      if (listener != null) {
        listener.onOutputAppend(paragraph, out, output);
      }
    }
  }

  @Override
  public void onOutputUpdate(Paragraph paragraph, InterpreterOutput out, String output) {
    if (jobListenerFactory != null) {
      ParagraphJobListener listener = jobListenerFactory.getParagraphJobListener(this);
      if (listener != null) {
        listener.onOutputUpdate(paragraph, out, output);
      }
    }
  }



  public NoteEventListener getNoteEventListener() {
    return noteEventListener;
  }

  public void setNoteEventListener(NoteEventListener noteEventListener) {
    this.noteEventListener = noteEventListener;
  }

}
