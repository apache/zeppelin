package org.apache.zeppelin.event;

import io.reactivex.rxjava3.core.Observable;
import org.junit.jupiter.api.Test;

class RxJavaTest {

  @Test
  void testObservable() {
    Observable<String> observable = Observable.just("Hello", "RxJava", "Test");

    observable.test()
        .assertValues("Hello", "RxJava", "Test")
        .assertComplete()
        .assertNoErrors();
  }
}
