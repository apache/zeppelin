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
  private static final Gson GSON = new Gson();
  private static final Note EMPTY_NOTE = new Note();

  private String token;
  private ZeppelinhubRestApiHandler zeppelinhubHandler;

  public ZeppelinHubRepo(ZeppelinConfiguration conf) throws IOException {
    String zeppelinHubUrl = getZeppelinHubUrl(conf);
    LOG.info("Initializing ZeppelinHub integration module version ?");
    token = conf.getString("ZEPPELINHUB_API_TOKEN", ZEPPELIN_CONF_PROP_NAME_TOKEN, "");
    zeppelinhubHandler = ZeppelinhubRestApiHandler.newInstance(zeppelinHubUrl, token);
  }

  public void setZeppelinhubRestApiHandler(ZeppelinhubRestApiHandler zeppelinhub) {
    zeppelinhubHandler = zeppelinhub;
  }

  private String getZeppelinHubUrl(ZeppelinConfiguration conf) throws IOException {
    if (conf == null) {
      LOG.error("Invalid configuration, cannot be null");
      throw new IOException("Configuration is null");
    }
    URI apiRoot;
    String zeppelinhubUrl;
    try {
      String url = conf.getString("ZEPPELINHUB_API_ADDRESS",
                                  ZEPPELIN_CONF_PROP_NAME_SERVER,
                                  DEFAULT_SERVER);
      apiRoot = new URI(url);
    } catch (URISyntaxException e) {
      LOG.error("Invalid zeppelinhub url", e);
      throw new IOException(e);
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
    String response = zeppelinhubHandler.asyncGet("");
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
    String response = zeppelinhubHandler.asyncGet(noteId);
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
    zeppelinhubHandler.asyncPut(notebook);
    LOG.info("ZeppelinHub REST API saving note {} ", note.id()); 
  }

  @Override
  public void remove(String noteId) throws IOException {
    zeppelinhubHandler.asyncDel(noteId);
    LOG.info("ZeppelinHub REST API removing note {} ", noteId);
  }

  @Override
  public void close() {
    
  }

  @Override
  public void checkpoint(String noteId, String checkPointName) throws IOException {
    
  }

}
