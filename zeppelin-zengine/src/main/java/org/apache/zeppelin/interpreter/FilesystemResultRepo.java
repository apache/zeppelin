package org.apache.zeppelin.interpreter;

import java.sql.ResultSet;
import org.apache.commons.vfs2.FileContent;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSystemManager;
import org.apache.commons.vfs2.FileType;
import org.apache.commons.vfs2.NameScope;
import org.apache.commons.vfs2.Selectors;
import org.apache.commons.vfs2.VFS;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.conf.ZeppelinConfiguration.ConfVars;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;

import javax.sql.rowset.CachedRowSet;

import java.util.UUID;

/**
 * Store the results back into the filesystem.
 * @author Rusty Phillips {@literal: <rusty@cloudspace.com>}
 *
 */
public class FilesystemResultRepo extends RemoteResultRepo {
  static {
    RemoteResultRepo.register("File System", FilesystemResultRepo.class.getName());
  }
  private FileSystemManager fsManager;
  private URI filesystemRoot;

  private Properties properties;
  private ZeppelinConfiguration conf;
  
  public FilesystemResultRepo(ZeppelinConfiguration conf) throws IOException {
    this.conf = conf;

    try {
      filesystemRoot = new URI(conf.getNotebookDir() + "/_results");
    } catch (URISyntaxException e1) {
      throw new IOException(e1);
    }

    if (filesystemRoot.getScheme() == null) { // it is local path
      try {
        this.filesystemRoot = new URI(new File(
            conf.getRelativeDir(filesystemRoot.getPath())).getAbsolutePath());
      } catch (URISyntaxException e) {
        throw new IOException(e);
      }
    } else {
      this.filesystemRoot = filesystemRoot;
    }
    fsManager = VFS.getManager();
  }
  
  private String getPath(String path) {
    if (path == null || path.trim().length() == 0) {
      return filesystemRoot.toString();
    }
    if (path.startsWith("/")) {
      return filesystemRoot.toString() + path;
    } else {
      return filesystemRoot.toString() + "/" + path;
    }
  }
    
  private boolean isDirectory(FileObject fo) throws IOException {
    if (fo == null) return false;
    if (fo.getType() == FileType.FOLDER) {
      return true;
    } else {
      return false;
    }
  }
  
  private FileObject getRootDir() throws IOException {
    FileObject rootDir = fsManager.resolveFile(getPath("/"));

    if (!rootDir.exists()) {
      throw new IOException("Root path does not exists");
    }

    if (!isDirectory(rootDir)) {
      throw new IOException("Root path is not a directory");
    }

    return rootDir;
  }


  
  @Override
  public String save(String result, String id) throws IOException {
    removeResult(id);
    
    FileObject rootDir = getRootDir();
    FileObject resultDir = rootDir.resolveFile(id, NameScope.CHILD);
    if (!resultDir.exists()) {
      resultDir.createFolder();
    }
    
    if (!isDirectory(resultDir)) {
      throw new IOException(resultDir.getName().toString() + " is not a directory");
    }

    FileObject resultJson = resultDir.resolveFile("result.txt", NameScope.CHILD);
    // false means not appending. creates file if not exists
    OutputStream out = resultJson.getContent().getOutputStream(false);
    out.write(result.getBytes(conf.getString(ConfVars.ZEPPELIN_ENCODING)));
    out.close();
    return id;
  }

  private String getResult(FileObject resultDir) throws IOException {
    FileObject resultJson = resultDir.resolveFile("result.txt", NameScope.CHILD);
    if (!resultJson.exists()) {
      throw new IOException(resultJson.getName().toString() + " not found");
    }

    FileContent content = resultJson.getContent();
    InputStream ins = content.getInputStream();
    String text = IOUtils.toString(ins, conf.getString(ConfVars.ZEPPELIN_ENCODING));
    ins.close();

    return text;
  }

  @Override
  public void removeResult(String id) throws IOException {
    FileObject rootDir = fsManager.resolveFile(getPath("/"));
    FileObject resultDir = rootDir.resolveFile(id, NameScope.CHILD);

    if (!resultDir.exists()) {
      // nothing to do
      return;
    }

    if (!isDirectory(resultDir)) {
      // it is not look like zeppelin note savings
      throw new IOException("Can not remove " + resultDir.getName().toString());
    }

    resultDir.delete(Selectors.SELECT_SELF_AND_CHILDREN);
  }

  @Override
  public String get(String id) throws IOException {
    FileObject rootDir = fsManager.resolveFile(getPath("/"));
    FileObject noteDir = rootDir.resolveFile(id, NameScope.CHILD);

    return getResult(noteDir);
  }

}
