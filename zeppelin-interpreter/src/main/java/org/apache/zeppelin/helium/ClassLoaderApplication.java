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

import org.apache.zeppelin.resource.ResourceSet;

/**
 * Application wrapper
 */
public class ClassLoaderApplication extends Application {
  Application app;
  ClassLoader cl;
  public ClassLoaderApplication(Application app, ClassLoader cl) throws ApplicationException {
    super(app.context());
    this.app = app;
    this.cl = cl;
  }

  @Override
  public void run(ResourceSet args) throws ApplicationException {
    // instantiate
    ClassLoader oldcl = Thread.currentThread().getContextClassLoader();
    Thread.currentThread().setContextClassLoader(cl);
    try {
      app.run(args);
    } catch (ApplicationException e) {
      throw e;
    } catch (Exception e) {
      throw new ApplicationException(e);
    } finally {
      Thread.currentThread().setContextClassLoader(oldcl);
    }
  }

  @Override
  public void unload() throws ApplicationException {
    // instantiate
    ClassLoader oldcl = Thread.currentThread().getContextClassLoader();
    Thread.currentThread().setContextClassLoader(cl);
    try {
      app.unload();
    } catch (ApplicationException e) {
      throw e;
    } catch (Exception e) {
      throw new ApplicationException(e);
    } finally {
      Thread.currentThread().setContextClassLoader(oldcl);
    }
  }

  public ClassLoader getClassLoader() {
    return cl;
  }

  public Application getInnerApplication() {
    return app;
  }
}
