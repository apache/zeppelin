/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.zeppelin.interpreter.launcher;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.Principal;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonIOException;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import org.apache.commons.lang.StringUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.security.UserGroupInformation;

import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthSchemeProvider;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.AuthSchemes;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.config.Lookup;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.impl.auth.SPNegoSchemeFactory;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.zeppelin.conf.ZeppelinConfiguration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.security.auth.Subject;
import javax.security.auth.kerberos.KerberosPrincipal;
import javax.security.auth.login.AppConfigurationEntry;
import javax.security.auth.login.LoginContext;

import static org.apache.zeppelin.conf.ZeppelinConfiguration.ConfVars.ZEPPELIN_SERVER_KERBEROS_KEYTAB;
import static org.apache.zeppelin.conf.ZeppelinConfiguration.ConfVars.ZEPPELIN_SERVER_KERBEROS_PRINCIPAL;

public class YarnClient {
  private Logger LOGGER = LoggerFactory.getLogger(YarnClient.class);

  private String yarnWebHttpAddr;
  private String principal = "";
  private String keytab = "";

  public static final String YARN_REST_APPATTEMPTS = "appAttempts";
  public static final String YARN_REST_CONTAINER = "container";
  public static final String YARN_REST_APPATTEMPT = "appAttempt";
  public static final String YARN_REST_APPATTEMPTID = "appAttemptId";

  public static final String YARN_REST_EXPOSEDPORTS = "EXPOSEDPORTS";
  public static final String CONTAINER_IP = "CONTAINER_IP";
  public static final String CONTAINER_PORT = "CONTAINER_PORT";
  public static final String HOST_IP = "HOST_IP";
  public static final String HOST_PORT = "HOST_PORT";

  private Configuration hadoopConf;
  private ZeppelinConfiguration zConf = ZeppelinConfiguration.create();
  private boolean hadoopSecurityEnabled = false; // simple or kerberos

  public YarnClient() {
    this.hadoopConf = new Configuration();

    hadoopSecurityEnabled = UserGroupInformation.isSecurityEnabled();
    if (hadoopSecurityEnabled) {
      String keytab = zConf.getString(ZEPPELIN_SERVER_KERBEROS_KEYTAB);
      String principal = zConf.getString(ZEPPELIN_SERVER_KERBEROS_PRINCIPAL);

      if (StringUtils.isBlank(keytab) || StringUtils.isBlank(principal)) {
        throw new RuntimeException("keytab and principal can not be empty, keytab: "
            + keytab + ", principal: " + principal);
      }
      this.principal = principal;
      this.keytab = keytab;
      if (LOGGER.isDebugEnabled()) {
        System.setProperty("sun.security.spnego.debug", "true");
        System.setProperty("sun.security.krb5.debug", "true");
      }
    }
    yarnWebHttpAddr = zConf.getYarnWebappAddress();
  }

  private HttpResponse callRest(final String url, HTTP operation) {
    if (hadoopSecurityEnabled) {
      return callSecurityRest(url, operation);
    } else {
      return callSimpleRest(url, operation);
    }
  }

  public HttpResponse callSimpleRest(final String url, HTTP operation) {
    DefaultHttpClient httpclient = new DefaultHttpClient();
    HttpResponse response = null;
    HttpUriRequest request = null;

    switch (operation) {
      case DELETE:
        request = new HttpDelete(url);
        break;
      case POST:
        request = new HttpPost(url);
        break;
      default:
        request = new HttpGet(url);
        break;
    }

    try {
      response = httpclient.execute(request);
    } catch (IOException e) {
      LOGGER.error(e.getMessage(), e);
    }

    return response;
  }

  // yarn application name match the pattern [a-z][a-z0-9-]*
  public static String formatYarnAppName(String interpreterGroupId) {
    return interpreterGroupId.toLowerCase().replaceAll("_", "-");
  }

  // http://yarn-web-http-address/app/v1/services/{service_name}
  public void deleteService(String serviceName) {
    String appUrl = this.yarnWebHttpAddr + "/app/v1/services/" + serviceName
        + "?_=" + System.currentTimeMillis();

    InputStream inputStream = null;
    try {
      HttpResponse response = callRest(appUrl, HTTP.DELETE);
      inputStream = response.getEntity().getContent();
      String result = new BufferedReader(new InputStreamReader(inputStream))
          .lines().collect(Collectors.joining(System.lineSeparator()));
      if (response.getStatusLine().getStatusCode() != 200 /*success*/) {
        LOGGER.warn("Status code " + response.getStatusLine().getStatusCode());
        LOGGER.warn("message is :" + Arrays.deepToString(response.getAllHeaders()));
        LOGGER.warn("result：\n" + result);
      }
    } catch (Exception exp) {
      exp.printStackTrace();
    } finally {
      try {
        if (null != inputStream) {
          inputStream.close();
        }
      } catch (Exception e) {
        LOGGER.error(e.getMessage(), e);
      }
    }
  }

  // http://yarn-web-http-address/app/v1/services/{appIdOrName}
  // test/resources/app-v1-services-app_name.json
  public Map<String, Object> getAppServices(String appIdOrName) {
    Map<String, Object> mapStatus = new HashMap<>();
    String appUrl = this.yarnWebHttpAddr + "/app/v1/services/" + appIdOrName
        + "?_=" + System.currentTimeMillis();

    InputStream inputStream = null;
    try {
      HttpResponse response = callRest(appUrl, HTTP.GET);
      if (null != response) {
        inputStream = response.getEntity().getContent();
        String result = new BufferedReader(new InputStreamReader(inputStream))
            .lines().collect(Collectors.joining(System.lineSeparator()));
        if (response.getStatusLine().getStatusCode() != 200 /*success*/
            && response.getStatusLine().getStatusCode() != 404 /*Not found*/) {
          LOGGER.warn("Status code " + response.getStatusLine().getStatusCode());
          LOGGER.warn("message is :" + Arrays.deepToString(response.getAllHeaders()));
          LOGGER.warn("result：\n" + result);
        }

        // parse app status json
        mapStatus = parseAppServices(result);
      }
    } catch (Exception exp) {
      exp.printStackTrace();
    } finally {
      try {
        if (null != inputStream) {
          inputStream.close();
        }
      } catch (Exception e) {
        LOGGER.error(e.getMessage(), e);
      }
    }

    return mapStatus;
  }

  // http://yarn-web-http-address/ws/v1/cluster/apps/{appId}
  // test/resources/ws-v1-cluster-apps-application_id-failed.json
  // test/resources/ws-v1-cluster-apps-application_id-finished.json
  // test/resources/ws-v1-cluster-apps-application_id-running.json
  public Map<String, Object> getClusterApps(String appId) {
    Map<String, Object> appAttempts = new HashMap<>();
    String appUrl = this.yarnWebHttpAddr + "/ws/v1/cluster/apps/" + appId
        + "?_=" + System.currentTimeMillis();

    InputStream inputStream = null;
    try {
      HttpResponse response = callRest(appUrl, HTTP.GET);
      inputStream = response.getEntity().getContent();
      String result = new BufferedReader(new InputStreamReader(inputStream))
          .lines().collect(Collectors.joining(System.lineSeparator()));
      if (response.getStatusLine().getStatusCode() != 200 /*success*/) {
        LOGGER.warn("Status code " + response.getStatusLine().getStatusCode());
        LOGGER.warn("message is :" + Arrays.deepToString(response.getAllHeaders()));
        LOGGER.warn("result：\n" + result);
      }
      // parse app status json
      appAttempts = parseClusterApps(result);

      return appAttempts;
    } catch (Exception exp) {
      exp.printStackTrace();
    } finally {
      try {
        if (null != inputStream) {
          inputStream.close();
        }
      } catch (Exception e) {
        LOGGER.error(e.getMessage(), e);
      }
    }

    return appAttempts;
  }

  public Map<String, Object> parseClusterApps(String jsonContent) {
    Map<String, Object> appAttempts = new HashMap<>();

    try {
      JsonParser jsonParser = new JsonParser();
      JsonObject jsonObject = (JsonObject) jsonParser.parse(jsonContent);

      JsonObject jsonAppAttempts = jsonObject.get("app").getAsJsonObject();
      if (null == jsonAppAttempts) {
        return appAttempts;
      }
      for (Map.Entry<String, JsonElement> entry : jsonAppAttempts.entrySet()) {
        String key = entry.getKey();
        if (null != entry.getValue() && entry.getValue() instanceof JsonPrimitive) {
          Object value = entry.getValue().getAsString();
          appAttempts.put(key, value);
        }
      }
    } catch (JsonIOException e) {
      LOGGER.error(e.getMessage(), e);
    } catch (JsonSyntaxException e) {
      LOGGER.error(e.getMessage(), e);
    }

    return appAttempts;
  }

  // http://yarn-web-http-address/ws/v1/cluster/apps/{appId}/appattempts
  // test/resources/ws-v1-cluster-apps-application_id-appattempts.json
  public List<Map<String, Object>> getAppAttempts(String appId) {
    List<Map<String, Object>> appAttempts = new ArrayList<>();
    String appUrl = this.yarnWebHttpAddr + "/ws/v1/cluster/apps/" + appId
        + "/appattempts?_=" + System.currentTimeMillis();

    InputStream inputStream = null;
    try {
      HttpResponse response = callRest(appUrl, HTTP.GET);
      inputStream = response.getEntity().getContent();
      String result = new BufferedReader(new InputStreamReader(inputStream))
          .lines().collect(Collectors.joining(System.lineSeparator()));
      if (response.getStatusLine().getStatusCode() != 200 /*success*/) {
        LOGGER.warn("Status code " + response.getStatusLine().getStatusCode());
        LOGGER.warn("message is :" + Arrays.deepToString(response.getAllHeaders()));
        LOGGER.warn("result：\n" + result);
      }

      // parse app status json
      appAttempts = parseAppAttempts(result);
    } catch (Exception exp) {
      exp.printStackTrace();
    } finally {
      try {
        if (null != inputStream) {
          inputStream.close();
        }
      } catch (Exception e) {
        LOGGER.error(e.getMessage(), e);
      }
    }

    return appAttempts;
  }

  // http://yarn-web-http-address/ws/v1/cluster/apps/{appId}/appattempts/{appAttemptId}/containers
  // test/resources/ws-v1-cluster-apps-application_id-appattempts-appattempt_id-containers.json
  public List<Map<String, Object>> getAppAttemptsContainers(String appId, String appAttemptId) {
    List<Map<String, Object>> appAttemptsContainers = new ArrayList<>();
    String appUrl = this.yarnWebHttpAddr + "/ws/v1/cluster/apps/" + appId
        + "/appattempts/" + appAttemptId + "/containers?_=" + System.currentTimeMillis();

    InputStream inputStream = null;
    try {
      HttpResponse response = callRest(appUrl, HTTP.GET);
      inputStream = response.getEntity().getContent();
      String result = new BufferedReader(new InputStreamReader(inputStream))
          .lines().collect(Collectors.joining(System.lineSeparator()));
      if (response.getStatusLine().getStatusCode() != 200 /*success*/) {
        LOGGER.warn("Status code " + response.getStatusLine().getStatusCode());
        LOGGER.warn("message is :" + Arrays.deepToString(response.getAllHeaders()));
        LOGGER.warn("result：\n" + result);
      }

      // parse app status json
      appAttemptsContainers = parseAppAttemptsContainers(result);
    } catch (Exception exp) {
      exp.printStackTrace();
    } finally {
      try {
        if (null != inputStream) {
          inputStream.close();
        }
      } catch (Exception e) {
        LOGGER.error(e.getMessage(), e);
      }
    }

    return appAttemptsContainers;
  }

  public List<Map<String, Object>> getAppAttemptsContainersExportPorts(String appId) {
    List<Map<String, Object>> listExportPorts = new ArrayList<>();

    // appId -> appAttemptId
    List<Map<String, Object>> listAppAttempts = getAppAttempts(appId);
    for (Map<String, Object> mapAppAttempts : listAppAttempts) {
      if (mapAppAttempts.containsKey(YARN_REST_APPATTEMPTID)) {
        String appAttemptId = (String) mapAppAttempts.get(YARN_REST_APPATTEMPTID);
        List<Map<String, Object>> exportPorts = getAppAttemptsContainers(appId, appAttemptId);
        if (exportPorts.size() > 0) {
          listExportPorts.addAll(exportPorts);
        }
      }
    }

    return listExportPorts;
  }

  // Kerberos authentication for simulated curling
  private static HttpClient buildSpengoHttpClient() {
    HttpClientBuilder builder = HttpClientBuilder.create();
    Lookup<AuthSchemeProvider> authSchemeRegistry
        = RegistryBuilder.<AuthSchemeProvider>create().register(
        AuthSchemes.SPNEGO, new SPNegoSchemeFactory(true)).build();
    builder.setDefaultAuthSchemeRegistry(authSchemeRegistry);
    BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
    credentialsProvider.setCredentials(new AuthScope(null, -1, null), new Credentials() {
      @Override
      public Principal getUserPrincipal() {
        return null;
      }

      @Override
      public String getPassword() {
        return null;
      }
    });
    builder.setDefaultCredentialsProvider(credentialsProvider);

    // Avoid output WARN: Cookie rejected
    RequestConfig globalConfig = RequestConfig.custom().setCookieSpec(CookieSpecs.IGNORE_COOKIES)
        .build();
    builder.setDefaultRequestConfig(globalConfig);

    CloseableHttpClient httpClient = builder.build();

    return httpClient;
  }

  public HttpResponse callSecurityRest(final String url, HTTP operation) {
    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug(String.format("Calling YarnClient %s %s %s",
          this.principal, this.keytab, url));
    }
    javax.security.auth.login.Configuration config = new javax.security.auth.login.Configuration() {
      @SuppressWarnings("serial")
      @Override
      public AppConfigurationEntry[] getAppConfigurationEntry(String name) {
        return new AppConfigurationEntry[]{new AppConfigurationEntry(
            "com.sun.security.auth.module.Krb5LoginModule",
            AppConfigurationEntry.LoginModuleControlFlag.REQUIRED,
            new HashMap<String, Object>() {
              {
                put("useTicketCache", "false");
                put("useKeyTab", "true");
                put("keyTab", keytab);
                // Krb5 in GSS API needs to be refreshed so it does not throw the error
                // Specified version of key is not available
                put("refreshKrb5Config", "true");
                put("principal", principal);
                put("storeKey", "true");
                put("doNotPrompt", "true");
                put("isInitiator", "true");
                if (LOGGER.isDebugEnabled()) {
                  put("debug", "true");
                }
              }
            })};
      }
    };

    Set<Principal> principals = new HashSet<Principal>(1);
    principals.add(new KerberosPrincipal(this.principal));
    Subject sub = new Subject(false, principals, new HashSet<Object>(), new HashSet<Object>());
    try {
      // Authentication module: Krb5Login
      LoginContext loginContext = new LoginContext("Krb5Login", sub, null, config);
      loginContext.login();
      Subject serviceSubject = loginContext.getSubject();
      return Subject.doAs(serviceSubject, new PrivilegedAction<HttpResponse>() {
        HttpResponse httpResponse = null;

        @Override
        public HttpResponse run() {
          try {
            HttpUriRequest request = null;
            switch (operation) {
              case DELETE:
                request = new HttpDelete(url);
                break;
              case POST:
                request = new HttpPost(url);
                break;
              default:
                request = new HttpGet(url);
                break;
            }

            HttpClient spengoClient = buildSpengoHttpClient();
            httpResponse = spengoClient.execute(request);
            return httpResponse;
          } catch (IOException e) {
            LOGGER.error(e.getMessage(), e);
          }
          return httpResponse;
        }
      });
    } catch (Exception e) {
      LOGGER.error(e.getMessage(), e);
    }
    return null;
  }

  private Map<String, Object> parseAppServices(String appJson) {
    Map<String, Object> mapStatus = new HashMap<>();

    try {
      JsonParser jsonParser = new JsonParser();
      JsonObject jsonObject = (JsonObject) jsonParser.parse(appJson);

      JsonElement elementAppId = jsonObject.get("id");
      JsonElement elementAppState = jsonObject.get("state");
      JsonElement elementAppName = jsonObject.get("name");

      String appId = (elementAppId == null) ? "" : elementAppId.getAsString();
      String appState = (elementAppState == null) ? "" : elementAppState.getAsString();
      String appName = (elementAppName == null) ? "" : elementAppName.getAsString();

      if (!StringUtils.isEmpty(appId)) {
        mapStatus.put(YarnConstants.YARN_APPLICATION_ID, appId);
      }
      if (!StringUtils.isEmpty(appName)) {
        mapStatus.put(YarnConstants.YARN_APPLICATION_NAME, appName);
      }
      if (!StringUtils.isEmpty(appState)) {
        mapStatus.put(YarnConstants.YARN_APPLICATION_STATUS, appState);
      }
    } catch (JsonIOException e) {
      LOGGER.error(e.getMessage(), e);
    } catch (JsonSyntaxException e) {
      LOGGER.error(e.getMessage(), e);
    }

    return mapStatus;
  }

  // appJson format : submarine/src/test/resources/appAttempts.json
  public List<Map<String, Object>> parseAppAttempts(String jsonContent) {
    List<Map<String, Object>> appAttempts = new ArrayList<>();

    try {
      JsonParser jsonParser = new JsonParser();
      JsonObject jsonObject = (JsonObject) jsonParser.parse(jsonContent);

      JsonObject jsonAppAttempts = jsonObject.get(YARN_REST_APPATTEMPTS).getAsJsonObject();
      if (null == jsonAppAttempts) {
        return appAttempts;
      }
      JsonArray jsonAppAttempt = jsonAppAttempts.get(YARN_REST_APPATTEMPT).getAsJsonArray();
      if (null == jsonAppAttempt) {
        return appAttempts;
      }
      for (int i = 0; i < jsonAppAttempt.size(); i++) {
        Map<String, Object> mapAppAttempt = new HashMap<>();

        JsonObject jsonParagraph = jsonAppAttempt.get(i).getAsJsonObject();

        JsonElement jsonElement = jsonParagraph.get("id");
        String id = (jsonElement == null) ? "" : jsonElement.getAsString();
        mapAppAttempt.put("id", id);

        jsonElement = jsonParagraph.get(YARN_REST_APPATTEMPTID);
        String appAttemptId = (jsonElement == null) ? "" : jsonElement.getAsString();
        mapAppAttempt.put(YARN_REST_APPATTEMPTID, appAttemptId);

        appAttempts.add(mapAppAttempt);
      }
    } catch (JsonIOException e) {
      LOGGER.error(e.getMessage(), e);
    } catch (JsonSyntaxException e) {
      LOGGER.error(e.getMessage(), e);
    }

    return appAttempts;
  }

  // appJson format : submarine/src/test/resources/appAttempts.json
  public List<Map<String, Object>> parseAppAttemptsContainers(String jsonContent) {
    List<Map<String, Object>> appContainers = new ArrayList<>();

    try {
      JsonParser jsonParser = new JsonParser();
      JsonObject jsonObject = (JsonObject) jsonParser.parse(jsonContent);

      JsonArray jsonContainers = jsonObject.get(YARN_REST_CONTAINER).getAsJsonArray();
      for (int i = 0; i < jsonContainers.size(); i++) {
        String hostIp = "";

        JsonObject jsonContainer = jsonContainers.get(i).getAsJsonObject();

        JsonElement jsonElement = jsonContainer.get("nodeId");
        String nodeId = (jsonElement == null) ? "" : jsonElement.getAsString();
        String[] nodeIdParts = nodeId.split(":");
        if (nodeIdParts.length == 2) {
          hostIp = nodeIdParts[0];
        }

        jsonElement = jsonContainer.get("exposedPorts");
        String exposedPorts = (jsonElement == null) ? "" : jsonElement.getAsString();

        Gson gson = new Gson();
        Map<String, List<Map<String, String>>> listExposedPorts = gson.fromJson(exposedPorts,
            new TypeToken<Map<String, List<Map<String, String>>>>() {
            }.getType());
        if (null == listExposedPorts) {
          continue;
        }
        for (Map.Entry<String, List<Map<String, String>>> entry : listExposedPorts.entrySet()) {
          String containerPort = entry.getKey();
          String[] containerPortParts = containerPort.split("/");
          if (containerPortParts.length == 2) {
            List<Map<String, String>> hostIps = entry.getValue();
            for (Map<String, String> hostAttrib : hostIps) {
              Map<String, Object> containerExposedPort = new HashMap<>();
              String hostPort = hostAttrib.get("HostPort");
              containerExposedPort.put(HOST_IP, hostIp);
              containerExposedPort.put(HOST_PORT, hostPort);
              containerExposedPort.put(CONTAINER_PORT, containerPortParts[0]);
              appContainers.add(containerExposedPort);
            }
          }
        }
      }
    } catch (JsonIOException e) {
      LOGGER.error(e.getMessage(), e);
    } catch (JsonSyntaxException e) {
      LOGGER.error(e.getMessage(), e);
    }

    return appContainers;
  }

  public List<Map<String, Object>> getAppExportPorts(String name) {
    // Query the IP and port of the submarine interpreter process through the yarn client
    Map<String, Object> mapAppStatus = getAppServices(name);
    if (mapAppStatus.containsKey(YarnConstants.YARN_APPLICATION_ID)
        && mapAppStatus.containsKey(YarnConstants.YARN_APPLICATION_NAME)
        && mapAppStatus.containsKey(YarnConstants.YARN_APPLICATION_STATUS)) {
      String appId = mapAppStatus.get(YarnConstants.YARN_APPLICATION_ID).toString();
      String appStatus = mapAppStatus.get(YarnConstants.YARN_APPLICATION_STATUS).toString();

      // if (StringUtils.equals(appStatus, SubmarineJob.YarnApplicationState.RUNNING.toString())) {
      List<Map<String, Object>> mapAppAttempts = getAppAttemptsContainersExportPorts(appId);
      return mapAppAttempts;
      //}
    }

    return new ArrayList<Map<String, Object>>() {
    };
  }

  public Map<String, String> getAppExportPorts(String name, String port) {
    // Query the IP and port of the submarine interpreter process through the yarn client
    Map<String, Object> mapAppStatus = getAppServices(name);
    if (mapAppStatus.containsKey(YarnConstants.YARN_APPLICATION_ID)
        && mapAppStatus.containsKey(YarnConstants.YARN_APPLICATION_NAME)
        && mapAppStatus.containsKey(YarnConstants.YARN_APPLICATION_STATUS)) {
      String appId = mapAppStatus.get(YarnConstants.YARN_APPLICATION_ID).toString();
      String appStatus = mapAppStatus.get(YarnConstants.YARN_APPLICATION_STATUS).toString();

      List<Map<String, Object>> mapAppAttempts = getAppAttemptsContainersExportPorts(appId);
      boolean findExistIntpContainer = false;
      for (Map<String, Object> exportPorts : mapAppAttempts) {
        if (exportPorts.containsKey(YarnClient.HOST_IP) && exportPorts.containsKey(YarnClient.HOST_PORT)
            && exportPorts.containsKey(YarnClient.CONTAINER_PORT)) {
          String intpAppHostIp = (String) exportPorts.get(YarnClient.HOST_IP);
          String intpAppHostPort = (String) exportPorts.get(YarnClient.HOST_PORT);
          String intpAppContainerPort = (String) exportPorts.get(YarnClient.CONTAINER_PORT);
          if (StringUtils.equals(port, intpAppContainerPort)) {
            LOGGER.info("Detection Submarine interpreter Container hostIp:{}, hostPort:{}, containerPort:{}.",
                intpAppHostIp, intpAppHostPort, intpAppContainerPort);

            Map<String, String> exportPortsAttr = new HashMap<>();
            exportPortsAttr.put(HOST_IP, intpAppHostIp);
            exportPortsAttr.put(HOST_PORT, intpAppHostPort);
            exportPortsAttr.put(CONTAINER_PORT, intpAppContainerPort);

            return exportPortsAttr;
          }
        }
      }
    }

    return new HashMap<String, String>();
  }

  public enum HTTP {
    GET,
    POST,
    DELETE;
  }
}
