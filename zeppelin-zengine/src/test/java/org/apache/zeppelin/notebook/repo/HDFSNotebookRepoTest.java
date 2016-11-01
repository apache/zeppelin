package org.apache.zeppelin.notebook.repo;


import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;

public class HDFSNotebookRepoTest {
  @Test
  public void removeProtocolTest() {
    String hdfsUrlNoProtocol1 = "/user/foo/notebook";
    String hdfsUrlNoProtocol2 = "/user/foo/notebook/";
    String hdfsUrlWithProtocol1 = "hdfs://namenode/user/foo/notebook";
    String hdfsUrlWithProtocol2 = "hdfs://dummyhost:8020/user/foo/notebook";

    ZeppelinConfiguration conf = new ZeppelinConfiguration();
    HDFSNotebookRepo repo = null;
    try {
      repo = new HDFSNotebookRepo(conf);
    } catch (IOException e) {
      e.printStackTrace();
    }
    assertEquals("hdfsUrlNoProtocol1", "/user/foo/notebook", repo.removeProtocol(hdfsUrlNoProtocol1));
    assertEquals("hdfsUrlNoProtocol2", "/user/foo/notebook", repo.removeProtocol(hdfsUrlNoProtocol2));
    assertEquals("hdfsUrlWithProtocol1", "/user/foo/notebook", repo.removeProtocol(hdfsUrlWithProtocol1));
    assertEquals("hdfsUrlWithProtocol2", "/user/foo/notebook", repo.removeProtocol(hdfsUrlWithProtocol2));
  }
}
