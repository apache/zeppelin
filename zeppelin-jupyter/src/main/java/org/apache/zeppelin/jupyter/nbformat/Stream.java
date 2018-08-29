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
import org.apache.zeppelin.jupyter.types.ZeppelinOutputType;
import org.apache.zeppelin.jupyter.zformat.TypeData;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 *
 */
public class Stream extends Output {

  @SerializedName("name")
  private String name;

  @SerializedName("text")
  private Object text;

  public List<String> getText() {
    List<String> textList = new ArrayList<>();
    if (text instanceof String) {
      textList.add((String) text);
    } else {
      textList = (List<String>) text;
    }
    return textList;
  }

  public boolean isError() {
    if (name == null) {
      return true;
    }
    return name.equals("stderr");
  }

  @Override
  public ZeppelinOutputType getTypeOfZeppelin() {
    return ZeppelinOutputType.TEXT;
  }

  @Override
  public TypeData toZeppelinResult() {
    List<String> text = verifyEndOfLine(getText());
    String result = Joiner.on("").join(text);
    return new TypeData(getTypeOfZeppelin().toString(), result);
  }
}
