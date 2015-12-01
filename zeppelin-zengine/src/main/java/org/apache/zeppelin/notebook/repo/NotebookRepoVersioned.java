package org.apache.zeppelin.notebook.repo;

import java.io.IOException;
import java.util.List;

import org.apache.zeppelin.notebook.Note;

/**
 * Notebook repository w/ versions
 */
public interface NotebookRepoVersioned extends NotebookRepo {

  /**
   * Get particular revision of the Notebooks
   *
   * @param noteId Id of the Notebook
   * @param rev revision of the Notebook
   * @return a Notebook
   * @throws IOException
   */
  public abstract Note get(String noteId, String rev) throws IOException;

  /**
   * List of revisions of the given Notebook
   *
   * @param noteId id of the Notebook
   * @return list of revisions
   */
  public abstract List<Rev> history(String noteId);

  /**
   * Represents the 'Revision' a point in life of the notebook
   */
  static class Rev {
    public Rev(String name, int time) {
      this.name = name;
      this.time = time;
    }
    String name;
    int time;
  }

}
