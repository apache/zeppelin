package org.apache.zeppelin.interpreter;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;

import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Map;

import javax.management.ReflectionException;

import java.util.HashMap;
import java.util.Collections;
/**
 * Keeps track of the different varieties of result repositories.
 * @author Rusty Phillips {@literal: <rusty@cloudspace.com>}
 *
 */
public class ResultRepoFactory {
  Logger logger = LoggerFactory.getLogger(ResultRepoFactory.class);
  private ZeppelinConfiguration conf;
    
  private Map<String, RemoteResultRepo> repos = Collections
      .synchronizedMap(new HashMap<String, RemoteResultRepo>());
  
  public ResultRepoFactory(ZeppelinConfiguration conf)
      throws IOException, ReflectiveOperationException {
    this.conf = conf;
    for (String className: RemoteResultRepo.registeredRepos.values()) {
      repos.put(className, (RemoteResultRepo)
        Class.forName(className).getConstructor(
          new Class[] {ZeppelinConfiguration.class }).newInstance(conf));
    }
  }
  
  public RemoteResultRepo getDefaultRepo() throws IOException
  {
    return getRepoByClassName("org.apache.zeppelin.interpreter.FilesystemResultRepo");
  }
  
  public RemoteResultRepo getRepoByClassName(String className)
      throws IOException {
    if (repos.containsKey(className))
      return repos.get(className);
    try {
      ClassLoader cl = ClassLoader.getSystemClassLoader();
      cl.loadClass(className);
      return repos.put(className, (RemoteResultRepo)
        Class.forName(className).getConstructor(
          new Class[] {ZeppelinConfiguration.class }).newInstance(conf) );

    } catch (ClassNotFoundException | NoSuchMethodException | SecurityException
        | InstantiationException | IllegalAccessException
        | IllegalArgumentException | InvocationTargetException e) {
      e.printStackTrace();
      throw new IOException(e);
    }
  }


}
