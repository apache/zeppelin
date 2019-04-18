package org.apache.zeppelin.service;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.plugin.PluginManager;
import org.apache.zeppelin.serving.NoteServingTask;
import org.apache.zeppelin.serving.NoteServingTaskManager;
import org.apache.zeppelin.serving.TaskContext;

import javax.inject.Inject;

public class NoteServingTaskManagerService {

  private final ZeppelinConfiguration zConf;
  private final NotebookService notebookService;
  private final NoteServingTaskManager servingTaskManager;

  @Inject
  public NoteServingTaskManagerService(ZeppelinConfiguration zConf,
                                       NotebookService notebookService) throws IOException {
    this.zConf = zConf;
    this.notebookService = notebookService;

    PluginManager pluginManager = PluginManager.get();
    if (zConf.getRunMode() == ZeppelinConfiguration.RUN_MODE.K8S) {
      /**
       * For now, class name is hardcoded here.
       * Later, we can make it configurable if necessary.
       */
      servingTaskManager = pluginManager.loadNoteServingTaskManager(
              "K8sStandardInterpreterLauncher",
              "org.apache.zeppelin.serving.K8sNoteServingTaskManager"
              );
    } else {
      servingTaskManager = null;
      throw new IOException("No NoteServingTaskManager found for run mode " + zConf.getRunMode());
    }
  }

  public NoteServingTask startServing(String noteId, String revId, ServiceContext serviceContext) throws Exception {
    final AtomicReference<Note> noteRef = new AtomicReference<>();
    final AtomicReference<Exception> exRef = new AtomicReference<>();

    notebookService.getNotebyRevision(noteId, revId, serviceContext, new ServiceCallback<Note>() {
      @Override
      public void onStart(String message, ServiceContext context) throws IOException {

      }

      @Override
      public void onSuccess(Note result, ServiceContext context) throws IOException {
        noteRef.set(result);
        synchronized (noteRef) {
          noteRef.notify();
        }
      }

      @Override
      public void onFailure(Exception ex, ServiceContext context) throws IOException {
        exRef.set(ex);
        synchronized (noteRef) {
          noteRef.notify();
        }
      }
    });

    synchronized (noteRef) {
      while (noteRef.get() == null && exRef.get() == null) {
        noteRef.wait(100);
      }

      if (exRef.get() != null) {
        throw exRef.get();
      }

      Note note = noteRef.get();
      NoteServingTask servingTask = servingTaskManager.start(note, revId);
      return servingTask;
    }
  }

  public NoteServingTask stopServing(String noteId, String revId, ServiceContext serviceContext) throws Exception {
    // TODO check permission
    return servingTaskManager.stop(noteId, revId);
  }

  public NoteServingTask getServing(String noteId, String revId, ServiceContext serviceContext) throws IOException {
    return servingTaskManager.get(noteId, revId);
  }

  public List<NoteServingTask> getAllServing() {
    return servingTaskManager.list();
  }
}
