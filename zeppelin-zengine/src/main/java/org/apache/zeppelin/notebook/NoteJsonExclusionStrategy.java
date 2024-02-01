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

package org.apache.zeppelin.notebook;

import java.util.HashSet;
import java.util.Set;

import org.apache.zeppelin.conf.ZeppelinConfiguration;

import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;

public class NoteJsonExclusionStrategy implements ExclusionStrategy {
  private Set<String> noteExcludeFields = new HashSet<>();
  private Set<String> paragraphExcludeFields = new HashSet<>();

  public NoteJsonExclusionStrategy(ZeppelinConfiguration zConf) {
    String[] excludeFields = zConf.getNoteFileExcludedFields();
    for (String field : excludeFields) {
      if (field.startsWith("Paragraph")) {
        paragraphExcludeFields.add(field.substring(10));
      } else {
        noteExcludeFields.add(field);
      }
    }
  }

  @Override
  public boolean shouldSkipField(FieldAttributes field) {
    if (field.getName().equals("path")) {
      return true;
    }
    if (field.getDeclaringClass().equals(Paragraph.class)) {
      return paragraphExcludeFields.contains(field.getName());
    } else {
      return noteExcludeFields.contains(field.getName());
    }
  }

  @Override
  public boolean shouldSkipClass(Class<?> aClass) {
    return false;
  }
}
