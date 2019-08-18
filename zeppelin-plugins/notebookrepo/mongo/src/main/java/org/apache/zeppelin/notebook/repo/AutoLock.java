package org.apache.zeppelin.notebook.repo;


import java.io.Closeable;

public interface AutoLock extends Closeable {
  void close();
}
