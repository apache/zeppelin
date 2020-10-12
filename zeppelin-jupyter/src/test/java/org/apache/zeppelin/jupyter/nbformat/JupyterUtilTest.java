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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.zeppelin.jupyter.JupyterUtil;
import org.apache.zeppelin.jupyter.zformat.Note;
import org.apache.zeppelin.jupyter.zformat.Paragraph;
import org.apache.zeppelin.jupyter.zformat.TypeData;
import org.junit.Test;

/**
 *
 */
public class JupyterUtilTest {

  @Test
  public void getNbFormat() throws Exception {
    InputStream resource = getClass().getResourceAsStream("/basic.ipynb");
    Nbformat nbformat = new JupyterUtil().getNbformat(new InputStreamReader(resource));
    assertTrue(nbformat.getCells().get(0) instanceof CodeCell);

    resource = getClass().getResourceAsStream("/examples.ipynb");
    nbformat = new JupyterUtil().getNbformat(new InputStreamReader(resource));
  }

  @Test
  public void getNote() throws Exception {
    InputStream resource = getClass().getResourceAsStream("/examples.ipynb");
    Note n = new JupyterUtil().getNote(new InputStreamReader(resource), "", "%python", "%md");
    assertNotNull(n);
  }

  @Test
  public void getNoteAndVerifyData() throws Exception {
    String noteName = "Note converted from Jupyter";
    InputStream resource = getClass().getResourceAsStream("/basic.ipynb");
    Note n = new JupyterUtil().getNote(new InputStreamReader(resource), "", "%python", "%md");
    assertEquals(8, n.getParagraphs().size());
    assertTrue(n.getName().startsWith(noteName));

    Paragraph firstParagraph = n.getParagraphs().get(0);
    assertEquals("%python\nimport numpy as np", firstParagraph.getText());
    assertEquals("FINISHED", firstParagraph.getStatus());
    Map<String, Object> config = firstParagraph.getConfig();

    assertEquals("ace/mode/python", config.get("editorMode"));
    assertFalse((boolean) config.get("editorHide"));

    Paragraph markdownParagraph = n.getParagraphs().get(6);

    assertEquals("%md\n" +
            "<div class=\"alert\" style=\"border: 1px solid #aaa; background: radial-gradient(ellipse at center, #ffffff 50%, #eee 100%);\">\n" +
            "<div class=\"row\">\n" +
            "    <div class=\"col-sm-1\"><img src=\"https://knowledgeanyhow.org/static/images/favicon_32x32.png\" style=\"margin-top: -6px\"/></div>\n" +
            "    <div class=\"col-sm-11\">This notebook was created using <a href=\"https://knowledgeanyhow.org\">IBM Knowledge Anyhow Workbench</a>.  To learn more, visit us at <a href=\"https://knowledgeanyhow.org\">https://knowledgeanyhow.org</a>.</div>\n" +
            "    </div>\n" +
            "</div>", markdownParagraph.getText());
    assertEquals("FINISHED", markdownParagraph.getStatus());

    Map<String, Object> markdownConfig = markdownParagraph.getConfig();
    assertEquals("ace/mode/markdown", markdownConfig.get("editorMode"));
    assertTrue((boolean) markdownConfig.get("editorHide"));
    assertEquals("SUCCESS", markdownParagraph.getResults().getCode());
    List<TypeData> results = markdownParagraph.getResults().getMsg();
    assertEquals("<div class=\"markdown-body\">\n" +
            "<div class=\"alert\" style=\"border: 1px solid #aaa; background: radial-gradient(ellipse at center, #ffffff 50%, #eee 100%);\">\n" +
            "<div class=\"row\">\n" +
            "    <div class=\"col-sm-1\"><img src=\"https://knowledgeanyhow.org/static/images/favicon_32x32.png\" style=\"margin-top: -6px\"/></div>\n" +
            "    <div class=\"col-sm-11\">This notebook was created using <a href=\"https://knowledgeanyhow.org\">IBM Knowledge Anyhow Workbench</a>.  To learn more, visit us at <a href=\"https://knowledgeanyhow.org\">https://knowledgeanyhow.org</a>.</div>\n" +
            "    </div>\n" +
            "</div>\n" +
            "</div>" , results.get(0).getData());
    assertEquals("HTML", results.get(0).getType());
  }

  @Test
  public void testgetNbformat() {
    InputStream resource = getClass().getResourceAsStream("/spark_example_notebook.zpln");
    String text = new BufferedReader(
      new InputStreamReader(resource, StandardCharsets.UTF_8))
        .lines()
        .collect(Collectors.joining("\n"));
    JupyterUtil util = new JupyterUtil();
    Nbformat nbformat = util.getNbformat(new StringReader(util.getNbformat(text)));
    assertEquals(7 , nbformat.getCells().size());
    assertEquals(3 , nbformat.getCells().stream().filter(c -> c instanceof MarkdownCell).count());
    assertEquals(4 , nbformat.getCells().stream().filter(c -> c instanceof CodeCell).count());
  }
}
