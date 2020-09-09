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


package org.apache.zeppelin.client;

import kong.unirest.GetRequest;
import kong.unirest.HttpResponse;
import kong.unirest.JsonNode;
import kong.unirest.Unirest;
import kong.unirest.apache.ApacheClient;
import kong.unirest.json.JSONArray;
import kong.unirest.json.JSONObject;
import org.apache.commons.text.StringEscapeUtils;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.zeppelin.common.SessionInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import unirest.shaded.org.apache.http.client.HttpClient;
import unirest.shaded.org.apache.http.impl.client.HttpClients;

import javax.net.ssl.SSLContext;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Low level api for interacting with Zeppelin. Underneath, it use the zeppelin rest api.
 * You can use this class to operate Zeppelin note/paragraph,
 * e.g. get/add/delete/update/execute/cancel
 */
public class ZeppelinClient {
  private static final Logger LOGGER = LoggerFactory.getLogger(ZeppelinClient.class);

  private ClientConfig clientConfig;

  public ZeppelinClient(ClientConfig clientConfig) throws Exception {
    this.clientConfig = clientConfig;
    Unirest.config().defaultBaseUrl(clientConfig.getZeppelinRestUrl() + "/api");

    if (clientConfig.isUseKnox()) {
      try {
        SSLContext sslContext = new SSLContextBuilder().loadTrustMaterial(null, new TrustSelfSignedStrategy() {
          public boolean isTrusted(X509Certificate[] chain, String authType) {
            return true;
          }
        }).build();
        HttpClient customHttpClient = HttpClients.custom().setSSLContext(sslContext)
                .setSSLHostnameVerifier(new NoopHostnameVerifier()).build();
        Unirest.config().httpClient(ApacheClient.builder(customHttpClient));
      } catch (Exception e) {
        throw new Exception("Fail to setup httpclient of Unirest", e);
      }
    }
  }

  public ClientConfig getClientConfig() {
    return clientConfig;
  }

  /**
   * Throw exception if the status code is not 200.
   *
   * @param response
   * @throws Exception
   */
  private void checkResponse(HttpResponse<JsonNode> response) throws Exception {
    if (response.getStatus() == 302) {
      throw new Exception("Please login first");
    }
    if (response.getStatus() != 200) {
      throw new Exception(String.format("Unable to call rest api, status: %s, statusText: %s, message: %s",
              response.getStatus(),
              response.getStatusText(),
              response.getBody().getObject().getString("message")));
    }
  }

  /**
   * Throw exception if the status in the json object is not `OK`.
   *
   * @param jsonNode
   * @throws Exception
   */
  private void checkJsonNodeStatus(JsonNode jsonNode) throws Exception {
    if (! "OK".equalsIgnoreCase(jsonNode.getObject().getString("status"))) {
      throw new Exception(StringEscapeUtils.unescapeJava(jsonNode.getObject().getString("message")));
    }
  }

  /**
   * Get Zeppelin version.
   *
   * @return
   * @throws Exception
   */
  public String getVersion() throws Exception {
    HttpResponse<JsonNode> response = Unirest
            .get("/version")
            .asJson();
    checkResponse(response);
    JsonNode jsonNode = response.getBody();
    checkJsonNodeStatus(jsonNode);
    return jsonNode.getObject().getJSONObject("body").getString("version");
  }

  /**
   * Request a new session id. It doesn't create session (interpreter process) in zeppelin server side, but just
   * create an unique session id.
   *
   * @param interpreter
   * @return
   * @throws Exception
   */
  public SessionInfo newSession(String interpreter) throws Exception {
    HttpResponse<JsonNode> response = Unirest
            .post("/session")
            .queryString("interpreter", interpreter)
            .asJson();
    checkResponse(response);
    JsonNode jsonNode = response.getBody();
    checkJsonNodeStatus(jsonNode);
    return createSessionInfoFromJson(jsonNode.getObject().getJSONObject("body"));
  }

  /**
   * Stop the session(interpreter process) in Zeppelin server.
   *
   * @param sessionId
   * @throws Exception
   */
  public void stopSession(String sessionId) throws Exception {
    HttpResponse<JsonNode> response = Unirest
            .delete("/session/{sessionId}")
            .routeParam("sessionId", sessionId)
            .asJson();
    checkResponse(response);
    JsonNode jsonNode = response.getBody();
    checkJsonNodeStatus(jsonNode);
  }

  /**
   * Get session info for the provided sessionId.
   *
   * @param sessionId
   * @throws Exception
   */
  public SessionInfo getSession(String sessionId) throws Exception {
    HttpResponse<JsonNode> response = Unirest
            .get("/session/{sessionId}")
            .routeParam("sessionId", sessionId)
            .asJson();
    checkResponse(response);
    JsonNode jsonNode = response.getBody();
    checkJsonNodeStatus(jsonNode);

    JSONObject bodyObject = jsonNode.getObject().getJSONObject("body");
    return createSessionInfoFromJson(bodyObject);
  }

  /**
   * List all the sessions.
   *
   * @return
   * @throws Exception
   */
  public List<SessionInfo> listSessions() throws Exception {
    return listSessions(null);
  }

  /**
   * List all the sessions for the provided interpreter.
   *
   * @param interpreter
   * @return
   * @throws Exception
   */
  public List<SessionInfo> listSessions(String interpreter) throws Exception {
    GetRequest getRequest = Unirest.get("/session");
    if (interpreter != null) {
      getRequest.queryString("interpreter", interpreter);
    }
    HttpResponse<JsonNode> response = getRequest.asJson();
    checkResponse(response);
    JsonNode jsonNode = response.getBody();
    checkJsonNodeStatus(jsonNode);
    JSONArray sessionJsonArray = jsonNode.getObject().getJSONArray("body");
    List<SessionInfo> sessionInfos = new ArrayList<>();
    for (int i = 0; i< sessionJsonArray.length();++i) {
      sessionInfos.add(createSessionInfoFromJson(sessionJsonArray.getJSONObject(i)));
    }
    return sessionInfos;
  }

  private SessionInfo createSessionInfoFromJson(JSONObject sessionJson) {
    SessionInfo sessionInfo = new SessionInfo();
    if (sessionJson.has("sessionId")) {
      sessionInfo.setSessionId(sessionJson.getString("sessionId"));
    }
    if (sessionJson.has("noteId")) {
      sessionInfo.setNoteId(sessionJson.getString("noteId"));
    }
    if (sessionJson.has("interpreter")) {
      sessionInfo.setInterpreter(sessionJson.getString("interpreter"));
    }
    if (sessionJson.has("state")) {
      sessionInfo.setState(sessionJson.getString("state"));
    }
    if (sessionJson.has("weburl")) {
      sessionInfo.setWeburl(sessionJson.getString("weburl"));
    }
    if (sessionJson.has("startTime")) {
      sessionInfo.setStartTime(sessionJson.getString("startTime"));
    }

    return sessionInfo;
  }

  /**
   * Login zeppelin with userName and password, throw exception if login fails.
   *
   * @param userName
   * @param password
   * @throws Exception
   */
  public void login(String userName, String password) throws Exception {
    if (clientConfig.isUseKnox()) {
      HttpResponse<String> response = Unirest.get("/")
              .basicAuth(userName, password)
              .asString();
      if (response.getStatus() != 200) {
        throw new Exception(String.format("Login failed, status: %s, statusText: %s",
                response.getStatus(),
                response.getStatusText()));
      }
    } else {
      HttpResponse<JsonNode> response = Unirest
              .post("/login")
              .field("userName", userName)
              .field("password", password)
              .asJson();
      if (response.getStatus() != 200) {
        throw new Exception(String.format("Login failed, status: %s, statusText: %s",
                response.getStatus(),
                response.getStatusText()));
      }
    }
  }

  public String createNote(String notePath) throws Exception {
    return createNote(notePath, "");
  }

  /**
   * Create a new empty note with provided notePath and defaultInterpreterGroup
   *
   * @param notePath
   * @param defaultInterpreterGroup
   * @return
   * @throws Exception
   */
  public String createNote(String notePath, String defaultInterpreterGroup) throws Exception {
    JSONObject bodyObject = new JSONObject();
    bodyObject.put("name", notePath);
    bodyObject.put("defaultInterpreterGroup", defaultInterpreterGroup);
    HttpResponse<JsonNode> response = Unirest
            .post("/notebook")
            .body(bodyObject.toString())
            .asJson();
    checkResponse(response);
    JsonNode jsonNode = response.getBody();
    checkJsonNodeStatus(jsonNode);

    return jsonNode.getObject().getString("body");
  }

  /**
   * Delete note with provided noteId.
   *
   * @param noteId
   * @throws Exception
   */
  public void deleteNote(String noteId) throws Exception {
    HttpResponse<JsonNode> response = Unirest
            .delete("/notebook/{noteId}")
            .routeParam("noteId", noteId)
            .asJson();
    checkResponse(response);
    JsonNode jsonNode = response.getBody();
    checkJsonNodeStatus(jsonNode);
  }

  /**
   * Query {@link NoteResult} with provided noteId.
   *
   * @param noteId
   * @return
   * @throws Exception
   */
  public NoteResult queryNoteResult(String noteId) throws Exception {
    HttpResponse<JsonNode> response = Unirest
            .get("/notebook/{noteId}")
            .routeParam("noteId", noteId)
            .asJson();
    checkResponse(response);
    JsonNode jsonNode = response.getBody();
    checkJsonNodeStatus(jsonNode);

    JSONObject noteJsonObject = jsonNode.getObject().getJSONObject("body");
    boolean isRunning = false;
    if (noteJsonObject.has("info")) {
      JSONObject infoJsonObject = noteJsonObject.getJSONObject("info");
      if (infoJsonObject.has("isRunning")) {
        isRunning = Boolean.parseBoolean(infoJsonObject.getString("isRunning"));
      }
    }

    List<ParagraphResult> paragraphResultList = new ArrayList<>();
    if (noteJsonObject.has("paragraphs")) {
      JSONArray paragraphJsonArray = noteJsonObject.getJSONArray("paragraphs");
      for (int i = 0; i< paragraphJsonArray.length(); ++i) {
        paragraphResultList.add(new ParagraphResult(paragraphJsonArray.getJSONObject(i)));
      }
    }

    return new NoteResult(noteId, isRunning, paragraphResultList);
  }

  /**
   * Execute note with provided noteId, return until note execution is completed.
   * Interpreter process will be stopped after note execution.
   *
   * @param noteId
   * @return
   * @throws Exception
   */
  public NoteResult executeNote(String noteId) throws Exception {
    return executeNote(noteId, new HashMap<>());
  }

  /**
   * Execute note with provided noteId and parameters, return until note execution is completed.
   * Interpreter process will be stopped after note execution.
   *
   * @param noteId
   * @param parameters
   * @return
   * @throws Exception
   */
  public NoteResult executeNote(String noteId, Map<String, String> parameters) throws Exception {
    submitNote(noteId, parameters);
    return waitUntilNoteFinished(noteId);
  }

  /**
   * Submit note to execute with provided noteId, return at once the submission is completed.
   * You need to query {@link NoteResult} by yourself afterwards until note execution is completed.
   * Interpreter process will be stopped after note execution.
   *
   * @param noteId
   * @return
   * @throws Exception
   */
  public NoteResult submitNote(String noteId) throws Exception  {
    return submitNote(noteId, new HashMap<>());
  }

  /**
   * Submit note to execute with provided noteId and parameters, return at once the submission is completed.
   * You need to query {@link NoteResult} by yourself afterwards until note execution is completed.
   * Interpreter process will be stopped after note execution.
   *
   * @param noteId
   * @param parameters
   * @return
   * @throws Exception
   */
  public NoteResult submitNote(String noteId, Map<String, String> parameters) throws Exception  {
    JSONObject bodyObject = new JSONObject();
    bodyObject.put("params", parameters);
    // run note in non-blocking and isolated way.
    HttpResponse<JsonNode> response = Unirest
            .post("/notebook/job/{noteId}")
            .routeParam("noteId", noteId)
            .queryString("blocking", "false")
            .queryString("isolated", "true")
            .body(bodyObject)
            .asJson();
    checkResponse(response);
    JsonNode jsonNode = response.getBody();
    checkJsonNodeStatus(jsonNode);
    return queryNoteResult(noteId);
  }

  /**
   * Block there until note execution is completed.
   *
   * @param noteId
   * @return
   * @throws Exception
   */
  public NoteResult waitUntilNoteFinished(String noteId) throws Exception {
    while (true) {
      NoteResult noteResult = queryNoteResult(noteId);
      if (!noteResult.isRunning()) {
        return noteResult;
      }
      Thread.sleep(clientConfig.getQueryInterval());
    }
  }

  /**
   * Block there until note execution is completed, and throw exception if note execution is not completed
   * in <code>timeoutInMills</code>.
   *
   * @param noteId
   * @param timeoutInMills
   * @return
   * @throws Exception
   */
  public NoteResult waitUntilNoteFinished(String noteId, long timeoutInMills) throws Exception {
    long start = System.currentTimeMillis();
    while (true && (System.currentTimeMillis() - start) < timeoutInMills) {
      NoteResult noteResult = queryNoteResult(noteId);
      if (!noteResult.isRunning()) {
        return noteResult;
      }
      Thread.sleep(clientConfig.getQueryInterval());
    }
    throw new Exception("Note is not finished in " + timeoutInMills / 1000 + " seconds");
  }

  /**
   * Add paragraph to note with provided title and text.
   *
   * @param noteId
   * @param title
   * @param text
   * @return
   * @throws Exception
   */
  public String addParagraph(String noteId, String title, String text) throws Exception {
    JSONObject bodyObject = new JSONObject();
    bodyObject.put("title", title);
    bodyObject.put("text", text);
    HttpResponse<JsonNode> response = Unirest.post("/notebook/{noteId}/paragraph")
            .routeParam("noteId", noteId)
            .body(bodyObject.toString())
            .asJson();
    checkResponse(response);
    JsonNode jsonNode = response.getBody();
    checkJsonNodeStatus(jsonNode);

    return jsonNode.getObject().getString("body");
  }

  /**
   * Update paragraph with specified title and text.
   *
   * @param noteId
   * @param paragraphId
   * @param title
   * @param text
   * @throws Exception
   */
  public void updateParagraph(String noteId, String paragraphId, String title, String text) throws Exception {
    JSONObject bodyObject = new JSONObject();
    bodyObject.put("title", title);
    bodyObject.put("text", text);
    HttpResponse<JsonNode> response = Unirest.put("/notebook/{noteId}/paragraph/{paragraphId}")
            .routeParam("noteId", noteId)
            .routeParam("paragraphId", paragraphId)
            .body(bodyObject.toString())
            .asJson();
    checkResponse(response);
    JsonNode jsonNode = response.getBody();
    checkJsonNodeStatus(jsonNode);
  }

  /**
   * Execute paragraph with parameters in specified session. If sessionId is null or empty string, then it depends on
   * the interpreter binding mode of Note(e.g. isolated per note), otherwise it will run in the specified session.
   *
   * @param noteId
   * @param paragraphId
   * @param sessionId
   * @param parameters
   * @return
   * @throws Exception
   */
  public ParagraphResult executeParagraph(String noteId,
                                          String paragraphId,
                                          String sessionId,
                                          Map<String, String> parameters) throws Exception {
    submitParagraph(noteId, paragraphId, sessionId, parameters);
    return waitUtilParagraphFinish(noteId, paragraphId);
  }

  /**
   * Execute paragraph with parameters.
   *
   * @param noteId
   * @param paragraphId
   * @param parameters
   * @return
   * @throws Exception
   */
  public ParagraphResult executeParagraph(String noteId,
                                          String paragraphId,
                                          Map<String, String> parameters) throws Exception {
    return executeParagraph(noteId, paragraphId, "", parameters);
  }

  /**
   * Execute paragraph in specified session. If sessionId is null or empty string, then it depends on
   * the interpreter binding mode of Note (e.g. isolated per note), otherwise it will run in the specified session.
   *
   * @param noteId
   * @param paragraphId
   * @param sessionId
   * @return
   * @throws Exception
   */
  public ParagraphResult executeParagraph(String noteId,
                                          String paragraphId,
                                          String sessionId) throws Exception {
    return executeParagraph(noteId, paragraphId, sessionId, new HashMap<>());
  }

  /**
   * Execute paragraph.
   *
   * @param noteId
   * @param paragraphId
   * @return
   * @throws Exception
   */
  public ParagraphResult executeParagraph(String noteId, String paragraphId) throws Exception {
    return executeParagraph(noteId, paragraphId, "", new HashMap<>());
  }

  /**
   * Submit paragraph to execute with provided parameters and sessionId. Return at once the submission is completed.
   * You need to query {@link ParagraphResult} by yourself afterwards until paragraph execution is completed.
   *
   * @param noteId
   * @param paragraphId
   * @param sessionId
   * @param parameters
   * @return
   * @throws Exception
   */
  public ParagraphResult submitParagraph(String noteId,
                                         String paragraphId,
                                         String sessionId,
                                         Map<String, String> parameters) throws Exception {
    JSONObject bodyObject = new JSONObject();
    bodyObject.put("params", parameters);
    HttpResponse<JsonNode> response = Unirest
            .post("/notebook/job/{noteId}/{paragraphId}")
            .routeParam("noteId", noteId)
            .routeParam("paragraphId", paragraphId)
            .queryString("sessionId", sessionId)
            .body(bodyObject.toString())
            .asJson();
    checkResponse(response);
    JsonNode jsonNode = response.getBody();
    checkJsonNodeStatus(jsonNode);
    return queryParagraphResult(noteId, paragraphId);
  }

  /**
   * Submit paragraph to execute with provided sessionId. Return at once the submission is completed.
   * You need to query {@link ParagraphResult} by yourself afterwards until paragraph execution is completed.
   *
   * @param noteId
   * @param paragraphId
   * @param sessionId
   * @return
   * @throws Exception
   */
  public ParagraphResult submitParagraph(String noteId,
                                         String paragraphId,
                                         String sessionId) throws Exception {
    return submitParagraph(noteId, paragraphId, sessionId, new HashMap<>());
  }

  /**
   * Submit paragraph to execute with provided parameters. Return at once the submission is completed.
   * You need to query {@link ParagraphResult} by yourself afterwards until paragraph execution is completed.
   *
   * @param noteId
   * @param paragraphId
   * @param parameters
   * @return
   * @throws Exception
   */
  public ParagraphResult submitParagraph(String noteId,
                                         String paragraphId,
                                         Map<String, String> parameters) throws Exception {
    return submitParagraph(noteId, paragraphId, "", parameters);
  }

  /**
   * Submit paragraph to execute. Return at once the submission is completed.
   * You need to query {@link ParagraphResult} by yourself afterwards until paragraph execution is completed.
   *
   * @param noteId
   * @param paragraphId
   * @return
   * @throws Exception
   */
  public ParagraphResult submitParagraph(String noteId, String paragraphId) throws Exception {
    return submitParagraph(noteId, paragraphId, "", new HashMap<>());
  }

  /**
   * This used by {@link ZSession} for creating or reusing a paragraph for executing another piece of code.
   *
   * @param noteId
   * @param maxParagraph
   * @return
   * @throws Exception
   */
  public String nextSessionParagraph(String noteId, int maxParagraph) throws Exception {
    HttpResponse<JsonNode> response = Unirest
            .post("/notebook/{noteId}/paragraph/next")
            .routeParam("noteId", noteId)
            .queryString("maxParagraph", maxParagraph)
            .asJson();
    checkResponse(response);
    JsonNode jsonNode = response.getBody();
    checkJsonNodeStatus(jsonNode);

    return jsonNode.getObject().getString("message");
  }

  /**
   * Cancel a running paragraph.
   *
   * @param noteId
   * @param paragraphId
   * @throws Exception
   */
  public void cancelParagraph(String noteId, String paragraphId) throws Exception {
    HttpResponse<JsonNode> response = Unirest
            .delete("/notebook/job/{noteId}/{paragraphId}")
            .routeParam("noteId", noteId)
            .routeParam("paragraphId", paragraphId)
            .asJson();
    checkResponse(response);
    JsonNode jsonNode = response.getBody();
    checkJsonNodeStatus(jsonNode);
  }

  /**
   * Query {@link ParagraphResult}
   *
   * @param noteId
   * @param paragraphId
   * @return
   * @throws Exception
   */
  public ParagraphResult queryParagraphResult(String noteId, String paragraphId) throws Exception {
    HttpResponse<JsonNode> response = Unirest
            .get("/notebook/{noteId}/paragraph/{paragraphId}")
            .routeParam("noteId", noteId)
            .routeParam("paragraphId", paragraphId)
            .asJson();
    checkResponse(response);
    JsonNode jsonNode = response.getBody();
    checkJsonNodeStatus(jsonNode);

    JSONObject paragraphJson = jsonNode.getObject().getJSONObject("body");
    return new ParagraphResult(paragraphJson);
  }

  /**
   * Query {@link ParagraphResult} until it is finished.
   *
   * @param noteId
   * @param paragraphId
   * @return
   * @throws Exception
   */
  public ParagraphResult waitUtilParagraphFinish(String noteId, String paragraphId) throws Exception {
    while (true) {
      ParagraphResult paragraphResult = queryParagraphResult(noteId, paragraphId);
      LOGGER.debug(paragraphResult.toString());
      if (paragraphResult.getStatus().isCompleted()) {
        return paragraphResult;
      }
      Thread.sleep(clientConfig.getQueryInterval());
    }
  }

  /**
   * Query {@link ParagraphResult} until it is finished or timeout.
   *
   * @param noteId
   * @param paragraphId
   * @param timeoutInMills
   * @return
   * @throws Exception
   */
  public ParagraphResult waitUtilParagraphFinish(String noteId, String paragraphId, long timeoutInMills) throws Exception {
    long start = System.currentTimeMillis();
    while (true && (System.currentTimeMillis() - start) < timeoutInMills) {
      ParagraphResult paragraphResult = queryParagraphResult(noteId, paragraphId);
      if (paragraphResult.getStatus().isCompleted()) {
        return paragraphResult;
      }
      Thread.sleep(clientConfig.getQueryInterval());
    }
    throw new Exception("Paragraph is not finished in " + timeoutInMills / 1000 + " seconds");
  }

  /**
   * Query {@link ParagraphResult} until it is running.
   *
   * @param noteId
   * @param paragraphId
   * @return
   * @throws Exception
   */
  public ParagraphResult waitUtilParagraphRunning(String noteId, String paragraphId) throws Exception {
    while (true) {
      ParagraphResult paragraphResult = queryParagraphResult(noteId, paragraphId);
      if (paragraphResult.getStatus().isRunning()) {
        return paragraphResult;
      }
      Thread.sleep(clientConfig.getQueryInterval());
    }
  }

  /**
   * This is equal to the restart operation in note page.
   *
   * @param noteId
   * @param interpreter
   */
  public void stopInterpreter(String noteId, String interpreter) throws Exception {
    JSONObject bodyObject = new JSONObject();
    bodyObject.put("noteId", noteId);
    HttpResponse<JsonNode> response = Unirest
            .put("/interpreter/setting/restart/{interpreter}")
            .routeParam("interpreter", interpreter)
            .body(bodyObject.toString())
            .asJson();
    checkResponse(response);
    JsonNode jsonNode = response.getBody();
    checkJsonNodeStatus(jsonNode);
  }
}
