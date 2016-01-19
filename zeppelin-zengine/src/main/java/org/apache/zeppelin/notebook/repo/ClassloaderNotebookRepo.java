package org.apache.zeppelin.notebook.repo;

import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.NoteInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

/**
 * Created by ringo on 2016. 1. 4..
 */
public class ClassloaderNotebookRepo implements NotebookRepo{
  private static final Logger LOG = LoggerFactory.getLogger(ClassloaderNotebookRepo.class);
  private NotebookRepo repo;
  private ClassLoader cl;

  public ClassloaderNotebookRepo(NotebookRepo repo, ClassLoader cl) {
    this.repo = repo;
    this.cl = cl;
  }

  @Override
  public List<NoteInfo> list() throws IOException {

    ClassLoader oldcl = Thread.currentThread().getContextClassLoader();
    Thread.currentThread().setContextClassLoader(cl);
    try {
      return repo.list();
    } catch (IOException e) {
      throw e;
    } catch (Throwable e) {
      throw new IOException(e);
    } finally {
      cl = Thread.currentThread().getContextClassLoader();
      Thread.currentThread().setContextClassLoader(oldcl);
    }
  }

  @Override
  public Note get(String noteId) throws IOException {
    ClassLoader oldcl = Thread.currentThread().getContextClassLoader();
    Thread.currentThread().setContextClassLoader(cl);
    try {
      return repo.get(noteId);
    } catch (IOException e) {
      throw e;
    } catch (Throwable e) {
      throw new IOException(e);
    } finally {
      cl = Thread.currentThread().getContextClassLoader();
      Thread.currentThread().setContextClassLoader(oldcl);
    }
  }

  @Override
  public void save(Note note) throws IOException {
    ClassLoader oldcl = Thread.currentThread().getContextClassLoader();
    Thread.currentThread().setContextClassLoader(cl);
    try {
      repo.save(note);
    } catch (IOException e) {
      throw e;
    } catch (Throwable e) {
      throw new IOException(e);
    } finally {
      cl = Thread.currentThread().getContextClassLoader();
      Thread.currentThread().setContextClassLoader(oldcl);
    }
  }

  @Override
  public void remove(String noteId) throws IOException {
    ClassLoader oldcl = Thread.currentThread().getContextClassLoader();
    Thread.currentThread().setContextClassLoader(cl);
    try {
      repo.remove(noteId);
    } catch (IOException e) {
      throw e;
    } catch (Throwable e) {
      throw new IOException(e);
    } finally {
      cl = Thread.currentThread().getContextClassLoader();
      Thread.currentThread().setContextClassLoader(oldcl);
    }
  }

  @Override
  public void close() {
    ClassLoader oldcl = Thread.currentThread().getContextClassLoader();
    Thread.currentThread().setContextClassLoader(cl);
    try {
      repo.close();
    } catch (Throwable e) {
      LOG.error("Close ERROR : ", e);
    }

    cl = Thread.currentThread().getContextClassLoader();
    Thread.currentThread().setContextClassLoader(oldcl);
  }

}
