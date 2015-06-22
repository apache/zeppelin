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

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.Interpreter.RegisteredInterpreter;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterFactory;
import org.apache.zeppelin.interpreter.InterpreterGroup;
import org.apache.zeppelin.interpreter.InterpreterSetting;

/**
 * Repl loader per note.
 */
public class NoteInterpreterLoader {
  private transient InterpreterFactory factory;
  String noteId;

  public NoteInterpreterLoader(InterpreterFactory factory) {
    this.factory = factory;
  }

  public void setNoteId(String noteId) {
    this.noteId = noteId;
  }

  /**
   * set interpreter ids
   * @param ids InterpreterSetting id list
   * @throws IOException
   */
  public void setInterpreters(List<String> ids) throws IOException {
    factory.putNoteInterpreterSettingBinding(noteId, ids);
  }

  public List<String> getInterpreters() {
    return factory.getNoteInterpreterSettingBinding(noteId);
  }

  public List<InterpreterSetting> getInterpreterSettings() {
    List<String> interpreterSettingIds = factory.getNoteInterpreterSettingBinding(noteId);
    LinkedList<InterpreterSetting> settings = new LinkedList<InterpreterSetting>();
    synchronized (interpreterSettingIds) {
      for (String id : interpreterSettingIds) {
        InterpreterSetting setting = factory.get(id);
        if (setting == null) {
          // interpreter setting is removed from factory. remove id from here, too
          interpreterSettingIds.remove(id);
        } else {
          settings.add(setting);
        }
      }
    }
    return settings;
  }

  public Interpreter get(String replName) {
    List<InterpreterSetting> settings = getInterpreterSettings();

    if (settings == null || settings.size() == 0) {
      return null;
    }

    if (replName == null || replName.trim().length() == 0) {
      return settings.get(0).getInterpreterGroup().getFirst();
    }

    if (Interpreter.registeredInterpreters == null) {
      return null;
    }

    String[] replNameSplit = replName.split("\\.");
    String group = null;
    String name = null;
    if (replNameSplit.length == 2) {
      group = replNameSplit[0];
      name = replNameSplit[1];

      Interpreter.RegisteredInterpreter registeredInterpreter = Interpreter.registeredInterpreters
          .get(group + "." + name);
      if (registeredInterpreter == null
          || registeredInterpreter.getClassName() == null) {
        throw new InterpreterException(replName + " interpreter not found");
      }
      String interpreterClassName = registeredInterpreter.getClassName();

      for (InterpreterSetting setting : settings) {
        InterpreterGroup intpGroup = setting.getInterpreterGroup();
        for (Interpreter interpreter : intpGroup) {
          if (interpreterClassName.equals(interpreter.getClassName())) {
            return interpreter;
          }
        }
      }
    } else {
      // first assume replName is 'name' of interpreter. ('groupName' is ommitted)
      // search 'name' from first (default) interpreter group
      InterpreterGroup intpGroup = settings.get(0).getInterpreterGroup();
      for (Interpreter interpreter : intpGroup) {
        RegisteredInterpreter intp = Interpreter
            .findRegisteredInterpreterByClassName(interpreter.getClassName());
        if (intp == null) {
          continue;
        }

        if (intp.getName().equals(replName)) {
          return interpreter;
        }
      }


      // next, assume replName is 'group' of interpreter ('name' is ommitted)
      // search interpreter group and return first interpreter.
      for (InterpreterSetting setting : settings) {
        intpGroup = setting.getInterpreterGroup();
        Interpreter interpreter = intpGroup.get(0);
        RegisteredInterpreter intp = Interpreter
            .findRegisteredInterpreterByClassName(interpreter.getClassName());
        if (intp == null) {
          continue;
        }

        if (intp.getGroup().equals(replName)) {
          return interpreter;
        }
      }
    }

    throw new InterpreterException(replName + " interpreter not found");
  }
}
