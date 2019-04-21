package org.apache.zeppelin.test;

import java.io.File;
import java.io.IOException;
import org.apache.zeppelin.background.FileSystemTaskContextStorage;
import org.apache.zeppelin.background.K8sNoteBackgroundTaskManager;
import org.apache.zeppelin.background.NoteBackgroundTask;
import org.apache.zeppelin.background.TaskContext;
import org.apache.zeppelin.background.TaskContextStorage;
import org.apache.zeppelin.conf.ZeppelinConfiguration;

public class K8sNoteTestTaskManager extends K8sNoteBackgroundTaskManager {
  public K8sNoteTestTaskManager(ZeppelinConfiguration zConf) throws IOException {
    super(zConf);
  }

  @Override
  protected TaskContextStorage createTaskContextStorage() {
    return new FileSystemTaskContextStorage(getConf().getK8sTestContextDir());
  }

  @Override
  protected NoteBackgroundTask createOrGetBackgroundTask(TaskContext taskContext) {
    File servingTemplateDir = new File(getConf().getK8sTemplatesDir(), "background");
    K8sNoteTestTask testTask = new K8sNoteTestTask(getKubectl(), taskContext, servingTemplateDir);
    return testTask;
  }
}
