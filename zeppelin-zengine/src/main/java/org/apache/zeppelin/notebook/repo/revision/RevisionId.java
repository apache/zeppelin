package org.apache.zeppelin.notebook.repo.revision;

/**
 * Represents Revision id element. This is generic class and can be fed any 
 * revision id class implemented on developers side. Also all the compare and equals methods 
 * can be provided by the generic class.
 * @param <T> tag for generic id class.
 */

public class RevisionId <T> {
  private T id;
  
  public RevisionId(T id) {
    this.setId(id);
  }

  public T getId() {
    return id;
  }

  public void setId(T id) {
    this.id = id;
  }

}
