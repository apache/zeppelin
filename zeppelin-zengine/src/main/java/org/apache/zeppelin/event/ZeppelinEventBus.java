package org.apache.zeppelin.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.subjects.PublishSubject;
import io.reactivex.rxjava3.subjects.Subject;
import jakarta.inject.Inject;

public class ZeppelinEventBus implements EventBus {

  private static final Logger LOGGER = LoggerFactory.getLogger(ZeppelinEventBus.class);

  private final Subject<Object> eventBus;

  @Inject
  public ZeppelinEventBus() {
    LOGGER.info("Starting ZeppelinEventBus");

    eventBus = PublishSubject.create();
  }

  @Override
  public void post(Object event) {
    LOGGER.debug("Posting event: {}", event.getClass().getName());

    eventBus.onNext(event);
  }

  @Override
  public <T> Observable<T> observe(Class<T> eventType) {
    LOGGER.debug("Observing event: {}", eventType.getName());

    return eventBus.ofType(eventType);
  }
}
