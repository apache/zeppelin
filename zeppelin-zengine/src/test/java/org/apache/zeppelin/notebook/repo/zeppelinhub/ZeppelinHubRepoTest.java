package org.apache.zeppelin.notebook.repo.zeppelinhub;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.apache.commons.httpclient.HttpException;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.NoteInfo;
import org.apache.zeppelin.notebook.repo.zeppelinhub.rest.ZeppelinhubRestApiHandler;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.junit.Before;
import org.junit.Test;

import com.google.common.io.Files;


public class ZeppelinHubRepoTest {
  final String token = "AAA-BBB-CCC-00";
  final String testAddr = "http://zeppelinhub.ltd";
  final AuthenticationInfo auth = new AuthenticationInfo("anthony");

  private ZeppelinHubRepo repo;
  private File pathOfNotebooks = new File(System.getProperty("user.dir") + "/src/test/resources/list_of_notes");
  private File pathOfNotebook = new File(System.getProperty("user.dir") + "/src/test/resources/note");

  @Before
  public void setUp() throws Exception {
    System.setProperty(ZeppelinHubRepo.ZEPPELIN_CONF_PROP_NAME_SERVER, testAddr);
    System.setProperty(ZeppelinHubRepo.ZEPPELIN_CONF_PROP_NAME_TOKEN, token);

    ZeppelinConfiguration conf = new ZeppelinConfiguration();
    repo = new ZeppelinHubRepo(conf);
    repo.setZeppelinhubRestApiHandler(getMockedZeppelinHandler());
  }

  private ZeppelinhubRestApiHandler getMockedZeppelinHandler() throws HttpException, IOException {
    ZeppelinhubRestApiHandler mockedZeppelinhubHandler = mock(ZeppelinhubRestApiHandler.class);

    byte[] listOfNotesResponse = Files.toByteArray(pathOfNotebooks);
    when(mockedZeppelinhubHandler.get("AAA-BBB-CCC-00", ""))
      .thenReturn(new String(listOfNotesResponse));

    byte[] noteResponse =  Files.toByteArray(pathOfNotebook);
    when(mockedZeppelinhubHandler.get("AAA-BBB-CCC-00", "AAAAA"))
      .thenReturn(new String(noteResponse));

    return mockedZeppelinhubHandler;
  }

  @Test
  public void testGetZeppelinhubUrl() {
    System.setProperty(ZeppelinHubRepo.ZEPPELIN_CONF_PROP_NAME_SERVER, testAddr);
    
    ZeppelinConfiguration config = new ZeppelinConfiguration();
    ZeppelinHubRepo repository = new ZeppelinHubRepo(config);
    assertThat(repository.getZeppelinHubUrl(config)).isEqualTo("http://zeppelinhub.ltd");

    System.setProperty(ZeppelinHubRepo.ZEPPELIN_CONF_PROP_NAME_SERVER, "yolow");

    config = new ZeppelinConfiguration();
    repository = new ZeppelinHubRepo(config);
    assertThat(repository.getZeppelinHubUrl(config)).isEqualTo("https://www.zeppelinhub.com");
    
    System.setProperty(ZeppelinHubRepo.ZEPPELIN_CONF_PROP_NAME_SERVER, "http://zeppelinhub.ltd:4242");

    config = new ZeppelinConfiguration();
    repository = new ZeppelinHubRepo(config);
    assertThat(repository.getZeppelinHubUrl(config)).isEqualTo("http://zeppelinhub.ltd:4242");
    
    System.setProperty(ZeppelinHubRepo.ZEPPELIN_CONF_PROP_NAME_SERVER, "http://zeppelinhub.ltd:0");

    config = new ZeppelinConfiguration();
    repository = new ZeppelinHubRepo(config);
    assertThat(repository.getZeppelinHubUrl(config)).isEqualTo("http://zeppelinhub.ltd");
  }

  @Test
  public void testGetZeppelinHubWsEndpoint() {
    System.setProperty(ZeppelinHubRepo.ZEPPELIN_CONF_PROP_NAME_SERVER, testAddr);

    ZeppelinConfiguration config = new ZeppelinConfiguration();
    ZeppelinHubRepo repository = new ZeppelinHubRepo(config);
    assertThat(repository.getZeppelinhubWebsocketUri(config)).isEqualTo("ws://zeppelinhub.ltd:80/async");

    System.setProperty(ZeppelinHubRepo.ZEPPELIN_CONF_PROP_NAME_SERVER, "https://zeppelinhub.ltd");

    config = new ZeppelinConfiguration();
    repository = new ZeppelinHubRepo(config);
    assertThat(repository.getZeppelinhubWebsocketUri(config)).isEqualTo("wss://zeppelinhub.ltd:443/async");

    System.setProperty(ZeppelinHubRepo.ZEPPELIN_CONF_PROP_NAME_SERVER, "yolow");

    config = new ZeppelinConfiguration();
    repository = new ZeppelinHubRepo(config);
    assertThat(repository.getZeppelinhubWebsocketUri(config)).isEqualTo("wss://www.zeppelinhub.com:443/async");

    System.setProperty(ZeppelinHubRepo.ZEPPELIN_CONF_PROP_NAME_SERVER, "http://zeppelinhub.ltd:4242");

    config = new ZeppelinConfiguration();
    repository = new ZeppelinHubRepo(config);
    assertThat(repository.getZeppelinhubWebsocketUri(config)).isEqualTo("ws://zeppelinhub.ltd:4242/async");

    System.setProperty(ZeppelinHubRepo.ZEPPELIN_CONF_PROP_NAME_SERVER, "https://www.zeppelinhub.com");

    config = new ZeppelinConfiguration();
    repository = new ZeppelinHubRepo(config);
    assertThat(repository.getZeppelinhubWebsocketUri(config)).isEqualTo("wss://www.zeppelinhub.com:443/async");

    System.setProperty(ZeppelinHubRepo.ZEPPELIN_CONF_PROP_NAME_SERVER, "http://www.zeppelinhub.com");

    config = new ZeppelinConfiguration();
    repository = new ZeppelinHubRepo(config);
    assertThat(repository.getZeppelinhubWebsocketUri(config)).isEqualTo("ws://www.zeppelinhub.com:80/async");

    System.setProperty(ZeppelinHubRepo.ZEPPELIN_CONF_PROP_NAME_SERVER, "https://www.zeppelinhub.com:4242");

    config = new ZeppelinConfiguration();
    repository = new ZeppelinHubRepo(config);
    assertThat(repository.getZeppelinhubWebsocketUri(config)).isEqualTo("wss://www.zeppelinhub.com:4242/async");
  }

  @Test
  public void testGetAllNotes() throws IOException {
    List<NoteInfo> notebooks = repo.list(auth);
    assertThat(notebooks).isNotEmpty();
    assertThat(notebooks.size()).isEqualTo(3);
  }
  
  @Test
  public void testGetNote() throws IOException {
    Note notebook = repo.get("AAAAA", auth);
    assertThat(notebook).isNotNull();
    assertThat(notebook.getId()).isEqualTo("2A94M5J1Z");
  }
  
  @Test
  public void testRemoveNote() throws IOException {
    // not suppose to throw
    repo.remove("AAAAA", auth);
  }
  
  @Test
  public void testRemoveNoteError() throws IOException {
    // not suppose to throw
    repo.remove("BBBBB", auth);
  }

}
