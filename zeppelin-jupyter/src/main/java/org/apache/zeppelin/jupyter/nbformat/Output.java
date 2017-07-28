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

import com.google.common.base.Joiner;
import com.google.gson.annotations.SerializedName;
import org.apache.zeppelin.jupyter.types.JupyterOutputType;
import org.apache.zeppelin.jupyter.types.ZeppelinOutputType;
import org.apache.zeppelin.jupyter.zformat.TypeData;

import java.util.List;
import java.util.Map;

/**
 *
 */
public abstract class Output {

  @SerializedName("output_type")
  private String outputType;

  private final transient String lineSeparator = System.lineSeparator();

  protected List<String> verifyEndOfLine(List<String> content) {
    if (null == content || content.size() == 1) {
      // one-liners don't have line separator
      return content;
    }
    for (int i = 0; i < content.size(); i++) {
      String line = content.get(i);
      // verify to end with line separator except the last element
      if (null != line && !line.endsWith(lineSeparator) && i != (content.size() - 1)) {
        content.set(i, line + lineSeparator);
      }
    }
    return content;
  }

  protected JupyterOutputType getType(Map<String, Object> data) {
    JupyterOutputType jupyterOutputType = JupyterOutputType.TEXT_PLAIN;

    if (data == null) {
      return null;
    }

    for (String dataType : data.keySet()) {
      if (!dataType.equals(JupyterOutputType.TEXT_PLAIN.toString())) {
        try {
          jupyterOutputType = JupyterOutputType.valueOf(dataType);
        } catch (IllegalArgumentException e) {
          // pass
        }
      }
    }

    return jupyterOutputType;
  }


  protected TypeData getZeppelinResult(Map<String, Object> data, JupyterOutputType type) {
    TypeData result = null;

    if (type == JupyterOutputType.IMAGE_PNG) {
      String base64Code = (String) data.get(type.toString());
      result = new TypeData(
              type.getZeppelinType().toString(),
              ZeppelinResultGenerator.toBase64ImageHtmlElement(base64Code)
      );
    } else {
      List<String> outputsRaw = (List<String>) data.get(type.toString());
      List<String> outputs = verifyEndOfLine(outputsRaw);
      String outputData = Joiner.on("").join(outputs);
      if (type == JupyterOutputType.LATEX) {
        result = new TypeData(
                type.getZeppelinType().toString(),
                ZeppelinResultGenerator.toLatex(outputData)
        );
      } else if (type == JupyterOutputType.TEXT_PLAIN) {
        result = new TypeData(type.getZeppelinType().toString(), outputData);
      }
    }
    return result;
  }

  public abstract ZeppelinOutputType getTypeOfZeppelin();
  public abstract TypeData toZeppelinResult();

  private static class ZeppelinResultGenerator {
    public static String toBase64ImageHtmlElement(String image) {
      return "<div style='width:auto;height:auto'><img src=data:image/png;base64," + image
              + " style='width=auto;height:auto'/></div>";
    }
    public static String toLatex(String latexCode) {
      String latexContents = latexCode;
      return "<div>" +
              "<div class='class=\"alert alert-warning\"'>" +
              "<strong>Warning!</strong> Currently, Latex is not supported." +
              "</div>" +
              "<div>" + latexContents + "</div>" +
              "</div>";
    }
  }
}
