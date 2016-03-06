package org.apache.zeppelin.livy;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
/**
 * 
 *
 */
public class SessionCallable implements Callable<Session> {
  int count = 0;
  private Session session;

  public SessionCallable(Session session) {
    this.session = session;
  }

  @Override
  public Session call() throws Exception {
    while (!this.session.state.equals("idle")) {
      if (this.session.state.equals("error")){
        return this.session;
      }
      this.session = SessionFactory.getSession(this.session);
      TimeUnit.MILLISECONDS.sleep(2000);
    }
    return this.session;
  }
}
