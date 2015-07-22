package org.apache.zeppelin.notebook.repo.mock;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import org.apache.commons.vfs2.VFS;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.conf.ZeppelinConfiguration.ConfVars;
import org.apache.zeppelin.notebook.repo.VFSNotebookRepo;

public class VFSNotebookRepoMock extends VFSNotebookRepo {
  
  private static ZeppelinConfiguration modifyNotebookDir(ZeppelinConfiguration conf) {
    String secNotebookDir = conf.getNotebookDir() + "_secondary";
    System.setProperty(ConfVars.ZEPPELIN_NOTEBOOK_DIR.getVarName(), secNotebookDir);
    ZeppelinConfiguration secConf = ZeppelinConfiguration.create();
    return secConf;
  }

  public VFSNotebookRepoMock(ZeppelinConfiguration conf) throws IOException {
    super(modifyNotebookDir(conf));
  }
  
}
