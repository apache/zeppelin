package com.nflabs.zeppelin.notebook;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nflabs.zeppelin.interpreter.Interpreter;
import com.nflabs.zeppelin.interpreter.InterpreterException;
import com.nflabs.zeppelin.interpreter.InterpreterFactory;
import com.nflabs.zeppelin.interpreter.InterpreterSetting;
import com.nflabs.zeppelin.interpreter.LazyOpenInterpreter;

/**
 * Repl loader per note.
 */
public class NoteInterpreterLoader {
  static transient Logger logger = LoggerFactory.getLogger(NoteInterpreterLoader.class);
  private transient InterpreterFactory factory;
  List<String> interpreterSettingIds;
  
  public NoteInterpreterLoader(InterpreterFactory factory) {
    this.factory = factory;
    interpreterSettingIds = Collections.synchronizedList(new LinkedList<String>());
    setInterpreters(factory.getDefaultInterpreterList());
  }
  
  /**
   * set interpreter ids
   * @param ids InterpreterSetting id list
   */
  public void setInterpreters(List<String> ids) {
    synchronized (interpreterSettingIds) {
      interpreterSettingIds.clear();
      interpreterSettingIds.addAll(ids);
    }
  }
  
  public List<InterpreterSetting> getInterpreterSettings() {
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
    
    if (replName == null) {
      return settings.get(0).getInterpreter();
    }
    
    for (InterpreterSetting setting : settings) {
      if (setting.getName().equals(replName)) {
        return setting.getInterpreter();
      }
    }
    
    return null;
  }
}
