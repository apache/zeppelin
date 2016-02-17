package org.apache.zeppelin.livy;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.params.HttpConnectionParams;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * Created by prabhjyot.singh on 17/02/16.
 */
public class RequestHelper {

  public static String executeHTTP(String targetURL, String method, String jsonData)
      throws Exception {

    HttpClient client = HttpClientBuilder.create().build();

    HttpResponse response;
    if (method.equals("POST")) {
      HttpPost request = new HttpPost(targetURL);
      request.addHeader("Content-Type", "application/json");
      StringEntity se = new StringEntity(jsonData);
      request.setEntity(se);
      response = client.execute(request);
    } else {
      HttpGet request = new HttpGet(targetURL);
      request.addHeader("Content-Type", "application/json");
      response = client.execute(request);
    }


    if (response.getStatusLine().getStatusCode() == 200 || response.getStatusLine().getStatusCode() == 201 ) {
      return null;
    }
    BufferedReader rd = new BufferedReader(
        new InputStreamReader(response.getEntity().getContent()));

    StringBuffer result = new StringBuffer();
    String line = "";
    while ((line = rd.readLine()) != null) {
      result.append(line);
    }
    return result.toString();
//    HttpURLConnection connection = null;
//    try {
//      //Create connection
//      URL url = new URL(targetURL);
//      connection = (HttpURLConnection) url.openConnection();
//      connection.setRequestProperty("Content-Type",
//          "application/json; charset=UTF-8");
//      connection.setDoOutput(true);
//      connection.setDoInput(true);
//      connection.setRequestMethod(method);
//
//      connection.setRequestProperty("Content-Length",
//          Integer.toString(urlParameters.getBytes().length));
//      connection.setRequestProperty("Content-Language", "en-US");
//
//      connection.setUseCaches(false);
//      connection.setDoOutput(true);
//
//      //Send request
//      OutputStreamWriter wr = new OutputStreamWriter(connection.getOutputStream());
//      if (jsonData != null) {
//        wr.write(jsonData);
//      }
//      wr.close();
//
//      //Get Response
//      InputStream is = connection.getInputStream();
//      BufferedReader rd = new BufferedReader(new InputStreamReader(is));
//      StringBuilder response = new StringBuilder(); // or StringBuffer if not Java 5+
//      String line;
//      while ((line = rd.readLine()) != null) {
//        response.append(line);
//        response.append('\r');
//      }
//      rd.close();
//      return response.toString();
//    } catch (Exception e) {
//      e.printStackTrace();
//      throw e;
//    } finally {
//      if (connection != null) {
//        connection.disconnect();
//      }
//    }
  }

  public static void main(String[] asd) throws Exception {
    String host = "http://192.168.0.106";
    String sessionCreate = executeHTTP(host + ":8998/sessions", "POST", "{\"kind\": \"pyspark\", \"proxyUser\": \"bob222\"}");
    System.out.println(sessionCreate);

    String countSession = executeHTTP(host + ":8998/sessions", "GET", null);
    System.out.println(countSession);
  }
}
