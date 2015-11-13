package org.apache.zeppelin.interpreter;

import java.sql.ResultSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.util.Properties;

import javax.tools.FileObject;

import org.apache.zeppelin.interpreter.Interpreter.RegisteredInterpreter;
import org.apache.commons.vfs2.FileType;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.conf.ZeppelinConfiguration.ConfVars;


/**
 * Describes how to save/load results.
 * @author Rusty Phillips {@literal: <rusty@cloudspace.com>}
 *
 */
public class RemoteResultRepo {
  protected Properties property;
  
  // TODO(Add Class loading):  Make it so that result repositories load their classes.
  private URL[] classloaderUrls;
  
  private RemoteResultRepo childRepo;
  
  public RemoteResultRepo() {
    childRepo = null;
  }
  
  public RemoteResultRepo(String ClassName, Properties props) throws ReflectiveOperationException {
    if (!registeredRepos.containsValue(ClassName)) {
      ClassName = registeredRepos.values().iterator().next();
    }      
    childRepo = (RemoteResultRepo) Class.forName(ClassName)
      .getConstructor(new Class[] { Properties.class }).newInstance(props);
      
  }

  public URL[] getClassloaderUrls() {
    return classloaderUrls;
  }
  
  public void setClassloaderUrls(URL[] classloaderUrls) {
    this.classloaderUrls = classloaderUrls;
  }

  // Returns the thing that finds the result.
  public String save(String result, String id) throws IOException
  { return childRepo.save(result, id); }
  public String get(String id) throws IOException { return childRepo.get(id); }
  public void removeResult(String id) throws IOException { childRepo.removeResult(id); }
   
  public static Map<String, String> registeredRepos =
    Collections.synchronizedMap(new HashMap<String, String>());
  
  public static void register(String name, String repoClass) {
    registeredRepos.put(name, repoClass);
  }
}

