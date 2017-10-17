package org.apache.zeppelin.notebook.repo;


import org.apache.commons.io.FileUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class FileSystemNotebookRepoTest {

  private ZeppelinConfiguration zConf;
  private Configuration hadoopConf;
  private FileSystem fs;
  private FileSystemNotebookRepo hdfsNotebookRepo;
  private String notebookDir;
  private AuthenticationInfo authInfo = AuthenticationInfo.ANONYMOUS;

  @Before
  public void setUp() throws IOException {
    notebookDir = Files.createTempDirectory("FileSystemNotebookRepoTest").toFile().getAbsolutePath();
    zConf = new ZeppelinConfiguration();
    System.setProperty(ZeppelinConfiguration.ConfVars.ZEPPELIN_NOTEBOOK_DIR.getVarName(), notebookDir);
    hadoopConf = new Configuration();
    fs = FileSystem.get(hadoopConf);
    hdfsNotebookRepo = new FileSystemNotebookRepo(zConf);
  }

  @After
  public void tearDown() throws IOException {
    FileUtils.deleteDirectory(new File(notebookDir));
  }

  @Test
  public void testBasics() throws IOException {
    assertEquals(0, hdfsNotebookRepo.list(authInfo).size());

    // create a new note
    Note note = new Note();
    note.setName("title_1");

    Map<String, Object> config = new HashMap<>();
    config.put("config_1", "value_1");
    note.setConfig(config);
    hdfsNotebookRepo.save(note, authInfo);
    assertEquals(1, hdfsNotebookRepo.list(authInfo).size());

    // read this note from hdfs
    Note note_copy = hdfsNotebookRepo.get(note.getId(), authInfo);
    assertEquals(note.getName(), note_copy.getName());
    assertEquals(note.getConfig(), note_copy.getConfig());

    // update this note
    note.setName("title_2");
    hdfsNotebookRepo.save(note, authInfo);
    assertEquals(1, hdfsNotebookRepo.list(authInfo).size());
    note_copy = hdfsNotebookRepo.get(note.getId(), authInfo);
    assertEquals(note.getName(), note_copy.getName());
    assertEquals(note.getConfig(), note_copy.getConfig());

    // delete this note
    hdfsNotebookRepo.remove(note.getId(), authInfo);
    assertEquals(0, hdfsNotebookRepo.list(authInfo).size());
  }

  @Test
  public void testComplicatedScenarios() throws IOException {
    // scenario_1: notebook_dir is not clean. There're some unrecognized dir and file under notebook_dir
    fs.mkdirs(new Path(notebookDir, "1/2"));
    OutputStream out = fs.create(new Path(notebookDir, "1/a.json"));
    out.close();

    assertEquals(0, hdfsNotebookRepo.list(authInfo).size());

    // scenario_2: note_folder is existed.
    // create a new note
    Note note = new Note();
    note.setName("title_1");
    Map<String, Object> config = new HashMap<>();
    config.put("config_1", "value_1");
    note.setConfig(config);

    fs.mkdirs(new Path(notebookDir, note.getId()));
    hdfsNotebookRepo.save(note, authInfo);
    assertEquals(1, hdfsNotebookRepo.list(authInfo).size());
  }
}
