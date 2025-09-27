package org.apache.zeppelin.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.reactivex.rxjava3.core.Observable;
import jakarta.inject.Inject;

public class NoOpEventBus implements EventBus {

  private static final Logger LOGGER = LoggerFactory.getLogger(NoOpEventBus.class);

  @Inject
  public NoOpEventBus() {
    LOGGER.info("Starting NoOpEventBus");
  }

  @Override
  public void post(Object event) {
    LOGGER.debug("Posting event: {}", event.getClass().getName());
  }

  @Override
  public <T> io.reactivex.rxjava3.core.Observable<T> observe(Class<T> eventType) {
    return Observable.empty();
  }
}
