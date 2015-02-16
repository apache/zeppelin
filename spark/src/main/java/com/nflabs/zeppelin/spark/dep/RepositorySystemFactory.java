package com.nflabs.zeppelin.spark.dep;

import org.apache.maven.repository.internal.DefaultServiceLocator;
import org.apache.maven.wagon.Wagon;
import org.apache.maven.wagon.providers.http.HttpWagon;
import org.apache.maven.wagon.providers.http.LightweightHttpWagon;
import org.sonatype.aether.RepositorySystem;
import org.sonatype.aether.connector.file.FileRepositoryConnectorFactory;
import org.sonatype.aether.connector.wagon.WagonProvider;
import org.sonatype.aether.connector.wagon.WagonRepositoryConnectorFactory;
import org.sonatype.aether.spi.connector.RepositoryConnectorFactory;

/**
 * Get maven repository instance.
 *
 * @author anthonycorbacho
 *
 */
public class RepositorySystemFactory {
  public static RepositorySystem newRepositorySystem() {
    DefaultServiceLocator locator = new DefaultServiceLocator();
    locator.addService(RepositoryConnectorFactory.class, FileRepositoryConnectorFactory.class);
    locator.addService(RepositoryConnectorFactory.class, WagonRepositoryConnectorFactory.class);
    locator.setServices(WagonProvider.class, new ManualWagonProvider());

    return locator.getService(RepositorySystem.class);
  }

  /**
   * ManualWagonProvider
   */
  public static class ManualWagonProvider implements WagonProvider {

    @Override
    public Wagon lookup(String roleHint) throws Exception {
      if ("http".equals(roleHint)) {
        return new LightweightHttpWagon();
      }

      if ("https".equals(roleHint)) {
        return new HttpWagon();
      }

      return null;
    }

    @Override
    public void release(Wagon arg0) {

    }
  }
}
