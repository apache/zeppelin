package org.apache.zeppelin.notebook.repo;

import static org.apache.zeppelin.conf.ZeppelinConfiguration.ConfVars.ZEPPELIN_NOTEBOOK_MONGO_URI;
import static org.apache.zeppelin.user.AuthenticationInfo.ANONYMOUS;
import org.junit.Before;
import org.junit.Test;
import java.io.IOException;
import java.util.Map;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.NoteInfo;
import org.apache.zeppelin.notebook.Paragraph;
import org.apache.zeppelin.user.AuthenticationInfo;

public class MongoNotebookRepoTest {

  private ZeppelinConfiguration zConf;

  private MongoNotebookRepo notebookRepo;

  private String noteId = "2E5ZV2YNR";

  private String notePath = "/my_project1/mydir1/my_note4";

  @Before
  public void setUp() throws IOException {
    System.setProperty(ZEPPELIN_NOTEBOOK_MONGO_URI.getVarName(), "mongodb://localhost:27017");

    zConf = new ZeppelinConfiguration();
    notebookRepo = new MongoNotebookRepo();
    notebookRepo.init(zConf);
  }

  @Test
  public void list() throws Exception {
    Map<String, NoteInfo> list = notebookRepo.list(ANONYMOUS);
    System.out.println(list);
  }

  @Test
  public void get() throws Exception {
    Note note = notebookRepo.get(noteId, notePath, ANONYMOUS);
    System.out.println(note);
  }

  @Test
  public void move1() throws Exception {
    notebookRepo.move("/abc", "/abcd", ANONYMOUS);
  }

  @Test
  public void remove() throws Exception {
    notebookRepo.remove(noteId, notePath, ANONYMOUS);
  }

  @Test
  public void remove1() throws Exception {
    notebookRepo.remove("/", ANONYMOUS);
  }

  @Test
  public void save() throws Exception {
    Note note1 = new Note();
    note1.setPath(notePath);
    Paragraph p1 = note1.insertNewParagraph(0, AuthenticationInfo.ANONYMOUS);
    p1.setText("%md hello world");
    p1.setTitle("my title");

    notebookRepo.save(note1, ANONYMOUS);
  }

  @Test
  public void move() throws Exception {
    String newPath = "/myproject/newPath/note3";
    notebookRepo.move(noteId, notePath, newPath, ANONYMOUS);
  }
}