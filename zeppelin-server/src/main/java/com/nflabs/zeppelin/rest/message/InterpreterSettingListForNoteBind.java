package com.nflabs.zeppelin.rest.message;

public class InterpreterSettingListForNoteBind {
  String id;
  String name;
  String group;
  private boolean selected;
  
  public InterpreterSettingListForNoteBind(String id, String name, String group, boolean selected) {
    super();
    this.id = id;
    this.name = name;
    this.group = group;
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

  public boolean isSelected() {
    return selected;
  }

  public void setSelected(boolean selected) {
    this.selected = selected;
  }
  
}
