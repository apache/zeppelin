package org.apache.zeppelin.jupyter.nbformat;

import static org.junit.Assert.assertTrue;

import java.io.InputStream;
import java.io.InputStreamReader;
import org.apache.zeppelin.jupyter.JupyterUtil;
import org.junit.Test;

/**
 *
 */
public class JupyterUtilTest {

  @Test
  public void getNbFormat() {
    InputStream resource = getClass().getResourceAsStream("/basic.ipynb");
    Nbformat nbformat = new JupyterUtil().getNbformat(new InputStreamReader(resource));
    assertTrue(nbformat.getCells().get(0) instanceof CodeCell);

    resource = getClass().getResourceAsStream("/examples.ipynb");
    nbformat = new JupyterUtil().getNbformat(new InputStreamReader(resource));
  }

}
