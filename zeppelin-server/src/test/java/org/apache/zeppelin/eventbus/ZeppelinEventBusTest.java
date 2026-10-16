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

package org.apache.zeppelin.eventbus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import io.reactivex.rxjava3.disposables.Disposable;

class ZeppelinEventBusTest {

  @Test
  void testSubscription() {
    EventBus bus = new ZeppelinEventBus();
    List<String> received = new ArrayList<>();
    Disposable subscription = bus.subscribe(MockEvent.class, event -> received.add(event.payload));

    bus.post(new ZeppelinEvent() { });
    bus.post(new MockEvent("received"));
    assertEquals(List.of("received"), received);

    subscription.dispose();
    bus.post(new MockEvent("ignored"));
    assertEquals(List.of("received"), received);
  }

  @Test
  void testContinueAfterException() {
    EventBus bus = new ZeppelinEventBus();
    List<String> received = new ArrayList<>();
    Disposable subscription = bus.subscribe(MockEvent.class, event -> {
      if ("checked".equals(event.payload)) {
        throw new IOException("Handler failed");
      }
      if ("runtime".equals(event.payload)) {
        throw new IllegalStateException("Handler failed");
      }
      received.add(event.payload);
    });

    try {
      bus.post(new MockEvent("checked"));
      bus.post(new MockEvent("after checked"));
      bus.post(new MockEvent("runtime"));
      bus.post(new MockEvent("after runtime"));

      assertFalse(subscription.isDisposed());
      assertEquals(List.of("after checked", "after runtime"), received);
    } finally {
      subscription.dispose();
    }
  }

  private static class MockEvent implements ZeppelinEvent {
    private final String payload;

    MockEvent(String payload) {
      this.payload = payload;
    }
  }
}
