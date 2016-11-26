package org.apache.zeppelin.notebook;

import java.util.ArrayList;
import java.util.List;

/**
 * 
 *
 */
public class ParagraphRuntimeInfos {

  String propertyName;
  String label;
  String group;
  List<String> values;

  public ParagraphRuntimeInfos(String propertyName, String label, String group) {
    this.propertyName = propertyName;
    this.label = label;
    this.group = group;
  }

  public void addValue(String value) {
    if (values == null) {
      values = new ArrayList<>();
    }
    values.add(value);
  }
}
