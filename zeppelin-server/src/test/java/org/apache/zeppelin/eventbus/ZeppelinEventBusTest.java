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
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.reactivex.rxjava3.disposables.Disposable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class ZeppelinEventBusTest {

  @Test
  void eventBusFlow() throws InterruptedException {
    ZeppelinEventBus bus = new ZeppelinEventBus();
    Publisher publisher = new Publisher(bus);
    Subscriber subscriber = new Subscriber(bus);

    String payload = "data";
    publisher.createNote(payload);

    assertTrue(subscriber.awaitEvent());

    List<String> received = subscriber.collection;
    assertEquals(1, received.size());
    assertEquals(payload, received.get(0));
    assertTrue(received.contains(payload));

    subscriber.stopListening();
  }

  private static class MockEvent implements ZeppelinEvent {
    private final String payload;

    MockEvent(String payload) {
      this.payload = payload;
    }
  }

  private static class Publisher {
    private final ZeppelinEventBus eventBus;

    Publisher(ZeppelinEventBus eventBus) {
      this.eventBus = eventBus;
    }

    void createNote(String noteId) {
      eventBus.post(new MockEvent(noteId));
    }
  }

  private static class Subscriber {
    private final List<String> collection = new ArrayList<>();

    private final CountDownLatch eventReceived = new CountDownLatch(1);

    private final Disposable disposable;

    Subscriber(ZeppelinEventBus eventBus) {
      this.disposable = eventBus.observe(MockEvent.class)
          .subscribe(event -> {
            collection.add(event.payload);
            eventReceived.countDown();
          });
    }

    boolean awaitEvent() throws InterruptedException {
      return eventReceived.await(1, TimeUnit.SECONDS);
    }

    void stopListening() {
      if (!disposable.isDisposed()) {
        disposable.dispose();
      }
    }
  }
}
