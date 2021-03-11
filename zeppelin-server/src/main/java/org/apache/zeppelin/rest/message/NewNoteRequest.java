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
package org.apache.zeppelin.rest.message;

import com.google.gson.Gson;

import java.util.List;

import org.apache.zeppelin.common.JsonSerializable;

/**
 *  NewNoteRequest rest api request message.
 */
public class NewNoteRequest implements JsonSerializable {
  private static final Gson GSON = new Gson();

  //TODO(zjffdu) rename it to be notePath instead of name
  private String name;
  private String defaultInterpreterGroup;
  private boolean addingEmptyParagraph = false;
  private List<NewParagraphRequest> paragraphs;

  public NewNoteRequest (){
  }

  public boolean getAddingEmptyParagraph() {
    return addingEmptyParagraph;
  }

  public String getName() {
    return name;
  }

  public String getDefaultInterpreterGroup() {
    return defaultInterpreterGroup;
  }

  public List<NewParagraphRequest> getParagraphs() {
    return paragraphs;
  }

  public String toJson() {
    return GSON.toJson(this);
  }

  public static NewNoteRequest fromJson(String json) {
    return GSON.fromJson(json, NewNoteRequest.class);
  }
}
