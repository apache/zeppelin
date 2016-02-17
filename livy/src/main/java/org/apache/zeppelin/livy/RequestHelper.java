package org.apache.zeppelin.livy;

import com.google.gson.Gson;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by prabhjyot.singh on 17/02/16.
 */
public class RequestHelper {
  static Logger LOGGER = LoggerFactory.getLogger(RequestHelper.class);

  public static String executeHTTP(String targetURL, String method, String jsonData)
      throws Exception {

    HttpClient client = HttpClientBuilder.create().build();

    HttpResponse response;
    LOGGER.error(jsonData);
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


    if (response.getStatusLine().getStatusCode() == 200
        || response.getStatusLine().getStatusCode() == 201) {
      BufferedReader rd = new BufferedReader(
          new InputStreamReader(response.getEntity().getContent()));

      StringBuffer result = new StringBuffer();
      String line = "";
      while ((line = rd.readLine()) != null) {
        result.append(line);
      }
      return result.toString();
    }
    return null;
  }

  public static void main(String[] asd) throws Exception {
//    String host = "http://192.168.0.106";
//    String sessionCreate = executeHTTP(host + ":8998/sessions", "POST",
// "{\"kind\": \"pyspark\", \"proxyUser\": \"bob222\"}");
//    System.out.println(sessionCreate);
//
//    String countSession = executeHTTP(host + ":8998/sessions", "GET", null);
//    System.out.println(countSession);
    String user = "user";
    String DEFAULT_URL = "http://192.168.0.106:8998";
//    String json = executeHTTP(DEFAULT_URL + "/sessions", "POST",
// "{\"kind\": \"pyspark\", \"proxyUser\": \"" + user + "\"}");
    Gson gson = new Gson();
//    Map jsonMap =  (Map<String, Object>) gson.fromJson(json,
//        new TypeToken<Map<String, Object>>() {}.getType());

    Map<String, Integer> stringIntegerHashMap = new HashMap<>();
    stringIntegerHashMap.put(null, 1);
    System.out.println();
//    String lines = "import time\\n" +
//        "REFRAIN = '''\\n" +
//        "%d bottles of beer on the wall,\\n" +
//        "%d bottles of beer,\\n" +
//        "take one down, pass it around,\\n" +
//        "%d bottles of beer on the wall!\\n" +
//        "'''\\n" +
//        "bottles_of_beer = 10\\n" +
//        "while bottles_of_beer > 1:\\n" +
//        "    time.sleep(1)\\n" +
//        "    print REFRAIN % (bottles_of_beer, bottles_of_beer,\\n" +
//        "        bottles_of_beer - 1)\\n" +
//        "    bottles_of_beer -= 1\\n";
//    String json = executeHTTP(DEFAULT_URL + "/sessions/"
//            + 0
//            + "/statements",
//        "POST",
//        "{\"code\": \"" + lines + "\" }");
//    Map jsonMap = (Map<String, Object>) gson.fromJson(json,
//        new TypeToken<Map<String, Object>>() {
//        }.getType());
//    System.out.println(json);
  }
}
