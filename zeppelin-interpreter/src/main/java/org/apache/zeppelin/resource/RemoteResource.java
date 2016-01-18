package org.apache.zeppelin.resource;

/**
 * Resource that can retrieve data from remote
 */
public class RemoteResource extends Resource {
  ResourcePoolConnector resourcePoolConnector;

  RemoteResource(ResourceId resourceId, Object r) {
    super(resourceId, r);
  }

  RemoteResource(ResourceId resourceId, boolean serializable, String className) {
    super(resourceId, serializable, className);
  }

  @Override
  public Object get() {
    if (isSerializable()) {
      Object o = resourcePoolConnector.readResource(getResourceId());
      return o;
    } else {
      return null;
    }
  }

  @Override
  public boolean isLocal() {
    return false;
  }

  public ResourcePoolConnector getResourcePoolConnector() {
    return resourcePoolConnector;
  }

  public void setResourcePoolConnector(ResourcePoolConnector resourcePoolConnector) {
    this.resourcePoolConnector = resourcePoolConnector;
  }
}
