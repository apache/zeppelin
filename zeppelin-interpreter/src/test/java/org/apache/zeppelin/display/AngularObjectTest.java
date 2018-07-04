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

import org.apache.thrift.TException;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;

public class AngularObjectTest {

  @Test
  public void testEquals() {
    assertEquals(
        new AngularObject("name", "value", "note1", null, null),
        new AngularObject("name", "value", "note1", null, null)
    );

    assertEquals(
        new AngularObject("name", "value", "note1", "paragraph1", null),
        new AngularObject("name", "value", "note1", "paragraph1", null)
    );

    assertEquals(
        new AngularObject("name", "value", null, null, null),
        new AngularObject("name", "value", null, null, null)
    );

    assertEquals(
        new AngularObject("name", "value1", null, null, null),
        new AngularObject("name", "value2", null, null, null)
    );

    assertNotSame(
        new AngularObject("name1", "value", null, null, null),
        new AngularObject("name2", "value", null, null, null)
    );

    assertNotSame(
        new AngularObject("name1", "value", "note1", null, null),
        new AngularObject("name2", "value", "note2", null, null)
    );

    assertNotSame(
        new AngularObject("name1", "value", "note", null, null),
        new AngularObject("name2", "value", null, null, null)
    );

    assertNotSame(
        new AngularObject("name", "value", "note", "paragraph1", null),
        new AngularObject("name", "value", "note", "paragraph2", null)
    );

    assertNotSame(
        new AngularObject("name", "value", "note1", null, null),
        new AngularObject("name", "value", "note1", "paragraph1", null)
    );


  }

  @Test
  public void testListener() throws TException {
    final AtomicInteger updated = new AtomicInteger(0);
    AngularObject ao = new AngularObject("name", "value", "note1", null,
        new AngularObjectListener() {

          @Override
          public void updated(AngularObject updatedObject) {
            updated.incrementAndGet();
          }
        });

    assertEquals(0, updated.get());
    ao.set("newValue");
    assertEquals(1, updated.get());
    assertEquals("newValue", ao.get());

    ao.set("newValue");
    assertEquals(2, updated.get());

    ao.set("newnewValue", false);
    assertEquals(2, updated.get());
    assertEquals("newnewValue", ao.get());
  }

  @Test
  public void testWatcher() throws InterruptedException, TException {
    final AtomicInteger updated = new AtomicInteger(0);
    final AtomicInteger onWatch = new AtomicInteger(0);
    AngularObject ao = new AngularObject("name", "value", "note1", null,
        new AngularObjectListener() {
          @Override
          public void updated(AngularObject updatedObject) {
            updated.incrementAndGet();
          }
        });

    ao.addWatcher(new AngularObjectWatcher(null) {
      @Override
      public void watch(Object oldObject, Object newObject, InterpreterContext context) {
        onWatch.incrementAndGet();
      }
    });

    assertEquals(0, onWatch.get());
    ao.set("newValue");

    Thread.sleep(500);
    assertEquals(1, onWatch.get());
  }
}
