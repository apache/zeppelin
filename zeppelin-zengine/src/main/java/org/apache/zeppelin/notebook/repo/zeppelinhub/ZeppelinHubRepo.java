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

import org.apache.commons.lang.StringUtils;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.NoteInfo;
import org.apache.zeppelin.notebook.repo.NotebookRepo;
import org.apache.zeppelin.notebook.repo.zeppelinhub.rest.ZeppelinhubRestApiHandler;
import org.apache.zeppelin.notebook.repo.zeppelinhub.websocket.Client;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

/**
 * ZeppelinHub repo class.
 */
public class ZeppelinHubRepo implements NotebookRepo {
  private static final Logger LOG = LoggerFactory.getLogger(ZeppelinhubRestApiHandler.class);
  private static final String DEFAULT_SERVER = "https://www.zeppelinhub.com";
  static final String ZEPPELIN_CONF_PROP_NAME_SERVER = "zeppelinhub.api.address";
  static final String ZEPPELIN_CONF_PROP_NAME_TOKEN = "zeppelinhub.api.token";
  public static final String TOKEN_HEADER = "X-Zeppelin-Token";
  private static final Gson GSON = new Gson();
  private static final Note EMPTY_NOTE = new Note();
  private final Client websocketClient;

  private String token;
  private ZeppelinhubRestApiHandler restApiClient;

  public ZeppelinHubRepo(ZeppelinConfiguration conf) {
    String zeppelinHubUrl = getZeppelinHubUrl(conf);
    LOG.info("Initializing ZeppelinHub integration module");
    token = conf.getString("ZEPPELINHUB_API_TOKEN", ZEPPELIN_CONF_PROP_NAME_TOKEN, "");
    restApiClient = ZeppelinhubRestApiHandler.newInstance(zeppelinHubUrl, token);

    websocketClient = Client.initialize(getZeppelinWebsocketUri(conf),
        getZeppelinhubWebsocketUri(conf), token, conf);
    websocketClient.start();
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

  @Override
  public List<NoteInfo> list() throws IOException {
    String response = restApiClient.asyncGet("");
    List<NoteInfo> notes = GSON.fromJson(response, new TypeToken<List<NoteInfo>>() {}.getType());
    if (notes == null) {
      return Collections.emptyList();
    }
    LOG.info("ZeppelinHub REST API listing notes ");
    return notes;
  }

  @Override
  public Note get(String noteId) throws IOException {
    if (StringUtils.isBlank(noteId)) {
      return EMPTY_NOTE;
    }
    //String response = zeppelinhubHandler.get(noteId);
    String response = restApiClient.asyncGet(noteId);
    Note note = GSON.fromJson(response, Note.class);
    if (note == null) {
      return EMPTY_NOTE;
    }
    LOG.info("ZeppelinHub REST API get note {} ", noteId);
    return note;
  }

  @Override
  public void save(Note note) throws IOException {
    if (note == null) {
      throw new IOException("Zeppelinhub failed to save empty note");
    }
    String notebook = GSON.toJson(note);
    restApiClient.asyncPut(notebook);
    LOG.info("ZeppelinHub REST API saving note {} ", note.id()); 
  }

  @Override
  public void remove(String noteId) throws IOException {
    restApiClient.asyncDel(noteId);
    LOG.info("ZeppelinHub REST API removing note {} ", noteId);
  }

  @Override
  public void close() {
    websocketClient.stop();
  }

  @Override
  public void checkpoint(String noteId, String checkPointName) throws IOException {
    
  }

}
