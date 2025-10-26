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

package org.apache.zeppelin.event;

import io.reactivex.rxjava3.disposables.Disposable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MockEvent {
  String payload;

  public MockEvent(String payload) {
    this.payload = payload;
  }
}

class Publisher {
  private final ZeppelinEventBus eventBus;

  public Publisher(ZeppelinEventBus eventBus) {
    this.eventBus = eventBus;
  }

  public void createNote(String noteId) {
    eventBus.post(new MockEvent(noteId));
  }
}

class Subscriber {
  List<String> collection = new ArrayList<>();

  Disposable disposable;

  public Subscriber(ZeppelinEventBus eventBus) {
    this.disposable = eventBus.observe(MockEvent.class)
        .subscribe(event -> {
          String payload = event.payload;
          collection.add(payload);
          System.out.println("EventSubscriber: event received, payload: " + payload);
        });
  }

  public void stopListening() {
    if (disposable != null && !disposable.isDisposed()) {
      disposable.dispose();
    }
  }
}

class ZeppelinEventBusTest {
  @Test
  void testEventFlowFromPublisherToSubscriber() throws InterruptedException {
    // Given
    var bus = new ZeppelinEventBus();

    var publisher = new Publisher(bus);
    var subscriber = new Subscriber(bus);

    // When
    String payload = "data";
    publisher.createNote(payload);

    Thread.sleep(100);

    // Then
    List<String> received = subscriber.collection;

    assertEquals(1, received.size());
    assertEquals(payload, received.get(0));
    assertTrue(received.contains(payload));

    // Cleanup
    subscriber.stopListening();
  }
}