package org.apache.zeppelin.notebook;

import java.util.ArrayList;
import java.util.List;

/**
 * Store runtime infos of each para
 *
 */
public class ParagraphRuntimeInfo {

  private String propertyName;  // Name of the property
  private String label;         // Label to be used in UI
  private String tooltip;       // Tooltip text toshow in UI
  private String group;         // The interpretergroup from which the info was derived
  private List<String> values;  // values for the property
  private String interpreterSettingId;
  
  public ParagraphRuntimeInfo(String propertyName, String label, 
      String tooltip, String group, String intpSettingId) {
    if (intpSettingId == null) {
      throw new IllegalArgumentException("Interpreter setting Id cannot be null");
    }
    this.propertyName = propertyName;
    this.label = label;
    this.tooltip = tooltip;
    this.group = group;
    this.interpreterSettingId = intpSettingId;
    this.values = new ArrayList<>();
  }

  public void addValue(String value) {
    values.add(value);
  }
  
  public String getInterpreterSettingId() {
    return interpreterSettingId;
  }
}
