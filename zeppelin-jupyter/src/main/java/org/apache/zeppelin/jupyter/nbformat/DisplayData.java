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
import com.google.common.collect.Lists;
import com.google.gson.annotations.SerializedName;
import org.apache.zeppelin.jupyter.types.JupyterOutputType;
import org.apache.zeppelin.jupyter.types.ZeppelinOutputType;
import org.apache.zeppelin.jupyter.zformat.TypeData;

import java.util.List;
import java.util.Map;

/**
 *
 */
public class DisplayData extends Output {

  @SerializedName("data")
  private Map<String, Object> data;

  public Map<String, Object> getData() {
    return data;
  }

  @Override
  public ZeppelinOutputType getTypeOfZeppelin() {
    return getType(data).getZeppelinType();
  }

  @Override
  public TypeData toZeppelinResult() {
    return getZeppelinResult(data, getType(data));
  }

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
