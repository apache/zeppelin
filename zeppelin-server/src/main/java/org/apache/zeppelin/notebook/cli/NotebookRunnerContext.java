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
package org.apache.zeppelin.notebook.cli;

import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterFactory;
import org.apache.zeppelin.interpreter.InterpreterSetting;
import org.apache.zeppelin.interpreter.InterpreterSettingManager;
import org.apache.zeppelin.interpreter.ManagedInterpreterGroup;
import org.apache.zeppelin.notebook.AuthorizationService;
import org.apache.zeppelin.notebook.GsonNoteParser;
import org.apache.zeppelin.notebook.NoteManager;
import org.apache.zeppelin.notebook.NoteParser;
import org.apache.zeppelin.notebook.Notebook;
import org.apache.zeppelin.notebook.repo.NotebookRepo;
import org.apache.zeppelin.notebook.repo.VFSNotebookRepo;
import org.apache.zeppelin.plugin.PluginManager;
import org.apache.zeppelin.storage.ConfigStorage;
import org.apache.zeppelin.user.Credentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Assembles the minimal set of objects needed to run a note headlessly, without Jetty/HK2: a
 * production {@link VFSNotebookRepo}-backed {@link Notebook} wired to real (non-mock) listener
 * implementations. Follows a manual dependency-injection recipe similar to
 * {@code StopInterpreter}/{@code AbstractInterpreterTest}, except it does not separately call
 * {@code interpreterSettingManager.setNotebook(notebook)} — {@link Notebook}'s own constructor
 * already does that, and the field it populates is only consulted by UI editor-setting/restart
 * paths that a headless {@code runAll} never exercises.
 */
public final class NotebookRunnerContext implements Closeable {

  private static final Logger LOGGER = LoggerFactory.getLogger(NotebookRunnerContext.class);

  private final InterpreterSettingManager interpreterSettingManager;
  private final InterpreterFactory interpreterFactory;
  private final Notebook notebook;
  private final HeadlessProcessListener processListener;

  private NotebookRunnerContext(InterpreterSettingManager interpreterSettingManager,
      InterpreterFactory interpreterFactory, Notebook notebook,
      HeadlessProcessListener processListener) {
    this.interpreterSettingManager = interpreterSettingManager;
    this.interpreterFactory = interpreterFactory;
    this.notebook = notebook;
    this.processListener = processListener;
  }

  public static NotebookRunnerContext bootstrap(ZeppelinConfiguration zConf) throws IOException {
    ConfigStorage storage = ConfigStorage.createConfigStorage(zConf);
    PluginManager pluginManager = new PluginManager(zConf);
    NoteParser noteParser = new GsonNoteParser(zConf);

    NotebookRepo notebookRepo = new VFSNotebookRepo();
    notebookRepo.init(zConf, noteParser);
    NoteManager noteManager = new NoteManager(notebookRepo, zConf);

    HeadlessProcessListener processListener = new HeadlessProcessListener();
    HeadlessAngularObjectListener angularObjectListener = new HeadlessAngularObjectListener();
    HeadlessApplicationEventListener applicationEventListener =
        new HeadlessApplicationEventListener();

    InterpreterSettingManager interpreterSettingManager = new InterpreterSettingManager(zConf,
        angularObjectListener, processListener, applicationEventListener, storage, pluginManager);
    InterpreterFactory interpreterFactory = new InterpreterFactory(interpreterSettingManager);

    AuthorizationService authorizationService =
        new AuthorizationService(noteManager, zConf, storage);
    Credentials credentials = new Credentials(zConf, storage);

    Notebook notebook = new Notebook(zConf, authorizationService, notebookRepo, noteManager,
        interpreterFactory, interpreterSettingManager, credentials);
    notebook.addNotebookEventListener(new HeadlessNoteEventListener());
    processListener.setNotebook(notebook);

    return new NotebookRunnerContext(interpreterSettingManager, interpreterFactory, notebook,
        processListener);
  }

  public Notebook getNotebook() {
    return notebook;
  }

  public InterpreterSettingManager getInterpreterSettingManager() {
    return interpreterSettingManager;
  }

  public InterpreterFactory getInterpreterFactory() {
    return interpreterFactory;
  }

  HeadlessProcessListener getProcessListener() {
    return processListener;
  }

  @Override
  public void close() throws IOException {
    // Order matters:
    // 1. Drain our own runParagraphs executor first.
    // 2. Stop every RemoteScheduler's *own* job-submission thread pool
    //    (Executors.newFixedThreadPool/newSingleThreadExecutor named "FIFO-...", created
    //    directly in RemoteScheduler#createExecutor -- NOT registered in ExecutorFactory) while
    //    the interpreter groups are still live. InterpreterGroup#close() (invoked below by
    //    InterpreterSettingManager#close()) only calls Scheduler#stop() -- the no-arg
    //    overload -- which for RemoteScheduler just interrupts its run() loop and never touches
    //    that executor field; only the 2-arg Scheduler#stop(timeout, unit) does
    //    (RemoteScheduler#stop(int, TimeUnit) -> ExecutorUtil.softShutdown). This is a
    //    pre-existing gap in the shared close() path that a long-lived server never notices
    //    (it never shuts down), but leaves this "FIFO-RemoteInterpreter-*" thread alive forever
    //    in a one-shot headless CLI process, hanging the JVM on exit.
    // 3. InterpreterSettingManager#close() tears down the interpreter processes themselves
    //    (they unregister against the event server as they go).
    // 4. Stop the event server's own (non-daemon) Thrift server thread --
    //    InterpreterSettingManager#close() never does this itself (fine for a long-lived
    //    server, not for a one-shot CLI process).
    //
    // NOTE deliberately NOT done here: ExecutorFactory#shutdownAll(). That pool (notably
    // SchedulerFactory's backing executor, "SchedulerFactory-*") is a *JVM-wide* singleton
    // (ExecutorFactory.singleton()), not owned by this context. Calling shutdownAll() here would
    // tear it down for the entire process, including every other NotebookRunnerContext a test
    // suite (or any other caller sharing this JVM) creates afterwards -- confirmed by running
    // the full test suite: a later test's paragraph submission was rejected with
    // RejectedExecutionException because an earlier test's close() had already killed the
    // shared pool. NotebookRunner#main calls ExecutorFactory#shutdownAll() itself, once, right
    // before the process actually exits -- see its Javadoc for why that's the correct place.
    processListener.closeExecutor();
    stopRemoteInterpreterSchedulers();
    interpreterSettingManager.close();
    interpreterSettingManager.getInterpreterEventServer().stop();
  }

  private void stopRemoteInterpreterSchedulers() {
    for (InterpreterSetting setting : interpreterSettingManager.get()) {
      for (ManagedInterpreterGroup group : setting.getAllInterpreterGroups()) {
        for (List<Interpreter> session : group.values()) {
          for (Interpreter interpreter : session) {
            try {
              interpreter.getScheduler().stop(5, TimeUnit.SECONDS);
            } catch (Exception e) {
              LOGGER.warn("Failed to stop scheduler for interpreter {}",
                  interpreter.getClassName(), e);
            }
          }
        }
      }
    }
  }
}
