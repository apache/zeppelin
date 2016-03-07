/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.zeppelin.helium;

import org.apache.zeppelin.dep.DependencyResolver;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.resource.DistributedResourcePool;
import org.apache.zeppelin.resource.Resource;
import org.apache.zeppelin.resource.ResourcePool;
import org.apache.zeppelin.resource.ResourceSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.lang.reflect.Constructor;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;

/**
 * Load application
 */
public class ApplicationLoader {
  Logger logger = LoggerFactory.getLogger(ApplicationLoader.class);

  private final DependencyResolver depResolver;
  private final ResourcePool resourcePool;
  private final Map<HeliumPackageInfo, Class<Application>> cached;
  private final Map<RunningApplication, Application> runningApplications;

  public ApplicationLoader(ResourcePool resourcePool, DependencyResolver depResolver) {
    this.depResolver = depResolver;
    this.resourcePool = resourcePool;
    cached = Collections.synchronizedMap(
        new HashMap<HeliumPackageInfo, Class<Application>>());
    runningApplications = new HashMap<RunningApplication, Application>();
  }

  /**
   * Information of loaded application
   */
  private static class RunningApplication {
    HeliumPackageInfo packageInfo;
    String noteId;
    String paragraphId;

    public RunningApplication(HeliumPackageInfo packageInfo, String noteId, String paragraphId) {
      this.packageInfo = packageInfo;
      this.noteId = noteId;
      this.paragraphId = paragraphId;
    }

    public HeliumPackageInfo getPackageInfo() {
      return packageInfo;
    }

    public String getNoteId() {
      return noteId;
    }

    public String getParagraphId() {
      return paragraphId;
    }

    @Override
    public int hashCode() {
      return (paragraphId + noteId + packageInfo.getArtifact() + packageInfo.getClassName())
          .hashCode();
    }

    @Override
    public boolean equals(Object o) {
      if (!(o instanceof RunningApplication)) {
        return false;
      }

      RunningApplication r = (RunningApplication) o;
      return packageInfo.equals(r.getPackageInfo()) && paragraphId.equals(r.getParagraphId()) &&
          noteId.equals(r.getNoteId());
    }
  }

  /**
   *
   * Instantiate application
   *
   * @param packageInfo
   * @param context
   * @return
   * @throws Exception
   */
  public Application load(HeliumPackageInfo packageInfo, ApplicationContext context)
    throws Exception {
    if (packageInfo.getType() != HeliumPackageInfo.Type.APPLICATION) {
      throw new ApplicationException(
          "Can't instantiate " + packageInfo.getType() + " package using ApplicationLoader");
    }

    // check if already loaded
    RunningApplication key =
        new RunningApplication(packageInfo, context.getNoteId(), context.getParagraphId());
    synchronized (runningApplications) {
      if (runningApplications.containsKey(key)) {
        return runningApplications.get(key);
      }
    }

    // get resource required by this package
    ResourceSet resources = findRequiredResourceSet(packageInfo.getResources(),
        context.getNoteId(), context.getParagraphId());

    // load class
    Class<Application> appClass = loadClass(packageInfo);

    // instantiate
    ClassLoader oldcl = Thread.currentThread().getContextClassLoader();
    ClassLoader cl = appClass.getClassLoader();
    Thread.currentThread().setContextClassLoader(cl);
    try {
      Constructor<Application> constructor =
          appClass.getConstructor(ResourceSet.class, ApplicationContext.class);

      synchronized (runningApplications) {
        if (!runningApplications.containsKey(key)) {
          logger.info("Load {} {} from note {} paragraph {}",
              packageInfo.getArtifact(),
              packageInfo.getClassName(),
              context.getNoteId(),
              context.getParagraphId());

          Application app = new ClassLoaderApplication(
              constructor.newInstance(resources, context),
              cl);
          runningApplications.put(key, app);
          return app;
        } else {
          return runningApplications.get(key);
        }
      }
    } catch (Exception e) {
      throw new ApplicationException(e);
    } finally {
      Thread.currentThread().setContextClassLoader(oldcl);
    }
  }

  public void unload(HeliumPackageInfo packageInfo, ApplicationContext context)
      throws ApplicationException {
    Application appToUnload = null;
    synchronized (runningApplications) {
      RunningApplication key
          = new RunningApplication(packageInfo, context.getNoteId(), context.getParagraphId());

      if (runningApplications.containsKey(key)) {
        appToUnload = runningApplications.remove(key);
      }
    }

    if (appToUnload != null) {
      logger.info("Unload {} {} from note {} paragraph {}",
          packageInfo.getArtifact(),
          packageInfo.getClassName(),
          context.getNoteId(),
          context.getParagraphId());
      appToUnload.unload();
    }
  }

  public Application get(HeliumPackageInfo packageInfo, ApplicationContext context) {
    synchronized (runningApplications) {
      RunningApplication key
          = new RunningApplication(packageInfo, context.getNoteId(), context.getParagraphId());
      return runningApplications.get(key);
    }
  }

  private ResourceSet findRequiredResourceSet(
      String [][] requiredResources, String noteId, String paragraphId)
      throws ApplicationException {
    if (requiredResources == null || requiredResources.length == 0) {
      return new ResourceSet();
    }

    String localResourcePoolId = resourcePool.id();
    ResourceSet args = new ResourceSet();
    ResourceSet allResources;
    if (resourcePool instanceof DistributedResourcePool) {
      allResources = ((DistributedResourcePool) resourcePool).getAll(false);
    } else {
      allResources = resourcePool.getAll();
    }

    allResources = allResources.filterByNoteId(noteId).filterByParagraphId(paragraphId);

    for (String [] requires : requiredResources) {
      args.clear();

      for (String require : requires) {
        boolean found = false;

        for (Resource r : allResources) {
          if (r.getClassName().equals(require)) {
            args.add(r);
            found = true;
            break;
          }
        }

        if (found == false) {
          break;
        }
      }

      if (args.size() == requires.length) {
        return args;
      }
    }

    throw new ApplicationException("Can not find available resources");
  }


  private Class<Application> loadClass(HeliumPackageInfo packageInfo) throws Exception {
    if (cached.containsKey(packageInfo)) {
      return cached.get(packageInfo);
    }

    // Create Application classloader
    List<URL> urlList = new LinkedList<URL>();

    // load artifact
    if (packageInfo.getArtifact() != null) {
      List<File> paths = depResolver.load(packageInfo.getArtifact());

      if (paths != null) {

        for (File path : paths) {
          urlList.add(path.toURI().toURL());
        }
      }
    }
    URLClassLoader applicationClassLoader =
        new URLClassLoader(
            urlList.toArray(new URL[]{}),
            Thread.currentThread().getContextClassLoader());

    Class<Application> cls =
        (Class<Application>) applicationClassLoader.loadClass(packageInfo.getClassName());
    cached.put(packageInfo, cls);
    return cls;
  }
}
