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

import java.util.Arrays;
import java.util.List;

/**
 *
 */
public class Error extends Output {
  @SerializedName("ename")
  private String ename;

  @SerializedName("evalue")
  private String evalue;

  @SerializedName("traceback")
  private List<String> traceback;

  public String getEname() {
    return ename;
  }

  public String getEvalue() {
    return evalue;
  }

  public List<String> getTraceback() {
    return traceback;
  }

  @Override
  public ZeppelinOutputType getTypeOfZeppelin() {
    return ZeppelinOutputType.TEXT;
  }

  @Override
  public TypeData toZeppelinResult() {
    List<String> text = verifyEndOfLine(Arrays.asList(getEname(), getEvalue()));
    String result = Joiner.on("").join(text);
    return new TypeData(getTypeOfZeppelin().toString(), result);
  }
}
