package com.nflabs.zeppelin.interpreter;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.commons.lang.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nflabs.zeppelin.conf.ZeppelinConfiguration;
import com.nflabs.zeppelin.conf.ZeppelinConfiguration.ConfVars;
import com.nflabs.zeppelin.interpreter.Interpreter.RegisteredInterpreter;

/**
 * Manage interpreters.
 *
 */
public class InterpreterFactory {
  Logger logger = LoggerFactory.getLogger(InterpreterFactory.class);

  private Map<String, Object> share = Collections.synchronizedMap(new HashMap<String, Object>());
  private Map<String, URLClassLoader> cleanCl = Collections
      .synchronizedMap(new HashMap<String, URLClassLoader>());

  private ZeppelinConfiguration conf;
  String[] interpreterClassList;

  private Map<String, InterpreterSetting> interpreterSettings = 
      new HashMap<String, InterpreterSetting>();
  
  public InterpreterFactory(ZeppelinConfiguration conf) {
    this.conf = conf;
    String replsConf = conf.getString(ConfVars.ZEPPELIN_INTERPRETERS);
    interpreterClassList = replsConf.split(",");

    init();
  }


  private void init() {
    ClassLoader oldcl = Thread.currentThread().getContextClassLoader();

    // Load classes
    File[] interpreterDirs = new File(conf.getInterpreterDir()).listFiles();
    if (interpreterDirs != null) {
      for (File path : interpreterDirs) {
        logger.info("Reading " + path.getAbsolutePath());
        URL[] urls = null;
        try {
          urls = recursiveBuildLibList(path);
        } catch (MalformedURLException e1) {
          logger.error("Can't load jars ", e1);
        }
        URLClassLoader ccl = new URLClassLoader(urls, oldcl);

        for (String className : interpreterClassList) {
          try {
            Class.forName(className, true, ccl);
            Set<String> keys = Interpreter.registeredInterpreters.keySet();
            for (String intName : keys) {
              if (className.equals(
                  Interpreter.registeredInterpreters.get(intName).getClassName())) {
                logger.info("Interpreter " + intName + " found. class=" + className);
                cleanCl.put(intName, ccl);
              }
            }
          } catch (ClassNotFoundException e) {
            // nothing to do
          }
        }
      }
    }

    loadFromFile();

    // if no interpreter settings are loaded, create default set
    synchronized (interpreterSettings) {
      if (interpreterSettings.size() == 0) {
        HashMap<String, List<RegisteredInterpreter>> groupClassNameMap =
            new HashMap<String, List<RegisteredInterpreter>>();        
        
        for (String k : Interpreter.registeredInterpreters.keySet()) {
          RegisteredInterpreter info = Interpreter.registeredInterpreters.get(k);
          
          if (!groupClassNameMap.containsKey(info.getGroup())) {
            groupClassNameMap.put(info.getGroup(), new LinkedList<RegisteredInterpreter>());
          }
          
          groupClassNameMap.get(info.getGroup()).add(info);
        }
        
        for (String className : interpreterClassList) {
          for (String groupName : groupClassNameMap.keySet()) {
            List<RegisteredInterpreter> infos = groupClassNameMap.get(groupName);
            
            boolean found = false;
            for (RegisteredInterpreter info : infos) {
              if (info.getClassName().equals(className)) {
                found = true;
                break;
              }
            }

            if (found) {
              // add all interpreters in group
              Properties p = new Properties();
              add(groupName, groupName, p);             
              
              groupClassNameMap.remove(groupName);
              break;
            }
          }         
        }
      }
    }
  }
  
  private void loadFromFile() {
   // TODO(moon): Implement
  }
  
  private void saveToFile() {
    // TODO(moon): Implement
  }

  private RegisteredInterpreter getRegisteredReplInfoFromClassName(String clsName) {
    Set<String> keys = Interpreter.registeredInterpreters.keySet();
    for (String intName : keys) {
      RegisteredInterpreter info = Interpreter.registeredInterpreters.get(intName);
      if (clsName.equals(info.getClassName())) {
        return info;
      }
    }
    return null;
  }

  /**
   * @param name user defined name
   * @param groupName interpreter group name to instantiate
   * @param properties
   * @return
   * @throws InterpreterException
   */
  public InterpreterGroup add(String name, String groupName, Properties properties)
      throws InterpreterException {
    synchronized (interpreterSettings) {      
      InterpreterGroup interpreterGroup = createInterpreterGroup(groupName, properties);

      InterpreterSetting intpSetting = new InterpreterSetting(
          name,
          groupName,
          interpreterGroup);
      interpreterSettings.put(intpSetting.id(), intpSetting);

      saveToFile();
      return interpreterGroup;
    }
  }
  
  private InterpreterGroup createInterpreterGroup(String groupName, Properties properties)
      throws InterpreterException {
    synchronized (interpreterSettings) {      
      InterpreterGroup interpreterGroup = new InterpreterGroup();
      
      Set<String> keys = Interpreter.registeredInterpreters.keySet();
      for (String intName : keys) {
        RegisteredInterpreter info = Interpreter.registeredInterpreters.get(intName);
        if (info.getGroup().equals(groupName)) {
          Interpreter intp = createRepl(info.getName(), info.getClassName(), properties);
          interpreterGroup.add(intp);
        }
      }      
      return interpreterGroup;
    }
  }    
  
  public void remove(String id) {
    synchronized (interpreterSettings) {
      if (interpreterSettings.containsKey(id)) {
        InterpreterSetting intp = interpreterSettings.remove(id);
        intp.getInterpreterGroup().close();
        intp.getInterpreterGroup().destroy();
        saveToFile();
      }
    }
  }

  /**
   * Get loaded interpreters
   * @return
   */
  public Map<String, InterpreterSetting> get() {
    return interpreterSettings;
  }
  
  public InterpreterSetting get(String name) {
    synchronized (interpreterSettings) {
      return interpreterSettings.get(name);
    }
  }
  
  /**
   * Get default interpreter possible list order by zeppelin.intepreters property
   * @return
   */
  public List<String> getDefaultInterpreterList() {
    synchronized (interpreterSettings) {
      List<String> defaultList = new LinkedList<String>();
      
      for (String cls : interpreterClassList) {
        for (String intpId : interpreterSettings.keySet()) {
          InterpreterSetting intpSetting = interpreterSettings.get(intpId);
          InterpreterGroup intpGroup = intpSetting.getInterpreterGroup();
          for (Interpreter intp : intpGroup) {
            if (intp.getClassName().equals(cls)) {
              defaultList.add(intpId);
              break;
            }            
          }
        }
      }
      return defaultList;
    }
  }
  
  /**
   * Change interpreter property and restart
   * @param name
   * @param properties
   */
  public void setProperty(String id, Properties properties) {
    synchronized (interpreterSettings) {
      InterpreterSetting intpsetting = interpreterSettings.get(id);
      if (intpsetting != null) {
        intpsetting.getInterpreterGroup().close();
        intpsetting.getInterpreterGroup().destroy();

        InterpreterGroup interpreterGroup = createInterpreterGroup(
            intpsetting.getGroup(), properties);
        intpsetting.setInterpreterGroup(interpreterGroup);
      } else {
        throw new InterpreterException("Interpreter setting id " + id
            + " not found");
      }
    }
  }
  
  public void restart(String id) {
    synchronized (interpreterSettings) {
      synchronized (interpreterSettings) {
        InterpreterSetting intpsetting = interpreterSettings.get(id);
        if (intpsetting != null) {
          intpsetting.getInterpreterGroup().close();
          intpsetting.getInterpreterGroup().destroy();

          InterpreterGroup interpreterGroup = createInterpreterGroup(
              intpsetting.getGroup(), intpsetting.getProperties());
          intpsetting.setInterpreterGroup(interpreterGroup);
        } else {
          throw new InterpreterException("Interpreter setting id " + id
              + " not found");
        }
      }
    }     
  }

  private Interpreter createRepl(String dirName, String className, Properties property)
      throws InterpreterException {
    logger.info("Create repl {} from {}", className, dirName);

    ClassLoader oldcl = Thread.currentThread().getContextClassLoader();
    try {

      URLClassLoader ccl = cleanCl.get(dirName);
      if (ccl == null) {
        // classloader fallback
        ccl = URLClassLoader.newInstance(new URL[] {}, oldcl);
      }

      boolean separateCL = true;
      try { // check if server's classloader has driver already.
        Class cls = this.getClass().forName(className);
        if (cls != null) {
          separateCL = false;
        }
      } catch (Exception e) {
        // nothing to do.
      }

      URLClassLoader cl;

      if (separateCL == true) {
        cl = URLClassLoader.newInstance(new URL[] {}, ccl);
      } else {
        cl = (URLClassLoader) ccl;
      }
      Thread.currentThread().setContextClassLoader(cl);

      Class<Interpreter> replClass = (Class<Interpreter>) cl.loadClass(className);
      Constructor<Interpreter> constructor =
          replClass.getConstructor(new Class[] {Properties.class});
      Interpreter repl = constructor.newInstance(property);
      if (conf.containsKey("args")) {
        property.put("args", conf.getProperty("args"));
      }
      property.put("share", share);
      property.put("classloaderUrls", ccl.getURLs());
      return new LazyOpenInterpreter(new ClassloaderInterpreter(repl, cl, property));
    } catch (SecurityException e) {
      throw new InterpreterException(e);
    } catch (NoSuchMethodException e) {
      throw new InterpreterException(e);
    } catch (IllegalArgumentException e) {
      throw new InterpreterException(e);
    } catch (InstantiationException e) {
      throw new InterpreterException(e);
    } catch (IllegalAccessException e) {
      throw new InterpreterException(e);
    } catch (InvocationTargetException e) {
      throw new InterpreterException(e);
    } catch (ClassNotFoundException e) {
      throw new InterpreterException(e);
    } finally {
      Thread.currentThread().setContextClassLoader(oldcl);
    }
  }

  private URL[] recursiveBuildLibList(File path) throws MalformedURLException {
    URL[] urls = new URL[0];
    if (path == null || path.exists() == false) {
      return urls;
    } else if (path.getName().startsWith(".")) {
      return urls;
    } else if (path.isDirectory()) {
      File[] files = path.listFiles();
      if (files != null) {
        for (File f : files) {
          urls = (URL[]) ArrayUtils.addAll(urls, recursiveBuildLibList(f));
        }
      }
      return urls;
    } else {
      return new URL[] {path.toURI().toURL()};
    }
  }
}
