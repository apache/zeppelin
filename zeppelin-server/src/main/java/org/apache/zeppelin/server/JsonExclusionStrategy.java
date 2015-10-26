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

package org.apache.zeppelin.server;

import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import org.apache.zeppelin.interpreter.InterpreterOption;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.Note.ExcludeFromJson;
import org.apache.zeppelin.notebook.Paragraph;
import org.apache.zeppelin.display.AngularObject;

import java.util.List;

/**
 * Created by eranw on 8/30/15.
 * Omit classes from serialization
 */
public class JsonExclusionStrategy implements ExclusionStrategy {

  public boolean shouldSkipClass(Class<?> arg0) {

    return (InterpreterOption.class.equals(arg0) ||
        Paragraph.class.equals(arg0) ||
        AngularObject.class.equals(arg0));
  }

  public boolean shouldSkipField(FieldAttributes f) {

    Boolean shouldSkip = false;
    // Exclude Note angularObjects
    if (f.getDeclaringClass().equals(Note.class) && f.getName().equals("angularObjects")) {
      shouldSkip = true;
      // Exclude Note paragraphs
    } else if (f.getDeclaringClass().equals(Note.class) && f.getName().equals("paragraphs")) {
      shouldSkip = true;
    }
    return shouldSkip;
  }
}
