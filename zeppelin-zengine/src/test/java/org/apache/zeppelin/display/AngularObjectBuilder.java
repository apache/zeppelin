package org.apache.zeppelin.display;

public class AngularObjectBuilder {

  public static <T> AngularObject<T> build(String varName, T value, String noteId,
    String paragraphId) {

    return new AngularObject<>(varName, value, noteId, paragraphId, null);
  }
}
