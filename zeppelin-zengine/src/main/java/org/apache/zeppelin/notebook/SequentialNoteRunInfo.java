package org.apache.zeppelin.notebook;

/**
 * Info about sequential run of all paragraphs
 */
public class SequentialNoteRunInfo {

  private boolean runningSequentially = false;
  private transient Object synchronizer = new Object();

  public boolean isRunningSequentially() {
    return runningSequentially;
  }

  public void setRunningSequentially(boolean runningSequentially) {
    this.runningSequentially = runningSequentially;
  }

  public Object getSynchronizer() {
    return synchronizer;
  }
}
