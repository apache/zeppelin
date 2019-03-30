package org.apache.zeppelin.notebook.repo;

import static org.junit.Assert.assertArrayEquals;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import java.util.Arrays;
import java.util.Collection;


@RunWith(Parameterized.class)
public class ToPathArrayTest {

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  private MongoNotebookRepo repo = new MongoNotebookRepo();

  private String pathStr;

  private boolean includeLast;

  private String[] expactPathArray;

  public ToPathArrayTest(String pathStr, boolean includeLast, String[] expactPathArray) {
    this.pathStr = pathStr;
    this.includeLast = includeLast;
    this.expactPathArray = expactPathArray;
  }

  @Parameterized.Parameters
  public static Collection params() {
    Object[][] arrs = {
        {null, true, null},
        {null, false, null},
        {"", true, null},
        {"", false, null},
        {"/", true, new String[0]},
        {"/", false, new String[0]},

        {"/abc", true, new String[]{"abc"}},
        {"/abc/", true, new String[]{"abc"}},
        {"/a/b/c", true, new String[]{"a", "b", "c"}},
        {"/a/b//c/", true, new String[]{"a", "b", "c"}},

        {"/abc", false, new String[]{}},
        {"/abc/", false, new String[]{}},
        {"/a/b/c", false, new String[]{"a", "b"}},
        {"/a/b//c/", false, new String[]{"a", "b"}},
    };
    return Arrays.asList(arrs);
  }

  @Test
  public void runTest() {
    if (expactPathArray == null) {
      runForThrow();
    } else {
      runNormally();
    }
  }

  private void runForThrow() {
    thrown.expect(NullPointerException.class);
    runNormally();
  }

  private void runNormally() {
    String[] pathArray = repo.toPathArray(pathStr, includeLast);
    assertArrayEquals(expactPathArray, pathArray);
  }
}
