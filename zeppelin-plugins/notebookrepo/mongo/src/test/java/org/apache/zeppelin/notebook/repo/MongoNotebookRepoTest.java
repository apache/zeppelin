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
    ServerSocket socket = new ServerSocket(0);
    int port = socket.getLocalPort();
    socket.close();

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
    Note note1Loaded = notebookRepo.get(note1.getId(), note1.getPath(), AuthenticationInfo.ANONYMOUS);
    assertEquals(note1.getId(), note1Loaded.getId());
    assertEquals(note1.getName(), note1Loaded.getName());

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

    // remove note1
    notebookRepo.remove(note1.getId(), note1.getPath(), AuthenticationInfo.ANONYMOUS);
    assertEquals(1, notebookRepo.list(AuthenticationInfo.ANONYMOUS).size());

    notebookRepo.remove("/my_project3", AuthenticationInfo.ANONYMOUS);
    assertEquals(0, notebookRepo.list(AuthenticationInfo.ANONYMOUS).size());
  }

  @Test
  public void testGetNotePath() throws IOException {
    assertEquals(0, notebookRepo.list(AuthenticationInfo.ANONYMOUS).size());

    Note note = new Note();
    String notePath = "/folder1/folder2/folder3/folder4/folder5/my_note";
    note.setPath(notePath);
    notebookRepo.save(note, AuthenticationInfo.ANONYMOUS);

    notebookRepo.init(zConf);
    Map<String, NoteInfo> noteInfos = notebookRepo.list(AuthenticationInfo.ANONYMOUS);
    assertEquals(1, notebookRepo.list(AuthenticationInfo.ANONYMOUS).size());
    assertEquals(notePath, noteInfos.get(note.getId()).getPath());

    notebookRepo.remove(note.getId(), note.getPath(), AuthenticationInfo.ANONYMOUS);
    assertEquals(0, notebookRepo.list(AuthenticationInfo.ANONYMOUS).size());
  }
}
