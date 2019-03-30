package org.apache.zeppelin.notebook.repo;

import static org.apache.zeppelin.conf.ZeppelinConfiguration.ConfVars.ZEPPELIN_NOTEBOOK_MONGO_URI;
import static org.junit.Assert.assertEquals;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.Map;
import de.flapdoodle.embed.mongo.MongodExecutable;
import de.flapdoodle.embed.mongo.MongodStarter;
import de.flapdoodle.embed.mongo.config.IMongodConfig;
import de.flapdoodle.embed.mongo.config.MongodConfigBuilder;
import de.flapdoodle.embed.mongo.config.Net;
import de.flapdoodle.embed.mongo.distribution.Version;
import de.flapdoodle.embed.process.runtime.Network;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.NoteInfo;
import org.apache.zeppelin.notebook.Paragraph;
import org.apache.zeppelin.user.AuthenticationInfo;

public class MongoNotebookRepoTest {

  private MongodExecutable mongodExecutable;

  private ZeppelinConfiguration zConf;

  private MongoNotebookRepo notebookRepo;

  @Before
  public void setUp() throws IOException {
    String bindIp = "localhost";
    int port = new ServerSocket(0).getLocalPort();

    IMongodConfig mongodConfig = new MongodConfigBuilder()
        .version(Version.Main.PRODUCTION)
        .net(new Net(bindIp, port, Network.localhostIsIPv6()))
        .build();

    mongodExecutable = MongodStarter.getDefaultInstance()
        .prepare(mongodConfig);
    mongodExecutable.start();

    System.setProperty(ZEPPELIN_NOTEBOOK_MONGO_URI.getVarName(), "mongodb://" + bindIp + ":" + port);
    zConf = new ZeppelinConfiguration();
    notebookRepo = new MongoNotebookRepo();
    notebookRepo.init(zConf);
  }

  @After
  public void tearDown() throws IOException {
    if (mongodExecutable != null) {
      mongodExecutable.stop();
    }
  }

  @Test
  public void testBasics() throws IOException {
    assertEquals(0, notebookRepo.list(AuthenticationInfo.ANONYMOUS).size());

    // create note1
    Note note1 = new Note();
    note1.setPath("/my_project/my_note1");
    Paragraph p1 = note1.insertNewParagraph(0, AuthenticationInfo.ANONYMOUS);
    p1.setText("%md hello world");
    p1.setTitle("my title");
    notebookRepo.save(note1, AuthenticationInfo.ANONYMOUS);

    Map<String, NoteInfo> noteInfos = notebookRepo.list(AuthenticationInfo.ANONYMOUS);
    assertEquals(1, noteInfos.size());
    assertEquals(note1.getId(), noteInfos.get(note1.getId()).getId());
    assertEquals(note1.getName(), noteInfos.get(note1.getId()).getNoteName());

    // create note2
    Note note2 = new Note();
    note2.setPath("/my_note2");
    Paragraph p2 = note2.insertNewParagraph(0, AuthenticationInfo.ANONYMOUS);
    p2.setText("%md hello world2");
    p2.setTitle("my title2");
    notebookRepo.save(note2, AuthenticationInfo.ANONYMOUS);

    noteInfos = notebookRepo.list(AuthenticationInfo.ANONYMOUS);
    assertEquals(2, noteInfos.size());

    // move note2
    String newPath = "/my_project2/my_note2";
    notebookRepo.move(note2.getId(), note2.getPath(), "/my_project2/my_note2", AuthenticationInfo.ANONYMOUS);

    Note note3 = notebookRepo.get(note2.getId(), newPath, AuthenticationInfo.ANONYMOUS);
    assertEquals(note2, note3);

    // move folder
    notebookRepo.move("/my_project2", "/my_project3/my_project2", AuthenticationInfo.ANONYMOUS);
    noteInfos = notebookRepo.list(AuthenticationInfo.ANONYMOUS);
    assertEquals(2, noteInfos.size());

    Note note4 = notebookRepo.get(note3.getId(), "/my_project3/my_project2/my_note2", AuthenticationInfo.ANONYMOUS);
    assertEquals(note3, note4);

    // remote note1
    notebookRepo.remove(note1.getId(), note1.getPath(), AuthenticationInfo.ANONYMOUS);
    assertEquals(1, notebookRepo.list(AuthenticationInfo.ANONYMOUS).size());
  }
}