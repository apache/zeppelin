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
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
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
    Note n = new JupyterUtil().getNote(new InputStreamReader(resource), "%python", "%md");
  }

  @Test
  public void getNoteAndVerifyData() throws Exception {
    String noteName = "Note converted from Jupyter";
    InputStream resource = getClass().getResourceAsStream("/basic.ipynb");
    Note n = new JupyterUtil().getNote(new InputStreamReader(resource), "%python", "%md");
    Gson gson = new Gson();
    System.out.println(gson.toJson(n));
    System.out.println(n.getParagraphs().size());
    assertTrue(n.getParagraphs().size() == 8);
    assertTrue(noteName.equals(n.getName()));

    Paragraph firstParagraph = n.getParagraphs().get(0);
    assertTrue(firstParagraph.getText().equals("%python\nimport numpy as np"));
    assertTrue(firstParagraph.getStatus().equals("FINISHED"));
    Map<String, Object> config = firstParagraph.getConfig();

    assertTrue(((String) config.get("editorMode")).equals("ace/mode/python"));
    assertTrue(((boolean) config.get("editorHide")) == false);

    Paragraph markdownParagraph = n.getParagraphs().get(6);

    assertTrue(markdownParagraph.getText().equals("%md\n" +
            "<div class=\"alert\" style=\"border: 1px solid #aaa; background: radial-gradient(ellipse at center, #ffffff 50%, #eee 100%);\">\n" +
            "<div class=\"row\">\n" +
            "    <div class=\"col-sm-1\"><img src=\"https://knowledgeanyhow.org/static/images/favicon_32x32.png\" style=\"margin-top: -6px\"/></div>\n" +
            "    <div class=\"col-sm-11\">This notebook was created using <a href=\"https://knowledgeanyhow.org\">IBM Knowledge Anyhow Workbench</a>.  To learn more, visit us at <a href=\"https://knowledgeanyhow.org\">https://knowledgeanyhow.org</a>.</div>\n" +
            "    </div>\n" +
            "</div>"));
    assertTrue(markdownParagraph.getStatus().equals("FINISHED"));

    Map<String, Object> markdownConfig = markdownParagraph.getConfig();
    assertTrue(((String) markdownConfig.get("editorMode")).equals("ace/mode/markdown"));
    assertTrue(((boolean) markdownConfig.get("editorHide")) == true);
    assertTrue(markdownParagraph.getResults().getCode().equals("SUCCESS"));
    List<TypeData> results = markdownParagraph.getResults().getMsg();
    assertTrue(results.get(0).getData().equals("<div class=\"markdown-body\">\n" +
            "<div class=\"alert\" style=\"border: 1px solid #aaa; background: radial-gradient(ellipse at center, #ffffff 50%, #eee 100%);\">\n" +
            "<div class=\"row\">\n" +
            "    <div class=\"col-sm-1\"><img src=\"https://knowledgeanyhow.org/static/images/favicon_32x32.png\" style=\"margin-top: -6px\"/></div>\n" +
            "    <div class=\"col-sm-11\">This notebook was created using <a href=\"https://knowledgeanyhow.org\">IBM Knowledge Anyhow Workbench</a>.  To learn more, visit us at <a href=\"https://knowledgeanyhow.org\">https://knowledgeanyhow.org</a>.</div>\n" +
            "    </div>\n" +
            "</div>\n" +
            "</div>"));
    assertTrue(results.get(0).getType().equals("HTML"));
  }
}
