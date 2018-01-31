package org.apache.zeppelin.sap.universe;

import org.apache.commons.lang.StringUtils;

/**
 * Data of universe query
 */
public class UniverseQuery {
  private String select;
  private String where;
  private UniverseInfo universeInfo;

  public UniverseQuery(String select, String where, UniverseInfo universeInfo) {
    this.select = select;
    this.where = where;
    this.universeInfo = universeInfo;
  }

  public boolean isCorrect() {
    return StringUtils.isNotBlank(select) && universeInfo != null &&
        StringUtils.isNotBlank(universeInfo.getId())
        && StringUtils.isNotBlank(universeInfo.getName());
  }

  public String getSelect() {
    return select;
  }

  public String getWhere() {
    return where;
  }

  public UniverseInfo getUniverseInfo() {
    return universeInfo;
  }
}
