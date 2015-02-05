package com.nflabs.zeppelin.spark;

import java.io.File;
import java.util.Properties;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.nflabs.zeppelin.interpreter.InterpreterContext;
import com.nflabs.zeppelin.interpreter.InterpreterResult;
import com.nflabs.zeppelin.notebook.Paragraph;

public class DepInterpreterTest {
  private DepInterpreter dep;
  private InterpreterContext context;
  private File tmpDir;

  @Before
  public void setUp() throws Exception {
    tmpDir = new File(System.getProperty("java.io.tmpdir") + "/ZeppelinLTest_" + System.currentTimeMillis());
    System.setProperty("zeppelin.dep.localrepo", tmpDir.getAbsolutePath() + "/local-repo");

    tmpDir.mkdirs();

    Properties p = new Properties();

    dep = new DepInterpreter(p);
    dep.open();

    context = new InterpreterContext(new Paragraph(null, null));
  }

  @After
  public void tearDown() throws Exception {
    dep.close();
    delete(tmpDir);
  }

  private void delete(File file) {
    if (file.isFile()) file.delete();
    else if (file.isDirectory()) {
      File[] files = file.listFiles();
      if (files != null && files.length > 0) {
        for (File f : files) {
          delete(f);
        }
      }
      file.delete();
    }
  }

  @Test
  public void testBasic() {
    //repl.interpret("z.load(\"org.apache.commons:commons-csv:1.1\")", context);
    //assertEquals(InterpreterResult.Code.SUCCESS, repl.interpret("import org.apache.commons.csv.CSVFormat", context).code());

    InterpreterResult ret = dep.interpret("z.load(\"org.apache.commons:commons-csv:1.1\")", context);
    System.out.println("ret="+ret.message());
  }

}
