package org.apache.zeppelin.resourcepool;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Unittest for DistributedResourcePool
 */
public class DistributedResourcePoolTest {

  @Test
  public void testDistributedResourcePool() {
    final LocalResourcePool pool2 = new LocalResourcePool("pool2");
    final LocalResourcePool pool3 = new LocalResourcePool("pool3");

    DistributedResourcePool pool1 = new DistributedResourcePool("pool1", new ResourcePoolConnector() {
      @Override
      public ResourceSet getAllResourcesExcept(String excludePoolId) {
        ResourceSet set = pool2.getAll();
        set.addAll(pool3.getAll());
        return set;
      }
    });

    assertEquals(0, pool1.getAll().size());


    // test get() can get from pool
    pool2.put("object1", "value2");
    assertEquals(1, pool1.getAll().size());
    assertEquals("value2", pool1.get("object1").get());

    // test get() is locality aware
    pool1.put("object1", "value1");
    assertEquals(1, pool2.getAll().size());
    assertEquals("value1", pool1.get("object1").get());

    // test getAll() is locality aware
    assertEquals("value1", pool1.getAll().get(0).get());
    assertEquals("value2", pool1.getAll().get(1).get());
  }
}
