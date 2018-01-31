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

package org.apache.zeppelin.sap.universe;

import org.apache.commons.lang.StringUtils;

import java.util.*;

/**
 * Util class for convert request from Zeppelin to SAP
 */
public class UniverseUtil {

  private static final String COMPRASION_START_TEMPLATE = "<comparisonFilter path=\"%s\" " +
      "operator=\"%s\" id=\"%s\">\n";
  private static final String COMPRASION_END_TEMPLATE = "</comparisonFilter>\n";
  private static final String COMPARISON_FILTER = "<comparisonFilter id=\"%s\" path=\"%s\" " +
      "operator=\"%s\"/>\n";
  private static final String CONST_OPERAND_START_TEMPLATE = "<constantOperand>\n";
  private static final String CONST_OPERAND_END_TEMPLATE = "</constantOperand>\n";
  private static final String CONST_OPERAND_VALUE_TEMPLATE = "<value>\n" +
      "<caption type=\"%s\">%s</caption>\n </value>\n";
  private static final String PREDEFINED_FILTER_TEMPLATE = "<predefinedFilter path=\"%s\"" +
      " id=\"%s\"/>\n";
  private static final String OBJECT_OPERAND_TEMPLATE = "<objectOperand id=\"%s\" path=\"%s\"/>\n";
  private static final String RESULT_START_TEMPLATE = "<resultObjects>\n";
  private static final String RESULT_END_TEMPLATE = "</resultObjects>\n";
  private static final String RESULT_OBJ_TEMPLATE = "  <resultObject path=\"%s\" id=\"%s\"/>\n";

  public UniverseQuery convertQuery(String text, UniverseClient client, String token)
      throws UniverseException {
    StringBuilder select = new StringBuilder();
    StringBuilder where = new StringBuilder();
    StringBuilder universe = new StringBuilder();
    StringBuilder buf = new StringBuilder();
    StringBuilder condition = new StringBuilder();
    StringBuilder resultObj = new StringBuilder();
    UniverseInfo universeInfo = null;
    boolean doubleQuoteClosed = true;
    boolean singleQuoteClosed = true;
    boolean pathClosed = true;
    boolean universePart = false;
    boolean selectPart = false;
    boolean wherePart = false;
    boolean listValuesPosition = false;
    Map<String, UniverseNodeInfo> nodeInfos = null;
    Deque condOper = new ArrayDeque();
    int level = 1;
    char[] array = text.toCharArray();
    for (int i = 0; i < array.length; i++) {
      char c = array[i];
      buf.append(c);
      if (c == '"' && singleQuoteClosed) {
        doubleQuoteClosed = !doubleQuoteClosed;
      }
      if (c == '\'' && doubleQuoteClosed) {
        singleQuoteClosed = !singleQuoteClosed;
      }
      if (c == '[' && pathClosed && doubleQuoteClosed && singleQuoteClosed) {
        pathClosed = false;
      }
      if (c == ']' && !pathClosed && doubleQuoteClosed && singleQuoteClosed) {
        pathClosed = true;
      }


      if (!universePart && doubleQuoteClosed && singleQuoteClosed
          && buf.toString().toLowerCase().endsWith("universe")) {
        universePart = true;
        continue;
      }

      if (universePart) {
        if (c == ';' && doubleQuoteClosed && singleQuoteClosed) {
          universePart = false;
          if (universe.toString().trim().length() > 2) {
            String universeName =
                universe.toString().trim().substring(1, universe.toString().trim().length() - 1);
            universeInfo = client.getUniverseInfo(universeName);
            nodeInfos = client.getUniverseNodesInfo(token, universeName);
          }
        } else {
          universe.append(c);
        }
        continue;
      }

      if (!selectPart && pathClosed && doubleQuoteClosed && singleQuoteClosed
          && buf.toString().toLowerCase().endsWith("select")) {
        if (StringUtils.isBlank(universe.toString())) {
          throw new UniverseException("Not found universe name");
        }
        selectPart = true;
        select.append(RESULT_START_TEMPLATE);
        continue;
      }

      if (!wherePart && pathClosed && doubleQuoteClosed && singleQuoteClosed
          && (buf.toString().toLowerCase().endsWith("where") || i == array.length - 1)) {
        wherePart = true;
        selectPart = false;
        select.append(parseResultObj(resultObj.toString().replaceAll("(?i)wher$", ""), nodeInfos));
        select.append(RESULT_END_TEMPLATE);
        continue;
      }

      if (selectPart) {
        if (pathClosed && doubleQuoteClosed && singleQuoteClosed && c == ',') {
          select.append(parseResultObj(resultObj.toString(), nodeInfos));
          resultObj = new StringBuilder();
        } else {
          resultObj.append(c);
        }
        continue;
      }

      if (wherePart) {
        if (c == ';' && pathClosed && doubleQuoteClosed && singleQuoteClosed) {
          wherePart = false;
        } else {
          if (pathClosed && doubleQuoteClosed && singleQuoteClosed) {
            if (i < array.length - 2 && text.substring(i, i + 3).equalsIgnoreCase("and")) {
              i += 3;
              if (condOper.size() == 0) {
                condOper.addLast("and");
                where.append(String.format("<and>\n"));
              }
              if (condOper.size() == level) {
                where.append(parseCondition(condition.toString(), nodeInfos));
                condition = new StringBuilder();
              } else {
                condOper.addLast("and");
                where.append(String.format("<and>\n"));
                where.append(parseCondition(condition.toString(), nodeInfos));
                condition = new StringBuilder();
              }
            } else if (i < array.length - 1 && text.substring(i, i + 2).equalsIgnoreCase("or")) {
              i += 2;
              if (condOper.size() == 0) {
                condOper.addLast("or");
                where.append(String.format("<or>\n"));
              }
              if (condOper.size() == level) {
                where.append(parseCondition(condition.toString(), nodeInfos));
                condition = new StringBuilder();
              } else {
                condOper.addLast("or");
                where.append(String.format("<or>\n"));
                where.append(parseCondition(condition.toString(), nodeInfos));
                condition = new StringBuilder();
              }
            } else if (c == '(') {
              if (!condition.toString().matches("(.|\n)*(\\sin\\s*)$")) {
                level++;
              } else {
                listValuesPosition = true;
                condition.append(c);
              }
            } else if (c == ')') {
              if (listValuesPosition) {
                condition.append(c);
                listValuesPosition = false;
              } else {
                level--;
                where.append(String.format("%s</%s>\n",
                    parseCondition(condition.toString(), nodeInfos), condOper.pollLast()));
                condition = new StringBuilder();
              }
            } else {
              condition.append(c);
            }
          } else {
            condition.append(c);
          }
        }
        if (!wherePart || i == array.length - 1) {
          where.append(parseCondition(condition.toString(), nodeInfos));
          if (condOper.size() > 0) {
            where.append(String.format("</%s>", condOper.pollLast()));
          }
        }
      }
    }

    return new UniverseQuery(select.toString().trim(), where.toString().trim(), universeInfo);

  }

  private String parseResultObj(String resultObj, Map<String, UniverseNodeInfo> nodeInfos)
      throws UniverseException {
    UniverseNodeInfo nodeInfo = nodeInfos.get(resultObj.trim());
    if (nodeInfo != null) {
      return String.format(RESULT_OBJ_TEMPLATE, nodeInfo.getNodePath(), nodeInfo.getId());
    }
    throw new UniverseException(String.format("Not found information about: \"%s\"",
        resultObj.trim()));
  }

  private String parseCondition(String condition, Map<String, UniverseNodeInfo> nodeInfos)
      throws UniverseException {
    condition = condition.trim();
    StringBuilder result = new StringBuilder();
    StringBuilder elementBuf = new StringBuilder();
    String element = null;
    char[] condidionCharacters = condition.toCharArray();
    boolean openedPath = false;
    for (int i = 0; i < condidionCharacters.length; i++) {
      char c = condidionCharacters[i];
      elementBuf.append(c);
      if (c == '[') {
        openedPath = true;
        continue;
      }
      if (openedPath && c == ']') {
        openedPath = false;
        if (i == condidionCharacters.length - 1) {
          element = elementBuf.toString().trim();
        }
        continue;
      }
      if ((c == '.' && elementBuf.toString().endsWith("].")) || openedPath) {
        continue;
      }

      element = elementBuf.toString().trim();
      break;
    }

    UniverseNodeInfo nodeInfo = null;
    if (StringUtils.isNotBlank(element)) {
      nodeInfo = nodeInfos.get(element);
    }
    if (nodeInfo != null) {
      if (condition.length() > element.length()) {
        char[] tmp = condition.substring(element.length()).toCharArray();
        boolean possibleObjectOperand = false;
        StringBuilder buf = new StringBuilder();
        String operator = null;
        for (int i = 0; i < tmp.length; i++) {
          buf.append(tmp[i]);
          if (operator == null) {
            switch (buf.toString().toLowerCase().replaceAll("\\s*", "")) {
              case "=":
                operator = "EqualTo";
                possibleObjectOperand = true;
                break;
              case "<":
                if (i + 1 < tmp.length) {
                  if (tmp[i + 1] == '=') {
                    operator = "LessThanOrEqualTo";
                    i++;
                    break;
                  } else if (tmp[i + 1] == '>') {
                    operator = "NotEqualTo";
                    i++;
                    break;
                  }
                }
                possibleObjectOperand = true;
                operator = "LessThan";
                break;
              case ">":
                possibleObjectOperand = true;
                if (i + 1 < tmp.length) {
                  if (tmp[i + 1] == '=') {
                    operator = "GreaterThanOrEqualTo";
                    i++;
                    break;
                  }
                }
                operator = "GreaterThan";
                break;
              case "in":
                operator = "InList";
                break;
              case "notin":
                operator = "NotInList";
                break;
              case "isnull":
                operator = "IsNull";
                break;
              case "isnotnull":
                operator = "IsNotNull";
                break;
            }

            if (operator != null) {
              buf = new StringBuilder();
            }
          }
        }

        if (StringUtils.isNotBlank(operator)) {
          String conditionValue = buf.toString().trim().replaceAll("^\\(|\\)$", "").trim();
          if (operator.equalsIgnoreCase("IsNotNull") || operator.equalsIgnoreCase("IsNull")) {
            result.append(String.format(COMPARISON_FILTER, nodeInfo.getId(), nodeInfo.getNodePath(),
                operator));
          }
          if (!conditionValue.isEmpty()) {
            if (operator.equalsIgnoreCase("InList") || operator.equalsIgnoreCase("NotInList")) {
              tmp = conditionValue.toCharArray();
              boolean startItem = false;
              List<String> values = new ArrayList<>();
              StringBuilder value = new StringBuilder();
              boolean isNumericList = false;
              if (tmp[0] != '\'') {
                isNumericList = true;
              }
              if (isNumericList) {
                String[] nums = conditionValue.split(",");
                for (String num : nums) {
                  values.add(num.trim());
                }
              } else {
                for (int i = 0; i < tmp.length; i++) {
                  char c = tmp[i];
                  if (c == '\'' && (i == 0 || tmp[i - 1] != '\\')) {
                    startItem = !startItem;
                    if (!startItem) {
                      values.add(value.toString());
                      value = new StringBuilder();
                    }
                    continue;
                  }

                  if (startItem) {
                    value.append(c);
                  }
                }
              }

              if (!values.isEmpty()) {
                result.append(String.format(COMPRASION_START_TEMPLATE, nodeInfo.getNodePath(),
                    operator, nodeInfo.getId()));
                result.append(CONST_OPERAND_START_TEMPLATE);
                String type = isNumericList ? "Numeric" : "String";
                for (String v : values) {
                  result.append(String.format(CONST_OPERAND_VALUE_TEMPLATE, type, v));
                }
                result.append(CONST_OPERAND_END_TEMPLATE);
                result.append(COMPRASION_END_TEMPLATE);
              }
            } else if (possibleObjectOperand && conditionValue.startsWith("[")) {
              UniverseNodeInfo operandObject = nodeInfos.get(conditionValue);
              if (operandObject != null) {
                result.append(String.format(COMPRASION_START_TEMPLATE, nodeInfo.getNodePath(),
                    operator, nodeInfo.getId()));
                result.append(String.format(OBJECT_OPERAND_TEMPLATE, operandObject.getId(),
                    operandObject.getNodePath()));
                result.append(COMPRASION_END_TEMPLATE);
              }
            } else {
              String value = conditionValue.replaceAll("^'|'$", "").trim();
              if (StringUtils.isNotBlank(value)) {
                String type = conditionValue.length() == value.length() ? "Numeric" : "String";
                result.append(String.format(COMPRASION_START_TEMPLATE, nodeInfo.getNodePath(),
                    operator, nodeInfo.getId()));
                result.append(CONST_OPERAND_START_TEMPLATE);
                result.append(String.format(CONST_OPERAND_VALUE_TEMPLATE, type, value));
                result.append(CONST_OPERAND_END_TEMPLATE);
                result.append(COMPRASION_END_TEMPLATE);
              }
            }
          }
        }
      } else {
        result.append(String.format(PREDEFINED_FILTER_TEMPLATE, nodeInfo.getNodePath(),
            nodeInfo.getId()));
      }
    } else {
      throw new UniverseException(String.format("Not found information about: \"%s\"",
          condition.trim()));
    }

    if (StringUtils.isBlank(result.toString())) {
      throw new UniverseException(String.format("Condition error: \"%s\"", condition.trim()));
    }
    return result.toString();
  }
}
