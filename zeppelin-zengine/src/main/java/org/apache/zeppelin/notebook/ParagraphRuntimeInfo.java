package org.apache.zeppelin.notebook;

import com.google.common.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Store runtime infos of each para
 *
 */
public class ParagraphRuntimeInfo {
  private final String propertyName;  // Name of the property
  private final String label;         // Label to be used in UI
  private final String tooltip;       // Tooltip text toshow in UI
  private final String group;         // The interpretergroup from which the info was derived

  // runtimeInfos job url or dropdown-menu key in
  // zeppelin-web/src/app/notebook/paragraph/paragraph-control.html
  // Use ConcurrentLinkedQueue to make ParagraphRuntimeInfo thread-safe which is required by
  // Note/Paragraph serialization (saving note to NotebookRepo or broadcast to frontend), see ZEPPELIN-5530.
  private final ConcurrentLinkedQueue<Object> values;  // values for the key-value pair property
  private final String interpreterSettingId;
  
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
    this.values = new ConcurrentLinkedQueue();
  }

  public void addValue(Map<String, String> mapValue) {
    values.add(mapValue);
  }

  @VisibleForTesting
  public Queue<Object> getValue() {
    return values;
  }
  
  public String getInterpreterSettingId() {
    return interpreterSettingId;
  }
}
