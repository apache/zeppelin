package org.apache.zeppelin.resource;

import org.slf4j.Logger;
import org.apache.zeppelin.conf.ZeppelinConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Properties;

import javax.management.RuntimeErrorException;

import org.slf4j.LoggerFactory;

import com.google.gson.Gson;

import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSelectInfo;
import org.apache.commons.vfs2.FileSelector;
import org.apache.commons.vfs2.FileSystemException;
import org.apache.commons.vfs2.FileSystemManager;
import org.apache.commons.vfs2.FileType;
import org.apache.commons.vfs2.NameScope;
import org.apache.commons.vfs2.VFS;

/**
 * Resource pool that saves resources to the local file system.
 *
 */
public class VFSResourcePool extends DistributedResourcePool {
  Logger logger = LoggerFactory.getLogger(VFSResourcePool.class);
  
 
  @Override
  public void put(String name, Object object) {
    try {
      FileObject rootDir = getRootDir();
      FileObject resultDir = rootDir.resolveFile(name, NameScope.CHILD);
      if (!resultDir.exists()) {
        resultDir.createFolder();
      }
     
      if (!isDirectory(resultDir)) {
        throw new IOException(resultDir.getName().toString() + " is not a directory");
      }
      Gson gson = new Gson();

      FileObject resultFile = resultDir.resolveFile("result.dat", NameScope.CHILD);
      // false means not appending. creates file if not exists
      OutputStream out = resultFile.getContent().getOutputStream(false);
      ObjectOutputStream oos = new ObjectOutputStream(out);
      oos.writeObject(object);
      out.close();
    } catch (IOException e) {
      super.put(name, object);
      throw new RuntimeException(e);
    }
  }

  @Override
  public Resource get(String name) {
    return get(name, true);
  }

  @Override
  public Resource get(String noteId, String paragraphId, String name) {
    Resource r = get(noteId, paragraphId, name, true);
    return new Resource(
        new ResourceId(this.id(), noteId, paragraphId, name), r.get()); 
  }

  @Override
  public Resource get(String name, boolean remote) {
    try {
      FileObject rootDir = getRootDir();
      FileObject resultDir = rootDir.resolveFile(name, NameScope.CHILD);
      if (!resultDir.exists() || !isDirectory(resultDir))
        return null;
      FileObject resultFile = resultDir.resolveFile("result.dat", NameScope.CHILD);

      InputStream instream = resultFile.getContent().getInputStream();
      ObjectInputStream ois = new ObjectInputStream(instream);

      try {
        Object o = ois.readObject();
        return new Resource(new ResourceId(this.id(), name), o);
      } catch (ClassNotFoundException e) {
        throw new RuntimeException(e);
      }

    } catch (IOException e) {
    }
    return super.get(name, remote);
  }

  @Override
  public Resource get(String noteId, String paragraphId, String name, boolean remote) {
    return get(noteId + "___" + paragraphId + "___" + name, remote);
  }

  @Override
  public ResourceSet getAll() {
    return super.getAll(true);
  }

  @Override
  public ResourceSet getAll(boolean remote) {
    return super.getAll(remote);
  }

  @Override
  public void put(String noteId, String paragraphId, String name, Object object) {
    put(noteId + "___" + paragraphId + "___name", object);
  }

  @Override
  public Resource remove(String name) {
    try {
      Resource r = get(name);
      FileObject rootDir = getRootDir();
      FileObject resultDir = rootDir.resolveFile(name, NameScope.CHILD);
      if (resultDir.exists()) {
        resultDir.delete(new FileSelector() {
          @Override
          public boolean traverseDescendents(FileSelectInfo fileInfo) throws Exception {
            return true;
          }
          
          @Override
          public boolean includeFile(FileSelectInfo fileInfo) throws Exception {
            return true;
          }
        });
      }
      return r;
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public Resource remove(String noteId, String paragraphId, String name) {
    return remove(noteId + "___" + paragraphId + "___" + name);
  }

  private FileSystemManager fsManager;
  private URI filesystemRoot;
  public VFSResourcePool(String id, ResourcePoolConnector connector, Properties property) {
    super(id, connector, property);
    try {
      this.filesystemRoot = new 
          URI(property.getProperty("Resource_Path", "notebook/zeppelin_resources"));
    } catch (URISyntaxException e1) {
      throw new RuntimeException(e1);
    }
    
    if (filesystemRoot.getScheme() == null) { // it is local path
      try {
        this.filesystemRoot = new URI(new File( filesystemRoot.getPath()).getAbsolutePath());
      } catch (URISyntaxException e) {
        throw new RuntimeException(e);
      }
    } else {
      this.filesystemRoot = filesystemRoot;
    }

    try {
      fsManager = VFS.getManager();
      FileObject file;
      file = fsManager.resolveFile(filesystemRoot.getPath());
      if (!file.exists()) {
        logger.info("Notebook dir doesn't exist, create.");
        file.createFolder();
      }
    } catch (FileSystemException e) {
      throw new RuntimeException("Unable to load new file system.");
    }
  }
  
  private FileObject getRootDir() throws IOException {
    FileObject rootDir = fsManager.resolveFile(getPath("/"));
    // Does nothing if the folder already exists.
    rootDir.createFolder();


    if (!isDirectory(rootDir)) {
      throw new IOException("Root path is not a directory");
    }

    return rootDir;
  }
  
  private boolean isDirectory(FileObject fo) throws IOException {
    if (fo == null) return false;
    if (fo.getType() == FileType.FOLDER) {
      return true;
    } else {
      return false;
    }
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
}
