package org.apache.zeppelin.test;

import io.fabric8.kubernetes.api.model.batch.Job;
import java.io.File;
import java.io.IOException;
import org.apache.zeppelin.background.BackgroundTaskLifecycleListener;
import org.apache.zeppelin.background.FileSystemTaskContextStorage;
import org.apache.zeppelin.background.K8sNoteBackgroundTaskManager;
import org.apache.zeppelin.background.NoteBackgroundTask;
import org.apache.zeppelin.background.TaskContext;
import org.apache.zeppelin.background.TaskContextStorage;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.interpreter.launcher.BackgroundTaskLifecycleWatcherImpl;

public class K8sNoteTestTaskManager extends K8sNoteBackgroundTaskManager {
  private final BackgroundTaskLifecycleWatcherImpl<Job> watcher;

  public K8sNoteTestTaskManager(ZeppelinConfiguration zConf) throws IOException {
    super(zConf);

    watcher = new BackgroundTaskLifecycleWatcherImpl<Job>(getListener()) {
      @Override
      protected String getTaskId(Job job) {
        return job.getMetadata().getName().replaceFirst("test-", "");
      }
    };

    getKubectl().watchJobs(watcher, "taskType",  "test");
  }

  @Override
  protected TaskContextStorage createTaskContextStorage() {
    return new FileSystemTaskContextStorage(getConf().getK8sTestContextDir());
  }

  @Override
  protected NoteBackgroundTask createOrGetBackgroundTask(TaskContext taskContext) {
    File servingTemplateDir = new File(getConf().getK8sTemplatesDir(), "background");
    K8sNoteTestTask testTask = new K8sNoteTestTask(
            getKubectl(),
            taskContext,
            String.format("%s/%s/notebook",
                    new File(getConf().getK8sTestContextDir()).getAbsolutePath(),
                    taskContext.getId()),
            servingTemplateDir);
    return testTask;
  }


  public void setListener(BackgroundTaskLifecycleListener listener) {
    super.setListener(listener);
    watcher.setListener(listener);
  }
}
