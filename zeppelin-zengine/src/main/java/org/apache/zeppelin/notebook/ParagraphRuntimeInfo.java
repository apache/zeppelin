package org.apache.zeppelin.notebook;

import com.google.common.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Store runtime infos of each para
 *
 */
public class ParagraphRuntimeInfo {
  private String propertyName;  // Name of the property
  private String label;         // Label to be used in UI
  private String tooltip;       // Tooltip text toshow in UI
  private String group;         // The interpretergroup from which the info was derived

  // runtimeInfos job url or dropdown-menu key in
  // zeppelin-web/src/app/notebook/paragraph/paragraph-control.html
  private List<Map<String, String>> values;  // values for the key-value pair property
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

  public void addValue(Map<String, String> mapValue) {
    values.add(mapValue);
  }

  @VisibleForTesting
  public List<Map<String, String>> getValue() {
    return values;
  }
  
  public String getInterpreterSettingId() {
    return interpreterSettingId;
  }
}
