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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import org.apache.commons.lang.StringUtils;
import org.apache.zeppelin.interpreter.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.kerberos.client.KerberosRestTemplate;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Base class for livy interpreters.
 */
public abstract class BaseLivyInterprereter extends Interpreter {

  protected static final Logger LOGGER = LoggerFactory.getLogger(BaseLivyInterprereter.class);
  private static Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

  protected volatile SessionInfo sessionInfo;
  private String livyURL;
  private int sessionCreationTimeout;
  private int pullStatusInterval;
  protected boolean displayAppInfo;
  private AtomicBoolean sessionExpired = new AtomicBoolean(false);
  private LivyVersion livyVersion;

  // keep tracking the mapping between paragraphId and statementId, so that we can cancel the
  // statement after we execute it.
  private ConcurrentHashMap<String, Integer> paragraphId2StmtIdMapping = new ConcurrentHashMap<>();

  public BaseLivyInterprereter(Properties property) {
    super(property);
    this.livyURL = property.getProperty("zeppelin.livy.url");
    this.displayAppInfo = Boolean.parseBoolean(
        property.getProperty("zeppelin.livy.displayAppInfo", "false"));
    this.sessionCreationTimeout = Integer.parseInt(
        property.getProperty("zeppelin.livy.session.create_timeout", 120 + ""));
    this.pullStatusInterval = Integer.parseInt(
        property.getProperty("zeppelin.livy.pull_status.interval.millis", 1000 + ""));
  }

  public abstract String getSessionKind();

  @Override
  public void open() {
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
        sessionInfo.appId = extractStatementResult(
            interpret("sc.applicationId", null, false, false).message()
                .get(0).getData());
      }

      if (sessionInfo.appInfo == null ||
          StringUtils.isEmpty(sessionInfo.appInfo.get("sparkUiUrl"))) {
        interpret(
            "val webui=sc.getClass.getMethod(\"ui\").invoke(sc).asInstanceOf[Some[_]].get",
            null, false, false);
        sessionInfo.webUIAddress = extractStatementResult(
            interpret(
                "webui.getClass.getMethod(\"appUIAddress\").invoke(webui)", null, false, false)
                .message().get(0).getData());
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

  /**
   * Extract the eval result of spark shell, e.g. extract application_1473129941656_0048
   * from following:
   * res0: String = application_1473129941656_0048
   *
   * @param result
   * @return
   */
  private String extractStatementResult(String result) {
    int pos = -1;
    if ((pos = result.indexOf("=")) >= 0) {
      return result.substring(pos + 1).trim();
    } else {
      throw new RuntimeException("No result can be extracted from '" + result + "', " +
          "something must be wrong");
    }
  }

  @Override
  public void cancel(InterpreterContext context) {
    if (livyVersion.isCancelSupported()) {
      String paraId = context.getParagraphId();
      Integer stmtId = paragraphId2StmtIdMapping.get(paraId);
      try {
        if (stmtId != null) {
          cancelStatement(stmtId);
        }
      } catch (LivyException e) {
        LOGGER.error("Fail to cancel statement " + stmtId + " for paragraph " + paraId, e);
      } finally {
        paragraphId2StmtIdMapping.remove(paraId);
      }
    } else {
      LOGGER.warn("cancel is not supported for this version of livy: " + livyVersion);
    }
  }

  @Override
  public FormType getFormType() {
    return FormType.SIMPLE;
  }

  @Override
  public int getProgress(InterpreterContext context) {
    return 0;
  }

  private SessionInfo createSession(String user, String kind)
      throws LivyException {
    try {
      Map<String, String> conf = new HashMap<>();
      for (Map.Entry<Object, Object> entry : property.entrySet()) {
        if (entry.getKey().toString().startsWith("livy.spark.") &&
            !entry.getValue().toString().isEmpty())
          conf.put(entry.getKey().toString().substring(5), entry.getValue().toString());
      }

      CreateSessionRequest request = new CreateSessionRequest(kind,
          user.equals("anonymous") ? null : user, conf);
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
      if (paragraphId != null) {
        paragraphId2StmtIdMapping.put(paragraphId, stmtInfo.id);
      }
      // pull the statement status
      while (!stmtInfo.isAvailable()) {
        try {
          Thread.sleep(pullStatusInterval);
        } catch (InterruptedException e) {
          LOGGER.error("InterruptedException when pulling statement status.", e);
          throw new LivyException(e);
        }
        stmtInfo = getStatementInfo(stmtInfo.id);
      }
      if (appendSessionExpired) {
        return appendSessionExpire(getResultFromStatementInfo(stmtInfo, displayAppInfo),
            sessionExpired);
      } else {
        return getResultFromStatementInfo(stmtInfo, displayAppInfo);
      }
    } finally {
      if (paragraphId != null) {
        paragraphId2StmtIdMapping.remove(paragraphId);
      }
    }
  }

  private LivyVersion getLivyVersion() throws LivyException {
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
              "Paragraphs that depend on this paragraph need to be re-executed!" + "</font>");
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
    if (stmtInfo.output.isError()) {
      return new InterpreterResult(InterpreterResult.Code.ERROR, stmtInfo.output.evalue);
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
        //TODO(zjffdu), use multiple InterpreterResult to display appInfo
        InterpreterResult interpreterResult = new InterpreterResult(InterpreterResult.Code.SUCCESS);
        interpreterResult.add(InterpreterResult.Type.TEXT, result);
        String appInfoHtml = "<hr/>Spark Application Id: " + sessionInfo.appId + "<br/>"
            + "Spark WebUI: <a href=\"" + sessionInfo.webUIAddress + "\">"
            + sessionInfo.webUIAddress + "</a>";
        LOGGER.info("appInfoHtml:" + appInfoHtml);
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

  private RestTemplate getRestTemplate() {
    String keytabLocation = property.getProperty("zeppelin.livy.keytab");
    String principal = property.getProperty("zeppelin.livy.principal");
    if (StringUtils.isNotEmpty(keytabLocation) && StringUtils.isNotEmpty(principal)) {
      return new KerberosRestTemplate(keytabLocation, principal);
    }
    return new RestTemplate();
  }

  private String callRestAPI(String targetURL, String method) throws LivyException {
    return callRestAPI(targetURL, method, "");
  }

  private String callRestAPI(String targetURL, String method, String jsonData)
      throws LivyException {
    targetURL = livyURL + targetURL;
    LOGGER.debug("Call rest api in {}, method: {}, jsonData: {}", targetURL, method, jsonData);
    RestTemplate restTemplate = getRestTemplate();
    HttpHeaders headers = new HttpHeaders();
    headers.add("Content-Type", "application/json");
    headers.add("X-Requested-By", "zeppelin");
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
      if (response.getBody().matches("\"Session '\\d+' not found.\"")) {
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
    public StatementOutput output;

    public StatementInfo() {
    }

    public static StatementInfo fromJson(String json) {
      return gson.fromJson(json, StatementInfo.class);
    }

    public boolean isAvailable() {
      return state.equals("available") || state.equals("cancelled");
    }

    private static class StatementOutput {
      public String status;
      public String execution_count;
      public Data data;
      public String ename;
      public String evalue;
      public Object traceback;
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
