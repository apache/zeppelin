package org.apache.zeppelin.event;

import io.reactivex.rxjava3.core.Observable;

public interface EventBus {

  void post(Object event);

  <T> Observable<T> observe(Class<T> eventType);
}
