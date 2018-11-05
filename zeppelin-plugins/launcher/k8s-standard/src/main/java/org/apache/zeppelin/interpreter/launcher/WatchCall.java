package org.apache.zeppelin.interpreter.launcher;

import com.squareup.okhttp.Call;
import io.kubernetes.client.ApiException;

public interface WatchCall {
  Call list(String resourceVersion) throws ApiException;
}
