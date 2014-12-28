package com.nflabs.zeppelin.rest.message;

import java.util.List;

import com.nflabs.zeppelin.interpreter.Interpreter;

/**
 * InterpreterSetting information for binding
 */
public class InterpreterSettingListForNoteBind {
  String id;
  String name;
  String group;
  private boolean selected;
  private List<Interpreter> interpreters;
  
  public InterpreterSettingListForNoteBind(String id, String name,
      String group, List<Interpreter> interpreters, boolean selected) {
    super();
    this.id = id;
    this.name = name;
    this.group = group;
    this.interpreters = interpreters;
    this.selected = selected;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getGroup() {
    return group;
  }

  public void setGroup(String group) {
    this.group = group;
  }

  public List<Interpreter> getInterpreterNames() {
    return interpreters;
  }

  public void setInterpreterNames(List<Interpreter> interpreters) {
    this.interpreters = interpreters;
  }

  public boolean isSelected() {
    return selected;
  }

  public void setSelected(boolean selected) {
    this.selected = selected;
  }
  
}
