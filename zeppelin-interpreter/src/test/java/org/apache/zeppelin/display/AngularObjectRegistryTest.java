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

package org.apache.zeppelin.display;

import static org.junit.Assert.assertEquals;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

public class AngularObjectRegistryTest {

  @Test
  public void testBasic() {
    final AtomicInteger onAdd = new AtomicInteger(0);
    final AtomicInteger onUpdate = new AtomicInteger(0);
    final AtomicInteger onRemove = new AtomicInteger(0);

    AngularObjectRegistry registry = new AngularObjectRegistry("intpId",
        new AngularObjectRegistryListener() {

          @Override
          public void onAdd(String interpreterGroupId, AngularObject object) {
            onAdd.incrementAndGet();
          }

          @Override
          public void onUpdate(String interpreterGroupId, AngularObject object) {
            onUpdate.incrementAndGet();
          }

          @Override
          public void onRemove(String interpreterGroupId, AngularObject object) {
            onRemove.incrementAndGet();
          }
    });

    registry.add("name1", "value1");
    assertEquals(1, registry.getAll().size());
    assertEquals(1, onAdd.get());
    assertEquals(0, onUpdate.get());

    registry.get("name1").set("newValue");
    assertEquals(1, onUpdate.get());

    registry.remove("name1");
    assertEquals(0, registry.getAll().size());
    assertEquals(1, onRemove.get());

    assertEquals(null, registry.get("name1"));
  }
}
