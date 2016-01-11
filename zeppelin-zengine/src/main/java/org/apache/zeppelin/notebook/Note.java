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
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.apache.zeppelin.display.AngularObject;
import org.apache.zeppelin.display.AngularObjectRegistry;
import org.apache.zeppelin.display.Input;
import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterGroup;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterSetting;
import org.apache.zeppelin.notebook.repo.NotebookRepo;
import org.apache.zeppelin.notebook.utility.IdHashes;
import org.apache.zeppelin.scheduler.Job;
import org.apache.zeppelin.scheduler.Job.Status;
import org.apache.zeppelin.scheduler.JobListener;
import org.apache.zeppelin.search.SearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;

/**
 * Binded interpreters for a note
 */
public class Note implements Serializable, JobListener {
  private static final long serialVersionUID = 7920699076577612429L;

  final List<Paragraph> paragraphs = new LinkedList<>();
  private String name = "";
  private String id;

  @SuppressWarnings("rawtypes")
  Map<String, List<AngularObject>> angularObjects = new HashMap<>();

  private transient NoteInterpreterLoader replLoader;
  private transient JobListenerFactory jobListenerFactory;
  private transient NotebookRepo repo;
  private transient SearchService index;

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

  public Note(NotebookRepo repo, NoteInterpreterLoader replLoader,
      JobListenerFactory jlFactory, SearchService noteIndex) {
    this.repo = repo;
    this.replLoader = replLoader;
    this.jobListenerFactory = jlFactory;
    this.index = noteIndex;
    generateId();
  }

  private void generateId() {
    id = IdHashes.encode(System.currentTimeMillis() + new Random().nextInt());
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

  public void setName(String name) {
    this.name = name;
  }

  public NoteInterpreterLoader getNoteReplLoader() {
    return replLoader;
  }

  public void setReplLoader(NoteInterpreterLoader replLoader) {
    this.replLoader = replLoader;
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

  @SuppressWarnings("rawtypes")
  public Map<String, List<AngularObject>> getAngularObjects() {
    return angularObjects;
  }

  /**
   * Add paragraph last.
   *
   * @param p
   */
  public Paragraph addParagraph() {
    Paragraph p = new Paragraph(this, this, replLoader);
    synchronized (paragraphs) {
      paragraphs.add(p);
    }
    return p;
  }

  /**
   * Clone paragraph and add it to note.
   *
   * @param srcParagraph
   */
  public void addCloneParagraph(Paragraph srcParagraph) {
    Paragraph newParagraph = new Paragraph(this, this, replLoader);

    Map<String, Object> config = new HashMap<>(srcParagraph.getConfig());
    Map<String, Object> param = new HashMap<>(srcParagraph.settings.getParams());
    Map<String, Input> form = new HashMap<>(srcParagraph.settings.getForms());
    Gson gson = new Gson();
    InterpreterResult result = gson.fromJson(
        gson.toJson(srcParagraph.getReturn()),
        InterpreterResult.class);

    newParagraph.setConfig(config);
    newParagraph.settings.setParams(param);
    newParagraph.settings.setForms(form);
    newParagraph.setText(srcParagraph.getText());
    newParagraph.setTitle(srcParagraph.getTitle());
    newParagraph.setReturn(result, null);

    synchronized (paragraphs) {
      paragraphs.add(newParagraph);
    }
  }

  /**
   * Insert paragraph in given index.
   *
   * @param index
   * @param p
   */
  public Paragraph insertParagraph(int index) {
    Paragraph p = new Paragraph(this, this, replLoader);
    synchronized (paragraphs) {
      paragraphs.add(index, p);
    }
    return p;
  }

  /**
   * Remove paragraph by id.
   *
   * @param paragraphId
   * @return a paragraph that was deleted, or <code>null</code> otherwise
   */
  public Paragraph removeParagraph(String paragraphId) {
    synchronized (paragraphs) {
      Iterator<Paragraph> i = paragraphs.iterator();
      while (i.hasNext()) {
        Paragraph p = i.next();
        if (p.getId().equals(paragraphId)) {
          index.deleteIndexDoc(this, p);
          i.remove();
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
   *
   * @param jobListener
   */
  public void runAll() {
    synchronized (paragraphs) {
      for (Paragraph p : paragraphs) {
        p.setNoteReplLoader(replLoader);
        p.setListener(jobListenerFactory.getParagraphJobListener(this));
        Interpreter intp = replLoader.get(p.getRequiredReplName());
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
    p.setNoteReplLoader(replLoader);
    p.setListener(jobListenerFactory.getParagraphJobListener(this));
    Interpreter intp = replLoader.get(p.getRequiredReplName());
    if (intp == null) {
      throw new InterpreterException("Interpreter " + p.getRequiredReplName() + " not found");
    }
    if (p.getConfig().get("enabled") == null || (Boolean) p.getConfig().get("enabled")) {
      intp.getScheduler().submit(p);
    }
  }

  public List<String> completion(String paragraphId, String buffer, int cursor) {
    Paragraph p = getParagraph(paragraphId);
    p.setNoteReplLoader(replLoader);
    p.setListener(jobListenerFactory.getParagraphJobListener(this));
    return p.completion(buffer, cursor);
  }

  public List<Paragraph> getParagraphs() {
    synchronized (paragraphs) {
      return new LinkedList<Paragraph>(paragraphs);
    }
  }

  private void snapshotAngularObjectRegistry() {
    angularObjects = new HashMap<>();

    List<InterpreterSetting> settings = replLoader.getInterpreterSettings();
    if (settings == null || settings.size() == 0) {
      return;
    }

    for (InterpreterSetting setting : settings) {
      InterpreterGroup intpGroup = setting.getInterpreterGroup();
      AngularObjectRegistry registry = intpGroup.getAngularObjectRegistry();
      angularObjects.put(intpGroup.getId(), registry.getAllWithGlobal(id));
    }
  }

  public void persist() throws IOException {
    snapshotAngularObjectRegistry();
    index.updateIndexDoc(this);
    repo.save(this);
  }

  public void unpersist() throws IOException {
    repo.remove(id());
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

  @Override
  public void beforeStatusChange(Job job, Status before, Status after) {
  }

  @Override
  public void afterStatusChange(Job job, Status before, Status after) {
  }

  @Override
  public void onProgressUpdate(Job job, int progress) {}

}
