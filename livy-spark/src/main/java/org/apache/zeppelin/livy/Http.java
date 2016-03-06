package org.apache.zeppelin.livy;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.Response;

/**
 * 
 * */
public class Http {

  static AsyncHttpClient asyncHttpClient = new AsyncHttpClient();

  private static Response getFromFuture(Future<Response> f) {
    try {
      Response r = f.get();
      return r;
    } catch (InterruptedException e) {
      e.printStackTrace();
    } catch (ExecutionException e) {
      e.printStackTrace();
    }
    return null;
  }


  public static Response get(String url) {
    Future<Response> f = asyncHttpClient.prepareGet(url).execute();
    return getFromFuture(f);
  }


  public static Response post(String url, String jsonString) {
    final AsyncHttpClient.BoundRequestBuilder builder = asyncHttpClient.preparePost(url);

    builder.addHeader("Content-Type", "application/json");
    builder.setBody(jsonString);
    Future<Response> f = builder.execute();
    return getFromFuture(f);

  }

  public static Response delete(String url) {
    Future<Response> f = asyncHttpClient.prepareDelete(url).execute();
    return getFromFuture(f);
  }

}
