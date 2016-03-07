package org.apache.zeppelin.helium;

import org.apache.zeppelin.resource.ResourceSet;

/**
 * Application wrapper
 */
public class ClassLoaderApplication extends Application {
  Application app;
  ClassLoader cl;
  public ClassLoaderApplication(Application app, ClassLoader cl) throws ApplicationException {
    super(null, null);
    this.app = app;
    this.cl = cl;
  }

  @Override
  public void run() throws ApplicationException {
    // instantiate
    ClassLoader oldcl = Thread.currentThread().getContextClassLoader();
    Thread.currentThread().setContextClassLoader(cl);
    try {
      app.run();
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
