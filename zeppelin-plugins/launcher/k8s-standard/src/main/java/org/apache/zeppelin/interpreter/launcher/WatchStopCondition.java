package org.apache.zeppelin.interpreter.launcher;

import io.kubernetes.client.util.Watch;

public interface WatchStopCondition<T> {
  boolean shouldStop(Watch.Response<T> item);
}
