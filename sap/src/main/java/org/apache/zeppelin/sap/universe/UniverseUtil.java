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

import jline.console.completer.ArgumentCompleter.WhitespaceArgumentDelimiter;
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
      "<caption type=\"%s\">%s</caption>\n</value>\n";
  private static final String PREDEFINED_FILTER_TEMPLATE = "<predefinedFilter path=\"%s\"" +
      " id=\"%s\"/>\n";
  private static final String OBJECT_OPERAND_TEMPLATE = "<objectOperand id=\"%s\" path=\"%s\"/>\n";
  private static final String RESULT_START_TEMPLATE = "<resultObjects>\n";
  private static final String RESULT_END_TEMPLATE = "</resultObjects>\n";
  private static final String RESULT_OBJ_TEMPLATE = "<resultObject path=\"%s\" id=\"%s\"/>\n";

  private static final String MARKER_EQUAL = "#EqualTo#";
  private static final String MARKER_LESS_EQUAL = "#LessThanOrEqualTo#";
  private static final String MARKER_NOT_EQUAL = "#NotEqualTo#";
  private static final String MARKER_LESS = "#LessThan#";
  private static final String MARKER_GREATER_EQUALS = "#GreaterThanOrEqualTo#";
  private static final String MARKER_GREATER = "#GreaterThan#";
  private static final String MARKER_IN = "#InList#";
  private static final String MARKER_NOT_IN = "#NotInList#";
  private static final String MARKER_NULL = "#IsNull#";
  private static final String MARKER_NOT_NULL = "#IsNotNull#";
  private static final String MARKER_FILTER = "#filter#";
  private static final String MARKER_AND = "#and#";
  private static final String MARKER_OR = "#or#";
  private static final String MARKER_BACKSPACE = "#backspace#";
  private static final String MARKER_LEFT_BRACE = "#left_brace#";
  private static final String MARKER_RIGHT_BRACE = "#right_brace#";


  private static final String LEFT_BRACE = "(";
  private static final String RIGHT_BRACE = ")";

  public static final Map<String, Integer> OPERATIONS;
  private static final WhitespaceArgumentDelimiter delimiter = new WhitespaceArgumentDelimiter();

  static {
    OPERATIONS = new HashMap<>();
    OPERATIONS.put(MARKER_EQUAL, 1);
    OPERATIONS.put(MARKER_LESS_EQUAL, 1);
    OPERATIONS.put(MARKER_NOT_EQUAL, 1);
    OPERATIONS.put(MARKER_LESS, 1);
    OPERATIONS.put(MARKER_GREATER_EQUALS, 1);
    OPERATIONS.put(MARKER_GREATER, 1);
    OPERATIONS.put(MARKER_IN, 1);
    OPERATIONS.put(MARKER_NOT_IN, 1);
    OPERATIONS.put(MARKER_NULL, 1);
    OPERATIONS.put(MARKER_NOT_NULL, 1);
    OPERATIONS.put(MARKER_FILTER, 1);
    OPERATIONS.put(MARKER_AND, 2);
    OPERATIONS.put(MARKER_OR, 3);
  }

  public static OptionalInt parseInt(String toParse) {
    try {
      return OptionalInt.of(Integer.parseInt(toParse));
    } catch (NumberFormatException e) {
      return OptionalInt.empty();
    }
  }

  public UniverseQuery convertQuery(String text, UniverseClient client, String token)
      throws UniverseException {
    StringBuilder select = new StringBuilder();
    StringBuilder universe = new StringBuilder();
    StringBuilder buf = new StringBuilder();
    StringBuilder resultObj = new StringBuilder();
    StringBuilder whereBuf = new StringBuilder();
    UniverseInfo universeInfo = null;
    String where = null;
    boolean singleQuoteClosed = true;
    boolean pathClosed = true;
    boolean universePart = false;
    boolean selectPart = false;
    boolean wherePart = false;
    boolean listOperator = false;
    boolean operatorPosition = false;
    boolean duplicatedRows = true;
    Map<String, UniverseNodeInfo> nodeInfos = null;
    OptionalInt limit = OptionalInt.empty();

    final int limitIndex = text.lastIndexOf("limit");
    if (limitIndex != -1) {
      final String[] arguments = delimiter.delimit(text, 0).getArguments();
      final int length = arguments.length;
      if (arguments[length - 3].equals("limit")) {
        limit = parseInt(arguments[length - 2]);
      } else if (arguments[length - 2].equals("limit")) {
        final String toParse = arguments[length - 1];
        limit = parseInt(toParse.endsWith(";") ?
            toParse.substring(0, toParse.length() - 1) : toParse);
      }
      text = text.substring(0, limitIndex);
    }

    if (!text.endsWith(";")) {
      text += ";";
    }

    char[] array = text.toCharArray();
    for (int i = 0; i < array.length; i++) {
      char c = array[i];
      buf.append(c);
      if (c == '\'') {
        if (i == 0 || array[i - 1] != '\\') {
          singleQuoteClosed = !singleQuoteClosed;
        }
      }
      if (c == '[' && pathClosed && singleQuoteClosed) {
        pathClosed = false;
        if (wherePart) {
          operatorPosition = false;
        }
      }
      if (c == ']' && !pathClosed && singleQuoteClosed) {
        pathClosed = true;
        if (wherePart) {
          operatorPosition = true;
          if (i + 1 == array.length || (array[i + 1] != '.'
              && isFilter(String.format("%s]", whereBuf.toString()), text.substring(i + 1)))) {
            whereBuf.append(c);
            whereBuf.append(MARKER_FILTER);
            if (i + 1 == array.length) {
              wherePart = false;
              where = parseWhere(whereBuf.toString(), nodeInfos);
            }
            continue;
          }
        }
      }
      if (c == '(' && wherePart && pathClosed && singleQuoteClosed) {
        if (listOperator) {
          whereBuf.append(MARKER_LEFT_BRACE);
          continue;
        } else {
          whereBuf.append(c);
          continue;
        }
      }
      if (c == ')' && wherePart && pathClosed && singleQuoteClosed) {
        if (listOperator) {
          whereBuf.append(MARKER_RIGHT_BRACE);
          listOperator = false;
          continue;
        } else {
          whereBuf.append(c);
          continue;
        }
      }

      if (!universePart && singleQuoteClosed
          && buf.toString().toLowerCase().endsWith("universe")) {
        universePart = true;
        continue;
      }

      if (universePart) {
        if (c == ';' && singleQuoteClosed) {
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

      if (!selectPart && pathClosed && singleQuoteClosed
          && buf.toString().toLowerCase().endsWith("select")) {
        if (StringUtils.isBlank(universe.toString())) {
          throw new UniverseException("Not found universe name");
        }
        selectPart = true;
        select.append(RESULT_START_TEMPLATE);
        continue;
      }

      if (!wherePart && pathClosed && singleQuoteClosed) {
        if (buf.toString().toLowerCase().endsWith("where")) {
          wherePart = true;
        }
        if (buf.toString().toLowerCase().endsWith("where") || i == array.length - 1) {
          selectPart = false;
          select.append(parseResultObj(resultObj.toString()
                  .replaceAll("(?i)wher$", "").replaceAll("(?i)distinc", ""),
              nodeInfos));
          select.append(RESULT_END_TEMPLATE);
          continue;
        }
      }

      if (selectPart) {
        if (pathClosed && singleQuoteClosed && buf.toString().toLowerCase().endsWith("distinct")) {
          duplicatedRows = false;
          continue;
        }
        if (pathClosed && singleQuoteClosed && c == ',') {
          select.append(parseResultObj(resultObj.toString().replaceAll("(?i)distinc", ""), nodeInfos));
          resultObj = new StringBuilder();
        } else {
          resultObj.append(c);
        }
        continue;
      }

      if (wherePart) {
        if (c == ';' && pathClosed && singleQuoteClosed) { // todo: check for limit
          wherePart = false;
          where = parseWhere(whereBuf.toString(), nodeInfos);
        } else {
          if (!singleQuoteClosed || !pathClosed) {
            switch (c) {
              case ' ':
              case '\n':
                whereBuf.append(MARKER_BACKSPACE);
                break;
              case '(':
                whereBuf.append(MARKER_LEFT_BRACE);
                break;
              case ')':
                whereBuf.append(MARKER_RIGHT_BRACE);
                break;
              default:
                whereBuf.append(c);
            }
          } else if (pathClosed) {
            if ((c == 'a' || c == 'A') && i < array.length - 2 &&
                text.substring(i, i + 3).equalsIgnoreCase("and")) {
              i += 2;
              whereBuf.append(MARKER_AND);
              operatorPosition = false;
              continue;
            }
            if ((c == 'o' || c == 'O') && i < array.length - 1 &&
                text.substring(i, i + 2).equalsIgnoreCase("or")) {
              i += 1;
              whereBuf.append(MARKER_OR);
              operatorPosition = false;
              continue;
            }
            if (operatorPosition) {
              switch (c) {
                case '=':
                  whereBuf.append(MARKER_EQUAL);
                  operatorPosition = false;
                  break;
                case '<':
                  if (i + 1 < array.length) {
                    if (array[i + 1] == '=') {
                      whereBuf.append(MARKER_LESS_EQUAL);
                      operatorPosition = false;
                      i++;
                      break;
                    } else if (array[i + 1] == '>') {
                      whereBuf.append(MARKER_NOT_EQUAL);
                      operatorPosition = false;
                      i++;
                      break;
                    }
                  }
                  operatorPosition = false;
                  whereBuf.append(MARKER_LESS);
                  break;
                case '>':
                  if (i + 1 < array.length) {
                    if (array[i + 1] == '=') {
                      whereBuf.append(MARKER_GREATER_EQUALS);
                      operatorPosition = false;
                      i++;
                      break;
                    }
                  }
                  operatorPosition = false;
                  whereBuf.append(MARKER_GREATER);
                  break;
                case 'i':
                case 'I':
                  boolean whileI = true;
                  StringBuilder operI = new StringBuilder();
                  operI.append(c);
                  while (whileI) {
                    i++;
                    if (i >= array.length) {
                      whileI = false;
                    }

                    if (array[i] != ' ' && array[i] != '\n') {
                      operI.append(array[i]);
                    } else {
                      continue;
                    }
                    String tmp = operI.toString().toLowerCase();
                    if (tmp.equals("in")) {
                      whereBuf.append(MARKER_IN);
                      listOperator = true;
                      whileI = false;
                      operatorPosition = false;
                    } else if (tmp.equals("isnull")) {
                      whereBuf.append(MARKER_NULL);
                      whileI = false;
                      operatorPosition = false;
                    } else if (tmp.equals("isnotnull")) {
                      whereBuf.append(MARKER_NOT_NULL);
                      whileI = false;
                      operatorPosition = false;
                    }
                    // longest 9 - isnotnull
                    if (tmp.length() > 8) {
                      whileI = false;
                    }
                  }
                  break;
                case 'n':
                case 'N':
                  boolean whileN = true;
                  StringBuilder operN = new StringBuilder();
                  operN.append(c);
                  while (whileN) {
                    i++;
                    if (i >= array.length) {
                      whileN = false;
                    }

                    if (array[i] != ' ' && array[i] != '\n') {
                      operN.append(array[i]);
                    } else {
                      continue;
                    }

                    String tmp = operN.toString().toLowerCase();

                    if (tmp.equals("notin")) {
                      whereBuf.append(MARKER_NOT_IN);
                      listOperator = true;
                      whileN = false;
                      operatorPosition = false;
                    }

                    // longest 5 - notin
                    if (tmp.length() > 4) {
                      whileN = false;
                    }
                  }
                  break;
                default:
                  whereBuf.append(c);
              }
            } else {
              whereBuf.append(c);
            }
          } else {
            whereBuf.append(c);
          }
        }
      }
    }

    if (wherePart && StringUtils.isBlank(where)) {
      throw new UniverseException("Incorrect block where");
    }

    UniverseQuery universeQuery = new UniverseQuery(select.toString().trim(),
        where, universeInfo, duplicatedRows, limit);

    if (!universeQuery.isCorrect()) {
      throw new UniverseException("Incorrect query");
    }

    return universeQuery;
  }

  private String parseWhere(String where, Map<String, UniverseNodeInfo> nodeInfos)
      throws UniverseException {
    List<String> out = new ArrayList<>();
    Stack<String> stack = new Stack<>();

    where = where.replaceAll("\\s*", "");

    Set<String> operationSymbols = new HashSet<>(OPERATIONS.keySet());
    operationSymbols.add(LEFT_BRACE);
    operationSymbols.add(RIGHT_BRACE);

    int index = 0;

    boolean findNext = true;
    while (findNext) {
      int nextOperationIndex = where.length();
      String nextOperation = "";
      for (String operation : operationSymbols) {
        int i = where.indexOf(operation, index);
        if (i >= 0 && i < nextOperationIndex) {
          nextOperation = operation;
          nextOperationIndex = i;
        }
      }
      if (nextOperationIndex == where.length()) {
        findNext = false;
      } else {
        if (index != nextOperationIndex) {
          out.add(where.substring(index, nextOperationIndex));
        }
        if (nextOperation.equals(LEFT_BRACE)) {
          stack.push(nextOperation);
        } else if (nextOperation.equals(RIGHT_BRACE)) {
          while (!stack.peek().equals(LEFT_BRACE)) {
            out.add(stack.pop());
            if (stack.empty()) {
              throw new UniverseException("Unmatched brackets");
            }
          }
          stack.pop();
        } else {
          while (!stack.empty() && !stack.peek().equals(LEFT_BRACE) &&
              (OPERATIONS.get(nextOperation) >= OPERATIONS.get(stack.peek()))) {
            out.add(stack.pop());
          }
          stack.push(nextOperation);
        }
        index = nextOperationIndex + nextOperation.length();
      }
    }
    if (index != where.length()) {
      out.add(where.substring(index));
    }
    while (!stack.empty()) {
      out.add(stack.pop());
    }
    StringBuffer result = new StringBuffer();
    if (!out.isEmpty())
      result.append(out.remove(0));
    while (!out.isEmpty())
      result.append(" ").append(out.remove(0));

    // result contains the reverse polish notation
    return convertWhereToXml(result.toString(), nodeInfos);
  }

  private String parseResultObj(String resultObj, Map<String, UniverseNodeInfo> nodeInfos)
      throws UniverseException {
    if (StringUtils.isNotBlank(resultObj)) {
      UniverseNodeInfo nodeInfo = nodeInfos.get(resultObj.trim());
      if (nodeInfo != null) {
        return String.format(RESULT_OBJ_TEMPLATE, nodeInfo.getNodePath(), nodeInfo.getId());
      }
      throw new UniverseException(String.format("Not found information about: \"%s\"",
          resultObj.trim()));
    }

    return StringUtils.EMPTY;
  }

  private String convertWhereToXml(String rpn, Map<String, UniverseNodeInfo> nodeInfos)
      throws UniverseException {
    StringTokenizer tokenizer = new StringTokenizer(rpn, " ");

    Stack<String> stack = new Stack();

    while (tokenizer.hasMoreTokens()) {
      StringBuilder tmp = new StringBuilder();
      String token = tokenizer.nextToken();
      if (!OPERATIONS.keySet().contains(token)) {
        stack.push(token.trim());
      } else {
        String rightOperand = revertReplace(stack.pop());
        String operator = token.replaceAll("^#|#$", "");

        if (token.equalsIgnoreCase(MARKER_NOT_NULL) || token.equalsIgnoreCase(MARKER_NULL)) {
          UniverseNodeInfo rightOperandInfo = nodeInfos.get(rightOperand);
          stack.push(String.format(COMPARISON_FILTER, rightOperandInfo.getId(),
              rightOperandInfo.getNodePath(), operator));
          continue;
        }

        if (token.equalsIgnoreCase(MARKER_FILTER)) {
          UniverseNodeInfo rightOperandInfo = nodeInfos.get(rightOperand);
          stack.push(String.format(PREDEFINED_FILTER_TEMPLATE, rightOperandInfo.getNodePath(),
              rightOperandInfo.getId()));
          continue;
        }

        String leftOperand = stack.empty() ? null : revertReplace(stack.pop());

        if (token.equalsIgnoreCase(MARKER_AND) || token.equalsIgnoreCase(MARKER_OR)) {
          if (rightOperand.matches("^\\[.*\\]$")) {
            UniverseNodeInfo rightOperandInfo = nodeInfos.get(rightOperand);
            if (rightOperandInfo == null) {
              throw new UniverseException(String.format("Not found information about: \"%s\"",
                  rightOperand));
            }
            rightOperand = String.format(PREDEFINED_FILTER_TEMPLATE,
                rightOperandInfo.getNodePath(), rightOperandInfo.getId());
          }
          if (leftOperand.matches("^\\[.*\\]$")) {
            UniverseNodeInfo leftOperandInfo = nodeInfos.get(leftOperand);
            if (leftOperandInfo == null) {
              throw new UniverseException(String.format("Not found information about: \"%s\"",
                  leftOperand));
            }
            leftOperand = String.format(PREDEFINED_FILTER_TEMPLATE, leftOperandInfo.getNodePath(),
                leftOperandInfo.getId());
          }
          tmp.append(String.format("<%s>\n", operator));
          tmp.append(leftOperand);
          tmp.append("\n");
          tmp.append(rightOperand);
          tmp.append("\n");
          tmp.append(String.format("</%s>\n", operator));
          stack.push(tmp.toString());
          continue;
        }

        UniverseNodeInfo leftOperandInfo = nodeInfos.get(leftOperand);
        if (leftOperandInfo == null) {
          throw new UniverseException(String.format("Not found information about: \"%s\"",
              leftOperand));
        }
        if (token.equalsIgnoreCase(MARKER_IN) || token.equalsIgnoreCase(MARKER_NOT_IN)) {
          String listValues = rightOperand.replaceAll("^\\(|\\)$", "").trim();
          boolean startItem = false;
          List<String> values = new ArrayList<>();
          StringBuilder value = new StringBuilder();
          boolean isNumericList = false;
          if (listValues.charAt(0) != '\'') {
            isNumericList = true;
          }
          if (isNumericList) {
            String[] nums = listValues.split(",");
            for (String num : nums) {
              values.add(num.trim());
            }
          } else {
            for (int i = 0; i < listValues.length(); i++) {
              char c = listValues.charAt(i);
              if (c == '\'' && (i == 0 || listValues.charAt(i - 1) != '\\')) {
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
            tmp.append(String.format(COMPRASION_START_TEMPLATE, leftOperandInfo.getNodePath(),
                operator, leftOperandInfo.getId()));
            tmp.append(CONST_OPERAND_START_TEMPLATE);
            String type = isNumericList ? "Numeric" : "String";
            for (String v : values) {
              tmp.append(String.format(CONST_OPERAND_VALUE_TEMPLATE, type, v));
            }
            tmp.append(CONST_OPERAND_END_TEMPLATE);
            tmp.append(COMPRASION_END_TEMPLATE);
            stack.push(tmp.toString());
          }
          continue;
        }

        // EqualTo, LessThanOrEqualTo, NotEqualTo, LessThan, GreaterThanOrEqualTo, GreaterThan
        UniverseNodeInfo rightOperandInfo = null;
        if (rightOperand.startsWith("[") && rightOperand.endsWith("]")) {
          rightOperandInfo = nodeInfos.get(rightOperand);
          if (rightOperandInfo == null) {
            throw new UniverseException(String.format("Not found information about: \"%s\"",
                rightOperand));
          }
        }
        if (OPERATIONS.containsKey(token)) {
          if (rightOperandInfo != null) {
            tmp.append(String.format(COMPRASION_START_TEMPLATE, leftOperandInfo.getNodePath(),
                operator, leftOperandInfo.getId()));
            tmp.append(String.format(OBJECT_OPERAND_TEMPLATE, rightOperandInfo.getId(),
                rightOperandInfo.getNodePath()));
            tmp.append(COMPRASION_END_TEMPLATE);
          } else {
            String type = rightOperand.startsWith("'") ? "String" : "Numeric";
            String value = rightOperand.replaceAll("^'|'$", "");
            tmp.append(String.format(COMPRASION_START_TEMPLATE, leftOperandInfo.getNodePath(),
                operator, leftOperandInfo.getId()));
            tmp.append(CONST_OPERAND_START_TEMPLATE);
            tmp.append(String.format(CONST_OPERAND_VALUE_TEMPLATE, type, value));
            tmp.append(CONST_OPERAND_END_TEMPLATE);
            tmp.append(COMPRASION_END_TEMPLATE);
          }
          stack.push(tmp.toString());
          continue;
        }
        throw new UniverseException(String.format("Incorrect syntax after: \"%s\"", leftOperand));
      }
    }

    return stack.pop();
  }

  private String revertReplace(String s) {
    return s.replaceAll(MARKER_BACKSPACE, " ")
        .replaceAll(MARKER_LEFT_BRACE, "(")
        .replaceAll(MARKER_RIGHT_BRACE, ")");
  }

  private boolean isFilter(String buf, String after) {
    boolean result = false;
    String[] parts = buf.trim().split("\\s");
    if (parts[parts.length - 1].matches("^\\[.*\\]$")) {
      // check before
      if (parts.length == 1) {
        result = true;
      } else {
        int count = parts.length - 2;
        Set<String> operations = new HashSet(OPERATIONS.keySet());
        operations.remove(MARKER_AND);
        operations.remove(MARKER_OR);
        while (count >= 0) {
          String p = parts[count];
          if (StringUtils.isNotBlank(p)) {
            if (!operations.contains(p)) {
              result = true;
              break;
            } else {
              return false;
            }
          }
          count--;
        }
      }
      after = after.trim();
      // check after
      if (result && !after.startsWith("and") && !after.startsWith("or") &&
          !after.startsWith(";") && StringUtils.isNotBlank(after)) {
        result = false;
      }
    }

    return result;
  }
}
