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

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.subjects.PublishSubject;
import io.reactivex.rxjava3.subjects.Subject;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ZeppelinEventBus implements EventBus {

  private static final Logger LOGGER = LoggerFactory.getLogger(ZeppelinEventBus.class);

  private final Subject<ZeppelinEvent> eventBus;

  @Inject
  public ZeppelinEventBus() {
    LOGGER.info("Starting ZeppelinEventBus");

    eventBus = PublishSubject.<ZeppelinEvent>create().toSerialized();
  }

  @Override
  public void post(ZeppelinEvent event) {
    LOGGER.debug("Posting event: {}", event.getClass().getName());

    eventBus.onNext(event);
  }

  @Override
  public <T extends ZeppelinEvent> Observable<T> observe(Class<T> eventType) {
    LOGGER.debug("Observing event: {}", eventType.getName());

    return eventBus.ofType(eventType);
  }
}
