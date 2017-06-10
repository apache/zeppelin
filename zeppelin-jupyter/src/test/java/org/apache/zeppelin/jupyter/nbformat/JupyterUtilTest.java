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
package org.apache.zeppelin.jupyter.nbformat;

import static org.junit.Assert.assertTrue;

import java.io.InputStream;
import java.io.InputStreamReader;
import org.apache.zeppelin.jupyter.JupyterUtil;
import org.apache.zeppelin.jupyter.zformat.Note;
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

  @Test
  public void getNote() {
    InputStream resource = getClass().getResourceAsStream("/examples.ipynb");
    Note n = new JupyterUtil().getNote(new InputStreamReader(resource), "%python", "%md");
  }

}
