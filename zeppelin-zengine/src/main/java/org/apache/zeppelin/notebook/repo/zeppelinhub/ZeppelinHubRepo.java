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
package org.apache.zeppelin.notebook.repo.zeppelinhub;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.apache.commons.lang.StringUtils;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.NoteInfo;
import org.apache.zeppelin.notebook.repo.NotebookRepo;
import org.apache.zeppelin.notebook.repo.NotebookRepoSettingsInfo;
import org.apache.zeppelin.notebook.repo.zeppelinhub.model.Instance;
import org.apache.zeppelin.notebook.repo.zeppelinhub.model.UserSessionContainer;
import org.apache.zeppelin.notebook.repo.zeppelinhub.rest.ZeppelinhubRestApiHandler;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

/**
 * ZeppelinHub repo class.
 */
public class ZeppelinHubRepo implements NotebookRepo {
  private static final Logger LOG = LoggerFactory.getLogger(ZeppelinHubRepo.class);
  private static final String DEFAULT_SERVER = "https://www.zeppelinhub.com";
  static final String ZEPPELIN_CONF_PROP_NAME_SERVER = "zeppelinhub.api.address";
  static final String ZEPPELIN_CONF_PROP_NAME_TOKEN = "zeppelinhub.api.token";
  public static final String TOKEN_HEADER = "X-Zeppelin-Token";
  private static final Gson GSON = new Gson();
  private static final Note EMPTY_NOTE = new Note();
  //private final Client websocketClient;

  private String token;
  private ZeppelinhubRestApiHandler restApiClient;
  
  private final ZeppelinConfiguration conf;

  // In order to avoid too many call to ZeppelinHub backend, we save a map of user -> session.
  private ConcurrentMap<String, String> usersToken = new ConcurrentHashMap<String, String>();
  
  public ZeppelinHubRepo(ZeppelinConfiguration conf) {
    this.conf = conf;
    String zeppelinHubUrl = getZeppelinHubUrl(conf);
    LOG.info("Initializing ZeppelinHub integration module");
    token = conf.getString("ZEPPELINHUB_API_TOKEN", ZEPPELIN_CONF_PROP_NAME_TOKEN, "");
    restApiClient = ZeppelinhubRestApiHandler.newInstance(zeppelinHubUrl, token);

    // TODO(xxx): refactor this in the next itaration
    //websocketClient = Client.initialize(getZeppelinWebsocketUri(conf),
    //    getZeppelinhubWebsocketUri(conf), token, conf);
    //websocketClient.start();
  }

  private String getZeppelinHubWsUri(URI api) throws URISyntaxException {
    URI apiRoot = api;
    String scheme = apiRoot.getScheme();
    int port = apiRoot.getPort();
    if (port <= 0) {
      port = (scheme != null && scheme.equals("https")) ? 443 : 80;
    }

    if (scheme == null) {
      LOG.info("{} is not a valid zeppelinhub server address. proceed with default address {}",
          apiRoot, DEFAULT_SERVER);
      apiRoot = new URI(DEFAULT_SERVER);
      scheme = apiRoot.getScheme();
      port = apiRoot.getPort();
      if (port <= 0) {
        port = (scheme != null && scheme.equals("https")) ? 443 : 80;
      }
    }
    String ws = scheme.equals("https") ? "wss://" : "ws://";
    return ws + apiRoot.getHost() + ":" + port + "/async";
  }

  String getZeppelinhubWebsocketUri(ZeppelinConfiguration conf) {
    String zeppelinHubUri = StringUtils.EMPTY;
    try {
      zeppelinHubUri = getZeppelinHubWsUri(new URI(conf.getString("ZEPPELINHUB_API_ADDRESS",
          ZEPPELIN_CONF_PROP_NAME_SERVER, DEFAULT_SERVER)));
    } catch (URISyntaxException e) {
      LOG.error("Cannot get ZeppelinHub URI", e);
    }
    return zeppelinHubUri;
  }

  private String getZeppelinWebsocketUri(ZeppelinConfiguration conf) {
    int port = conf.getServerPort();
    if (port <= 0) {
      port = 80;
    }
    String ws = conf.useSsl() ? "wss" : "ws";
    return ws + "://localhost:" + port + "/ws";
  }

  // Used in tests
  void setZeppelinhubRestApiHandler(ZeppelinhubRestApiHandler zeppelinhub) {
    restApiClient = zeppelinhub;
  }

  String getZeppelinHubUrl(ZeppelinConfiguration conf) {
    if (conf == null) {
      LOG.error("Invalid configuration, cannot be null. Using default address {}", DEFAULT_SERVER);
      return DEFAULT_SERVER;
    }
    URI apiRoot;
    String zeppelinhubUrl;
    try {
      String url = conf.getString("ZEPPELINHUB_API_ADDRESS",
                                  ZEPPELIN_CONF_PROP_NAME_SERVER,
                                  DEFAULT_SERVER);
      apiRoot = new URI(url);
    } catch (URISyntaxException e) {
      LOG.error("Invalid zeppelinhub url, using default address {}", DEFAULT_SERVER, e);
      return DEFAULT_SERVER;
    }

    String scheme = apiRoot.getScheme();
    if (scheme == null) {
      LOG.info("{} is not a valid zeppelinhub server address. proceed with default address {}",
               apiRoot, DEFAULT_SERVER);
      zeppelinhubUrl = DEFAULT_SERVER;
    } else {
      zeppelinhubUrl = scheme + "://" + apiRoot.getHost();
      if (apiRoot.getPort() > 0) {
        zeppelinhubUrl += ":" + apiRoot.getPort();
      }
    }
    return zeppelinhubUrl;
  }
  
  /**
   * Get Token directly from Zeppelinhub.
   * This will avoid and remove the needs of setting up token in zeppelin-env.sh.
   */
  private String getUserZeppelinInstanceToken(String ticket) throws IOException {
    if (StringUtils.isBlank(ticket)) {
      return "";
    }

    List<Instance> instances = restApiClient.getInstances(ticket);
    // TODO(anthony): Implement NotebookRepo Setting to let user switch token at runtime.

    token = instances.isEmpty() ? StringUtils.EMPTY : instances.get(0).token;
    return token;
  }

  /**
   * For a given user logged in is zeppelin (via zeppelinhub notebook repo), get default token.
   *  */
  private String getUserToken(String principal) {
    String token = usersToken.get(principal);
    if (StringUtils.isBlank(token)) {
      String ticket = UserSessionContainer.instance.getSession(principal);
      try {
        token = getUserZeppelinInstanceToken(ticket);
        usersToken.putIfAbsent(principal, token);
      } catch (IOException e) {
        LOG.error("Cannot get user token", e);
        token = StringUtils.EMPTY;
      }
    }

    return token;
  }

  private boolean isSubjectValid(AuthenticationInfo subject) {
    if (subject == null) {
      return false;
    }
    return (subject.isAnonymous() && !conf.isAnonymousAllowed()) ? false : true;
  }
  
  @Override
  public List<NoteInfo> list(AuthenticationInfo subject) throws IOException {
    if (!isSubjectValid(subject)) {
      return Collections.emptyList();
    }
    String token = getUserToken(subject.getUser());
    String response = restApiClient.get(token, StringUtils.EMPTY);
    List<NoteInfo> notes = GSON.fromJson(response, new TypeToken<List<NoteInfo>>() {}.getType());
    if (notes == null) {
      return Collections.emptyList();
    }
    LOG.info("ZeppelinHub REST API listing notes ");
    return notes;
  }

  @Override
  public Note get(String noteId, AuthenticationInfo subject) throws IOException {
    if (StringUtils.isBlank(noteId) || !isSubjectValid(subject)) {
      return EMPTY_NOTE;
    }
    String token = getUserToken(subject.getUser());
    String response = restApiClient.get(token, noteId);
    Note note = GSON.fromJson(response, Note.class);
    if (note == null) {
      return EMPTY_NOTE;
    }
    LOG.info("ZeppelinHub REST API get note {} ", noteId);
    return note;
  }

  @Override
  public void save(Note note, AuthenticationInfo subject) throws IOException {
    if (note == null || !isSubjectValid(subject)) {
      throw new IOException("Zeppelinhub failed to save note");
    }
    String jsonNote = GSON.toJson(note);
    String token = getUserToken(subject.getUser());
    LOG.info("ZeppelinHub REST API saving note {} ", note.getId());
    restApiClient.put(token, jsonNote);
  }

  @Override
  public void remove(String noteId, AuthenticationInfo subject) throws IOException {
    if (StringUtils.isBlank(noteId) || !isSubjectValid(subject)) {
      throw new IOException("Zeppelinhub failed to remove note");
    }
    String token = getUserToken(subject.getUser());
    LOG.info("ZeppelinHub REST API removing note {} ", noteId);
    restApiClient.del(token, noteId);
  }

  @Override
  public void close() {
    //websocketClient.stop();
  }

  @Override
  public Revision checkpoint(String noteId, String checkpointMsg, AuthenticationInfo subject)
      throws IOException {
    if (StringUtils.isBlank(noteId) || !isSubjectValid(subject)) {
      return null;
    }
    String endpoint = Joiner.on("/").join(noteId, "checkpoint");
    String content = GSON.toJson(ImmutableMap.of("message", checkpointMsg));
    
    String token = getUserToken(subject.getUser());
    String response = restApiClient.putWithResponseBody(token, endpoint, content);

    return GSON.fromJson(response, Revision.class);
  }

  @Override
  public Note get(String noteId, String revId, AuthenticationInfo subject) throws IOException {
    if (StringUtils.isBlank(noteId) || StringUtils.isBlank(revId) || !isSubjectValid(subject)) {
      return EMPTY_NOTE;
    }
    String endpoint = Joiner.on("/").join(noteId, "checkpoint", revId);
    
    String token = getUserToken(subject.getUser());
    String response = restApiClient.get(token, endpoint);

    Note note = GSON.fromJson(response, Note.class);
    if (note == null) {
      return EMPTY_NOTE;
    }
    LOG.info("ZeppelinHub REST API get note {} revision {}", noteId, revId);
    return note;
  }

  @Override
  public List<Revision> revisionHistory(String noteId, AuthenticationInfo subject) {
    if (StringUtils.isBlank(noteId) || !isSubjectValid(subject)) {
      return Collections.emptyList();
    }
    String endpoint = Joiner.on("/").join(noteId, "checkpoint");
    List<Revision> history = Collections.emptyList();
    try {
      String token = getUserToken(subject.getUser());
      String response = restApiClient.get(token, endpoint);
      history = GSON.fromJson(response, new TypeToken<List<Revision>>(){}.getType());
    } catch (IOException e) {
      LOG.error("Cannot get note history", e);
    }
    return history;
  }

  @Override
  public List<NotebookRepoSettingsInfo> getSettings(AuthenticationInfo subject) {
    LOG.warn("Method not implemented");
    return Collections.emptyList();
  }

  @Override
  public void updateSettings(Map<String, String> settings, AuthenticationInfo subject) {
    LOG.warn("Method not implemented");
  }

}
