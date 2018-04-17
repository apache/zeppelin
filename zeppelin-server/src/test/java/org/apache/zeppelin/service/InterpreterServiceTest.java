package org.apache.zeppelin.service;

import static org.junit.Assert.*;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import org.apache.commons.io.FileUtils;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.dep.DependencyResolver;
import org.apache.zeppelin.interpreter.InterpreterSettingManager;
import org.apache.zeppelin.notebook.socket.Message;
import org.apache.zeppelin.rest.message.InterpreterInstallationRequest;
import org.apache.zeppelin.socket.NotebookServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class InterpreterServiceTest {
  @Mock ZeppelinConfiguration mockZeppelinConfiguration;
  @Mock NotebookServer mockNotebookServer;
  @Mock InterpreterSettingManager mockInterpreterSettingManager;

  Path temporaryDir;
  Path interpreterDir;
  Path localRepoDir;

  InterpreterService interpreterService;

  @Before
  public void setUp() throws Exception {
    temporaryDir = Files.createTempDirectory("tmp");
    interpreterDir = Files.createTempDirectory(temporaryDir, "interpreter");
    localRepoDir = Files.createTempDirectory(temporaryDir, "local-repo");

    when(mockZeppelinConfiguration.getInterpreterDir()).thenReturn(interpreterDir.toString());
    when(mockZeppelinConfiguration.getInterpreterLocalRepoPath())
        .thenReturn(localRepoDir.toString());

    when(mockZeppelinConfiguration.getZeppelinProxyUrl()).thenReturn(null);
    when(mockZeppelinConfiguration.getZeppelinProxyUser()).thenReturn(null);
    when(mockZeppelinConfiguration.getZeppelinProxyPassword()).thenReturn(null);

    interpreterService =
        new InterpreterService(
            mockZeppelinConfiguration, mockNotebookServer, mockInterpreterSettingManager);
  }

  @After
  public void tearDown() throws Exception {
    if (null != temporaryDir) {
      FileUtils.deleteDirectory(temporaryDir.toFile());
    }
  }

  @Test(expected = Exception.class)
  public void invalidProxyUrl() throws Exception {
    when(mockZeppelinConfiguration.getZeppelinProxyUrl()).thenReturn("invalidProxyPath");

    interpreterService.installInterpreter(new InterpreterInstallationRequest("name", "artifact"));
  }

  @Test(expected = Exception.class)
  public void interpreterAlreadyExist() throws Exception {
    when(mockZeppelinConfiguration.getZeppelinProxyUrl()).thenReturn(null);

    String alreadyExistName = "aen";
    Path specificInterpreterDir =
        Files.createDirectory(Paths.get(interpreterDir.toString(), alreadyExistName));

    interpreterService.installInterpreter(
        new InterpreterInstallationRequest(alreadyExistName, "artifact"));
  }

  @Test
  public void downloadInterpreter() throws IOException {
    String interpreterName = "test-interpreter";
    String artifactName = "junit:junit:4.11";
    Path specificInterpreterPath =
        Files.createDirectory(Paths.get(interpreterDir.toString(), interpreterName));
    DependencyResolver dependencyResolver = new DependencyResolver(localRepoDir.toString());

    doNothing().when(mockInterpreterSettingManager).refreshInterpreterTemplates();
    ArgumentCaptor<Message> messageArgumentCaptor = ArgumentCaptor.forClass(Message.class);
    doNothing().when(mockNotebookServer).broadcast(messageArgumentCaptor.capture());

    interpreterService.downloadInterpreter(
        new InterpreterInstallationRequest(interpreterName, artifactName),
        dependencyResolver,
        specificInterpreterPath);

    Message message = messageArgumentCaptor.getValue();
    assertNotNull(message.data);
    assertEquals("Success", message.data.get("result"));
    assertEquals(interpreterName + " downloaded", message.data.get("message"));
  }
}
