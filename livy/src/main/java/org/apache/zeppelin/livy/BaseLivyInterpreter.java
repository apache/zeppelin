/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.zeppelin.livy;

import java.io.FileInputStream;
import java.io.IOException;
import java.security.KeyStore;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.SSLContext;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.http.auth.AuthSchemeProvider;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.AuthSchemes;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLContexts;
import org.apache.http.impl.auth.SPNegoSchemeFactory;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResultMessage;
import org.apache.zeppelin.interpreter.InterpreterUtils;
import org.apache.zeppelin.interpreter.thrift.InterpreterCompletion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.security.kerberos.client.KerberosRestTemplate;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;


/**
 * Base class for livy interpreters.
 */
public abstract class BaseLivyInterpreter extends Interpreter {

  protected static final Logger LOGGER = LoggerFactory.getLogger(BaseLivyInterpreter.class);
  private static Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
  private static String SESSION_NOT_FOUND_PATTERN = "\"Session '\\d+' not found.\"";

  protected volatile SessionInfo sessionInfo;
  private String livyURL;
  private int sessionCreationTimeout;
  private int pullStatusInterval;
  protected boolean displayAppInfo;
  protected LivyVersion livyVersion;
  private RestTemplate restTemplate;
  private Map<String, String> customHeaders = new HashMap<>();

  Set<Object> paragraphsToCancel = Collections.newSetFromMap(
      new ConcurrentHashMap<Object, Boolean>());
  private ConcurrentHashMap<String, Integer> paragraphId2StmtProgressMap =
      new ConcurrentHashMap<>();

  public BaseLivyInterpreter(Properties property) {
    super(property);
    this.livyURL = property.getProperty("zeppelin.livy.url");
    this.displayAppInfo = Boolean.parseBoolean(
        property.getProperty("zeppelin.livy.displayAppInfo", "true"));
    this.sessionCreationTimeout = Integer.parseInt(
        property.getProperty("zeppelin.livy.session.create_timeout", 120 + ""));
    this.pullStatusInterval = Integer.parseInt(
        property.getProperty("zeppelin.livy.pull_status.interval.millis", 1000 + ""));
    this.restTemplate = createRestTemplate();
    if (!StringUtils.isBlank(property.getProperty("zeppelin.livy.http.headers"))) {
      String[] headers = property.getProperty("zeppelin.livy.http.headers").split(";");
      for (String header : headers) {
        String[] splits = header.split(":", -1);
        if (splits.length != 2) {
          throw new RuntimeException("Invalid format of http headers: " + header +
              ", valid http header format is HEADER_NAME:HEADER_VALUE");
        }
        customHeaders.put(splits[0].trim(), envSubstitute(splits[1].trim()));
      }
    }
  }

  private String envSubstitute(String value) {
    String newValue = new String(value);
    Pattern pattern = Pattern.compile("\\$\\{(.*)\\}");
    Matcher matcher = pattern.matcher(value);
    while (matcher.find()) {
      String env = matcher.group(1);
      newValue = newValue.replace("${" + env + "}", System.getenv(env));
    }
    return newValue;
  }

  // only for testing
  Map<String, String> getCustomHeaders() {
    return customHeaders;
  }

  public abstract String getSessionKind();

  @Override
  public void open() throws InterpreterException {
    try {
      initLivySession();
    } catch (LivyException e) {
      String msg = "Fail to create session, please check livy interpreter log and " +
          "livy server log";
      throw new RuntimeException(msg, e);
    }
  }

  @Override
  public void close() {
    if (sessionInfo != null) {
      closeSession(sessionInfo.id);
      // reset sessionInfo to null so that we won't close it twice.
      sessionInfo = null;
    }
  }

  protected void initLivySession() throws LivyException {
    this.sessionInfo = createSession(getUserName(), getSessionKind());
    if (displayAppInfo) {
      if (sessionInfo.appId == null) {
        // livy 0.2 don't return appId and sparkUiUrl in response so that we need to get it
        // explicitly by ourselves.
        sessionInfo.appId = extractAppId();
      }

      if (sessionInfo.appInfo == null ||
          StringUtils.isEmpty(sessionInfo.appInfo.get("sparkUiUrl"))) {
        sessionInfo.webUIAddress = extractWebUIAddress();
      } else {
        sessionInfo.webUIAddress = sessionInfo.appInfo.get("sparkUiUrl");
      }
      LOGGER.info("Create livy session successfully with sessionId: {}, appId: {}, webUI: {}",
          sessionInfo.id, sessionInfo.appId, sessionInfo.webUIAddress);
    } else {
      LOGGER.info("Create livy session successfully with sessionId: {}", this.sessionInfo.id);
    }
    // check livy version
    try {
      this.livyVersion = getLivyVersion();
      LOGGER.info("Use livy " + livyVersion);
    } catch (APINotFoundException e) {
      this.livyVersion = new LivyVersion("0.2.0");
      LOGGER.info("Use livy 0.2.0");
    }
  }

  protected abstract String extractAppId() throws LivyException;

  protected abstract String extractWebUIAddress() throws LivyException;

  public SessionInfo getSessionInfo() {
    return sessionInfo;
  }

  @Override
  public InterpreterResult interpret(String st, InterpreterContext context) {
    if (StringUtils.isEmpty(st)) {
      return new InterpreterResult(InterpreterResult.Code.SUCCESS, "");
    }

    try {
      return interpret(st, context.getParagraphId(), this.displayAppInfo, true);
    } catch (LivyException e) {
      LOGGER.error("Fail to interpret:" + st, e);
      return new InterpreterResult(InterpreterResult.Code.ERROR,
          InterpreterUtils.getMostRelevantMessage(e));
    }
  }

  @Override
  public List<InterpreterCompletion> completion(String buf, int cursor,
      InterpreterContext interpreterContext) {
    List<InterpreterCompletion> candidates = Collections.emptyList();
    try {
      candidates = callCompletion(new CompletionRequest(buf, getSessionKind(), cursor));
    } catch (SessionNotFoundException e) {
      LOGGER.warn("Livy session {} is expired. Will return empty list of candidates.",
          sessionInfo.id);
    } catch (LivyException le) {
      logger.error("Failed to call code completions. Will return empty list of candidates", le);
    }
    return candidates;
  }

  private List<InterpreterCompletion> callCompletion(CompletionRequest req) throws LivyException {
    List<InterpreterCompletion> candidates = new ArrayList<>();
    try {
      CompletionResponse resp = CompletionResponse.fromJson(
          callRestAPI("/sessions/" + sessionInfo.id + "/completion", "POST", req.toJson()));
      for (String candidate : resp.candidates) {
        candidates.add(new InterpreterCompletion(candidate, candidate, StringUtils.EMPTY));
      }
    } catch (APINotFoundException e) {
      logger.debug("completion api seems not to be available. (available from livy 0.5)", e);
    }
    return candidates;
  }

  @Override
  public void cancel(InterpreterContext context) {
    paragraphsToCancel.add(context.getParagraphId());
    LOGGER.info("Added paragraph " + context.getParagraphId() + " for cancellation.");
  }

  @Override
  public FormType getFormType() {
    return FormType.NATIVE;
  }

  @Override
  public int getProgress(InterpreterContext context) {
    if (livyVersion.isGetProgressSupported()) {
      String paraId = context.getParagraphId();
      Integer progress = paragraphId2StmtProgressMap.get(paraId);
      return progress == null ? 0 : progress;
    }
    return 0;
  }

  private SessionInfo createSession(String user, String kind)
      throws LivyException {
    try {
      Map<String, String> conf = new HashMap<>();
      for (Map.Entry<Object, Object> entry : getProperties().entrySet()) {
        if (entry.getKey().toString().startsWith("livy.spark.") &&
            !entry.getValue().toString().isEmpty())
          conf.put(entry.getKey().toString().substring(5), entry.getValue().toString());
      }

      CreateSessionRequest request = new CreateSessionRequest(kind,
          user == null || user.equals("anonymous") ? null : user, conf);
      SessionInfo sessionInfo = SessionInfo.fromJson(
          callRestAPI("/sessions", "POST", request.toJson()));
      long start = System.currentTimeMillis();
      // pull the session status until it is idle or timeout
      while (!sessionInfo.isReady()) {
        if ((System.currentTimeMillis() - start) / 1000 > sessionCreationTimeout) {
          String msg = "The creation of session " + sessionInfo.id + " is timeout within "
              + sessionCreationTimeout + " seconds, appId: " + sessionInfo.appId
              + ", log: " + sessionInfo.log;
          throw new LivyException(msg);
        }
        Thread.sleep(pullStatusInterval);
        sessionInfo = getSessionInfo(sessionInfo.id);
        LOGGER.info("Session {} is in state {}, appId {}", sessionInfo.id, sessionInfo.state,
            sessionInfo.appId);
        if (sessionInfo.isFinished()) {
          String msg = "Session " + sessionInfo.id + " is finished, appId: " + sessionInfo.appId
              + ", log: " + sessionInfo.log;
          throw new LivyException(msg);
        }
      }
      return sessionInfo;
    } catch (Exception e) {
      LOGGER.error("Error when creating livy session for user " + user, e);
      throw new LivyException(e);
    }
  }

  private SessionInfo getSessionInfo(int sessionId) throws LivyException {
    return SessionInfo.fromJson(callRestAPI("/sessions/" + sessionId, "GET"));
  }

  public InterpreterResult interpret(String code,
                                     String paragraphId,
                                     boolean displayAppInfo,
                                     boolean appendSessionExpired) throws LivyException {
    StatementInfo stmtInfo = null;
    boolean sessionExpired = false;
    try {
      try {
        stmtInfo = executeStatement(new ExecuteRequest(code));
      } catch (SessionNotFoundException e) {
        LOGGER.warn("Livy session {} is expired, new session will be created.", sessionInfo.id);
        sessionExpired = true;
        // we don't want to create multiple sessions because it is possible to have multiple thread
        // to call this method, like LivySparkSQLInterpreter which use ParallelScheduler. So we need
        // to check session status again in this sync block
        synchronized (this) {
          if (isSessionExpired()) {
            initLivySession();
          }
        }
        stmtInfo = executeStatement(new ExecuteRequest(code));
      }

      // pull the statement status
      while (!stmtInfo.isAvailable()) {
        if (paragraphId != null && paragraphsToCancel.contains(paragraphId)) {
          cancel(stmtInfo.id, paragraphId);
          return new InterpreterResult(InterpreterResult.Code.ERROR, "Job is cancelled");
        }
        try {
          Thread.sleep(pullStatusInterval);
        } catch (InterruptedException e) {
          LOGGER.error("InterruptedException when pulling statement status.", e);
          throw new LivyException(e);
        }
        stmtInfo = getStatementInfo(stmtInfo.id);
        if (paragraphId != null) {
          paragraphId2StmtProgressMap.put(paragraphId, (int) (stmtInfo.progress * 100));
        }
      }
      if (appendSessionExpired) {
        return appendSessionExpire(getResultFromStatementInfo(stmtInfo, displayAppInfo),
            sessionExpired);
      } else {
        return getResultFromStatementInfo(stmtInfo, displayAppInfo);
      }
    } finally {
      if (paragraphId != null) {
        paragraphId2StmtProgressMap.remove(paragraphId);
        paragraphsToCancel.remove(paragraphId);
      }
    }
  }

  private void cancel(int id, String paragraphId) {
    if (livyVersion.isCancelSupported()) {
      try {
        LOGGER.info("Cancelling statement " + id);
        cancelStatement(id);
      } catch (LivyException e) {
        LOGGER.error("Fail to cancel statement " + id + " for paragraph " + paragraphId, e);
      }
      finally {
        paragraphsToCancel.remove(paragraphId);
      }
    } else {
      LOGGER.warn("cancel is not supported for this version of livy: " + livyVersion);
      paragraphsToCancel.clear();
    }
  }

  protected LivyVersion getLivyVersion() throws LivyException {
    return new LivyVersion((LivyVersionResponse.fromJson(callRestAPI("/version", "GET")).version));
  }

  private boolean isSessionExpired() throws LivyException {
    try {
      getSessionInfo(sessionInfo.id);
      return false;
    } catch (SessionNotFoundException e) {
      return true;
    } catch (LivyException e) {
      throw e;
    }
  }

  private InterpreterResult appendSessionExpire(InterpreterResult result, boolean sessionExpired) {
    if (sessionExpired) {
      InterpreterResult result2 = new InterpreterResult(result.code());
      result2.add(InterpreterResult.Type.HTML,
          "<font color=\"red\">Previous livy session is expired, new livy session is created. " +
              "Paragraphs that depend on this paragraph need to be re-executed!</font>");
      for (InterpreterResultMessage message : result.message()) {
        result2.add(message.getType(), message.getData());
      }
      return result2;
    } else {
      return result;
    }
  }


  private InterpreterResult getResultFromStatementInfo(StatementInfo stmtInfo,
                                                       boolean displayAppInfo) {
    if (stmtInfo.output != null && stmtInfo.output.isError()) {
      InterpreterResult result = new InterpreterResult(InterpreterResult.Code.ERROR);
      StringBuilder sb = new StringBuilder();
      sb.append(stmtInfo.output.evalue);
      // in case evalue doesn't have newline char
      if (!stmtInfo.output.evalue.contains("\n"))
        sb.append("\n");
      if (stmtInfo.output.traceback != null) {
        sb.append(StringUtils.join(stmtInfo.output.traceback));
      }
      result.add(sb.toString());
      return result;
    } else if (stmtInfo.isCancelled()) {
      // corner case, output might be null if it is cancelled.
      return new InterpreterResult(InterpreterResult.Code.ERROR, "Job is cancelled");
    } else if (stmtInfo.output == null) {
      // This case should never happen, just in case
      return new InterpreterResult(InterpreterResult.Code.ERROR, "Empty output");
    } else {
      //TODO(zjffdu) support other types of data (like json, image and etc)
      String result = stmtInfo.output.data.plain_text;

      // check table magic result first
      if (stmtInfo.output.data.application_livy_table_json != null) {
        StringBuilder outputBuilder = new StringBuilder();
        boolean notFirstColumn = false;

        for (Map header : stmtInfo.output.data.application_livy_table_json.headers) {
          if (notFirstColumn) {
            outputBuilder.append("\t");
          }
          outputBuilder.append(header.get("name"));
          notFirstColumn = true;
        }

        outputBuilder.append("\n");
        for (List<Object> row : stmtInfo.output.data.application_livy_table_json.records) {
          outputBuilder.append(StringUtils.join(row, "\t"));
          outputBuilder.append("\n");
        }
        return new InterpreterResult(InterpreterResult.Code.SUCCESS,
            InterpreterResult.Type.TABLE, outputBuilder.toString());
      } else if (stmtInfo.output.data.image_png != null) {
        return new InterpreterResult(InterpreterResult.Code.SUCCESS,
            InterpreterResult.Type.IMG, (String) stmtInfo.output.data.image_png);
      } else if (result != null) {
        result = result.trim();
        if (result.startsWith("<link")
            || result.startsWith("<script")
            || result.startsWith("<style")
            || result.startsWith("<div")) {
          result = "%html " + result;
        }
      }

      if (displayAppInfo) {
        InterpreterResult interpreterResult = new InterpreterResult(InterpreterResult.Code.SUCCESS);
        interpreterResult.add(result);
        String appInfoHtml = "<hr/>Spark Application Id: " + sessionInfo.appId + "<br/>"
            + "Spark WebUI: <a href=\"" + sessionInfo.webUIAddress + "\">"
            + sessionInfo.webUIAddress + "</a>";
        interpreterResult.add(InterpreterResult.Type.HTML, appInfoHtml);
        return interpreterResult;
      } else {
        return new InterpreterResult(InterpreterResult.Code.SUCCESS, result);
      }
    }
  }

  private StatementInfo executeStatement(ExecuteRequest executeRequest)
      throws LivyException {
    return StatementInfo.fromJson(callRestAPI("/sessions/" + sessionInfo.id + "/statements", "POST",
        executeRequest.toJson()));
  }

  private StatementInfo getStatementInfo(int statementId)
      throws LivyException {
    return StatementInfo.fromJson(
        callRestAPI("/sessions/" + sessionInfo.id + "/statements/" + statementId, "GET"));
  }

  private void cancelStatement(int statementId) throws LivyException {
    callRestAPI("/sessions/" + sessionInfo.id + "/statements/" + statementId + "/cancel", "POST");
  }


  private RestTemplate createRestTemplate() {
    String keytabLocation = getProperty("zeppelin.livy.keytab");
    String principal = getProperty("zeppelin.livy.principal");
    boolean isSpnegoEnabled = StringUtils.isNotEmpty(keytabLocation) &&
        StringUtils.isNotEmpty(principal);

    HttpClient httpClient = null;
    if (livyURL.startsWith("https:")) {
      String keystoreFile = getProperty("zeppelin.livy.ssl.trustStore");
      String password = getProperty("zeppelin.livy.ssl.trustStorePassword");
      if (StringUtils.isBlank(keystoreFile)) {
        throw new RuntimeException("No zeppelin.livy.ssl.trustStore specified for livy ssl");
      }
      if (StringUtils.isBlank(password)) {
        throw new RuntimeException("No zeppelin.livy.ssl.trustStorePassword specified " +
            "for livy ssl");
      }
      FileInputStream inputStream = null;
      try {
        inputStream = new FileInputStream(keystoreFile);
        KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
        trustStore.load(new FileInputStream(keystoreFile), password.toCharArray());
        SSLContext sslContext = SSLContexts.custom()
            .loadTrustMaterial(trustStore)
            .build();
        SSLConnectionSocketFactory csf = new SSLConnectionSocketFactory(sslContext);
        HttpClientBuilder httpClientBuilder = HttpClients.custom().setSSLSocketFactory(csf);
        RequestConfig reqConfig = new RequestConfig() {
          @Override
          public boolean isAuthenticationEnabled() {
            return true;
          }
        };
        httpClientBuilder.setDefaultRequestConfig(reqConfig);
        Credentials credentials = new Credentials() {
          @Override
          public String getPassword() {
            return null;
          }

          @Override
          public Principal getUserPrincipal() {
            return null;
          }
        };
        CredentialsProvider credsProvider = new BasicCredentialsProvider();
        credsProvider.setCredentials(AuthScope.ANY, credentials);
        httpClientBuilder.setDefaultCredentialsProvider(credsProvider);
        if (isSpnegoEnabled) {
          Registry<AuthSchemeProvider> authSchemeProviderRegistry =
              RegistryBuilder.<AuthSchemeProvider>create()
                  .register(AuthSchemes.SPNEGO, new SPNegoSchemeFactory())
                  .build();
          httpClientBuilder.setDefaultAuthSchemeRegistry(authSchemeProviderRegistry);
        }

        httpClient = httpClientBuilder.build();
      } catch (Exception e) {
        throw new RuntimeException("Failed to create SSL HttpClient", e);
      } finally {
        if (inputStream != null) {
          try {
            inputStream.close();
          } catch (IOException e) {
            LOGGER.error("Failed to close keystore file", e);
          }
        }
      }
    }


    if (isSpnegoEnabled) {
      if (httpClient == null) {
        return new KerberosRestTemplate(keytabLocation, principal);
      } else {
        return new KerberosRestTemplate(keytabLocation, principal, httpClient);
      }
    }
    if (httpClient == null) {
      return new RestTemplate();
    } else {
      return new RestTemplate(new HttpComponentsClientHttpRequestFactory(httpClient));
    }
  }

  private String callRestAPI(String targetURL, String method) throws LivyException {
    return callRestAPI(targetURL, method, "");
  }

  private String callRestAPI(String targetURL, String method, String jsonData)
      throws LivyException {
    targetURL = livyURL + targetURL;
    LOGGER.debug("Call rest api in {}, method: {}, jsonData: {}", targetURL, method, jsonData);
    HttpHeaders headers = new HttpHeaders();
    headers.add("Content-Type", MediaType.APPLICATION_JSON_UTF8_VALUE);
    headers.add("X-Requested-By", "zeppelin");
    for (Map.Entry<String, String> entry : customHeaders.entrySet()) {
      headers.add(entry.getKey(), entry.getValue());
    }
    ResponseEntity<String> response = null;
    try {
      if (method.equals("POST")) {
        HttpEntity<String> entity = new HttpEntity<>(jsonData, headers);
        response = restTemplate.exchange(targetURL, HttpMethod.POST, entity, String.class);
      } else if (method.equals("GET")) {
        HttpEntity<String> entity = new HttpEntity<>(headers);
        response = restTemplate.exchange(targetURL, HttpMethod.GET, entity, String.class);
      } else if (method.equals("DELETE")) {
        HttpEntity<String> entity = new HttpEntity<>(headers);
        response = restTemplate.exchange(targetURL, HttpMethod.DELETE, entity, String.class);
      }
    } catch (HttpClientErrorException e) {
      response = new ResponseEntity(e.getResponseBodyAsString(), e.getStatusCode());
      LOGGER.error(String.format("Error with %s StatusCode: %s",
          response.getStatusCode().value(), e.getResponseBodyAsString()));
    } catch (RestClientException e) {
      // Exception happens when kerberos is enabled.
      if (e.getCause() instanceof HttpClientErrorException) {
        HttpClientErrorException cause = (HttpClientErrorException) e.getCause();
        if (cause.getResponseBodyAsString().matches(SESSION_NOT_FOUND_PATTERN)) {
          throw new SessionNotFoundException(cause.getResponseBodyAsString());
        }
        throw new LivyException(cause.getResponseBodyAsString() + "\n"
            + ExceptionUtils.getFullStackTrace(ExceptionUtils.getRootCause(e)));
      }
      if (e instanceof HttpServerErrorException) {
        HttpServerErrorException errorException = (HttpServerErrorException) e;
        String errorResponse = errorException.getResponseBodyAsString();
        if (errorResponse.contains("Session is in state dead")) {
          throw new LivyException("%html <font color=\"red\">Livy session is dead somehow, " +
              "please check log to see why it is dead, and then restart livy interpreter</font>");
        }
        throw new LivyException(errorResponse, e);
      }
      throw new LivyException(e);
    }
    if (response == null) {
      throw new LivyException("No http response returned");
    }
    LOGGER.debug("Get response, StatusCode: {}, responseBody: {}", response.getStatusCode(),
        response.getBody());
    if (response.getStatusCode().value() == 200
        || response.getStatusCode().value() == 201) {
      return response.getBody();
    } else if (response.getStatusCode().value() == 404) {
      if (response.getBody().matches(SESSION_NOT_FOUND_PATTERN)) {
        throw new SessionNotFoundException(response.getBody());
      } else {
        throw new APINotFoundException("No rest api found for " + targetURL +
            ", " + response.getStatusCode());
      }
    } else {
      String responseString = response.getBody();
      if (responseString.contains("CreateInteractiveRequest[\\\"master\\\"]")) {
        return responseString;
      }
      throw new LivyException(String.format("Error with %s StatusCode: %s",
          response.getStatusCode().value(), responseString));
    }
  }

  private void closeSession(int sessionId) {
    try {
      callRestAPI("/sessions/" + sessionId, "DELETE");
    } catch (Exception e) {
      LOGGER.error(String.format("Error closing session for user with session ID: %s",
          sessionId), e);
    }
  }

  /*
  * We create these POJO here to accommodate livy 0.3 which is not released yet. livy rest api has
  * some changes from version to version. So we create these POJO in zeppelin side to accommodate
  * incompatibility between versions. Later, when livy become more stable, we could just depend on
  * livy client jar.
  */
  private static class CreateSessionRequest {
    public final String kind;
    @SerializedName("proxyUser")
    public final String user;
    public final Map<String, String> conf;

    public CreateSessionRequest(String kind, String user, Map<String, String> conf) {
      this.kind = kind;
      this.user = user;
      this.conf = conf;
    }

    public String toJson() {
      return gson.toJson(this);
    }
  }

  /**
   *
   */
  public static class SessionInfo {

    public final int id;
    public String appId;
    public String webUIAddress;
    public final String owner;
    public final String proxyUser;
    public final String state;
    public final String kind;
    public final Map<String, String> appInfo;
    public final List<String> log;

    public SessionInfo(int id, String appId, String owner, String proxyUser, String state,
                       String kind, Map<String, String> appInfo, List<String> log) {
      this.id = id;
      this.appId = appId;
      this.owner = owner;
      this.proxyUser = proxyUser;
      this.state = state;
      this.kind = kind;
      this.appInfo = appInfo;
      this.log = log;
    }

    public boolean isReady() {
      return state.equals("idle");
    }

    public boolean isFinished() {
      return state.equals("error") || state.equals("dead") || state.equals("success");
    }

    public static SessionInfo fromJson(String json) {
      return gson.fromJson(json, SessionInfo.class);
    }
  }

  private static class ExecuteRequest {
    public final String code;

    public ExecuteRequest(String code) {
      this.code = code;
    }

    public String toJson() {
      return gson.toJson(this);
    }
  }

  private static class StatementInfo {
    public Integer id;
    public String state;
    public double progress;
    public StatementOutput output;

    public StatementInfo() {
    }

    public static StatementInfo fromJson(String json) {
      String right_json = "";
      try {
        gson.fromJson(json, StatementInfo.class);
        right_json = json;
      } catch (Exception e) {
        if (json.contains("\"traceback\":{}")) {
          LOGGER.debug("traceback type mismatch, replacing the mismatching part ");
          right_json = json.replace("\"traceback\":{}", "\"traceback\":[]");
          LOGGER.debug("new json string is {}", right_json);
        }
      }
      return gson.fromJson(right_json, StatementInfo.class);
    }

    public boolean isAvailable() {
      return state.equals("available") || state.equals("cancelled");
    }

    public boolean isCancelled() {
      return state.equals("cancelled");
    }

    private static class StatementOutput {
      public String status;
      public String execution_count;
      public Data data;
      public String ename;
      public String evalue;
      public String[] traceback;
      public TableMagic tableMagic;

      public boolean isError() {
        return status.equals("error");
      }

      public String toJson() {
        return gson.toJson(this);
      }

      private static class Data {
        @SerializedName("text/plain")
        public String plain_text;
        @SerializedName("image/png")
        public String image_png;
        @SerializedName("application/json")
        public String application_json;
        @SerializedName("application/vnd.livy.table.v1+json")
        public TableMagic application_livy_table_json;
      }

      private static class TableMagic {
        @SerializedName("headers")
        List<Map> headers;

        @SerializedName("data")
        List<List> records;
      }
    }
  }

  static class CompletionRequest {
    public final String code;
    public final String kind;
    public final int cursor;

    public CompletionRequest(String code, String kind, int cursor) {
      this.code = code;
      this.kind = kind;
      this.cursor = cursor;
    }

    public String toJson() {
      return gson.toJson(this);
    }
  }

  static class CompletionResponse {
    public final String[] candidates;

    public CompletionResponse(String[] candidates) {
      this.candidates = candidates;
    }

    public static CompletionResponse fromJson(String json) {
      return gson.fromJson(json, CompletionResponse.class);
    }
  }

  private static class LivyVersionResponse {
    public String url;
    public String branch;
    public String revision;
    public String version;
    public String date;
    public String user;

    public static LivyVersionResponse fromJson(String json) {
      return gson.fromJson(json, LivyVersionResponse.class);
    }
  }

}
