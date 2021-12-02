/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.zeppelin.flink;


import org.apache.flink.table.catalog.ResolvedSchema;
import org.apache.flink.table.data.MapData;
import org.apache.flink.table.data.RowData;
import org.apache.flink.table.data.TimestampData;
import org.apache.flink.table.types.logical.*;
import org.apache.flink.types.Row;
import org.apache.flink.util.StringUtils;

import java.sql.Time;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.apache.flink.table.types.logical.utils.LogicalTypeChecks.getPrecision;
import static org.apache.zeppelin.flink.TimestampStringUtils.*;

/**
 * Copied from flink-project with minor modification.
 * */
public class PrintUtils {

  public static final String NULL_COLUMN = "(NULL)";
  private static final String COLUMN_TRUNCATED_FLAG = "...";

  private PrintUtils() {}


  public static String[] rowToString(
          Row row, ResolvedSchema resolvedSchema, ZoneId sessionTimeZone) {
    return rowToString(row, NULL_COLUMN, false, resolvedSchema, sessionTimeZone);
  }

  public static String[] rowToString(
          Row row,
          String nullColumn,
          boolean printRowKind,
          ResolvedSchema resolvedSchema,
          ZoneId sessionTimeZone) {
    final int len = printRowKind ? row.getArity() + 1 : row.getArity();
    final List<String> fields = new ArrayList<>(len);
    if (printRowKind) {
      fields.add(row.getKind().shortString());
    }
    for (int i = 0; i < row.getArity(); i++) {
      final Object field = row.getField(i);
      final LogicalType fieldType =
              resolvedSchema.getColumnDataTypes().get(i).getLogicalType();
      if (field == null) {
        fields.add(nullColumn);
      } else {
        fields.add(
                StringUtils.arrayAwareToString(
                        formattedTimestamp(field, fieldType, sessionTimeZone)));
      }
    }
    return fields.toArray(new String[0]);
  }

  /**
   * Normalizes field that contains TIMESTAMP, TIMESTAMP_LTZ and TIME type data.
   *
   * <p>This method also supports nested type ARRAY, ROW, MAP.
   */
  private static Object formattedTimestamp(
          Object field, LogicalType fieldType, ZoneId sessionTimeZone) {
    final LogicalTypeRoot typeRoot = fieldType.getTypeRoot();
    if (field == null) {
      return "null";
    }
    switch (typeRoot) {
      case TIMESTAMP_WITHOUT_TIME_ZONE:
      case TIMESTAMP_WITH_LOCAL_TIME_ZONE:
        return formatTimestampField(field, fieldType, sessionTimeZone);
      case TIME_WITHOUT_TIME_ZONE:
        return formatTimeField(field);
      case ARRAY:
        LogicalType elementType = ((ArrayType) fieldType).getElementType();
        if (field instanceof List) {
          List<?> array = (List<?>) field;
          Object[] formattedArray = new Object[array.size()];
          for (int i = 0; i < array.size(); i++) {
            formattedArray[i] =
                    formattedTimestamp(array.get(i), elementType, sessionTimeZone);
          }
          return formattedArray;
        } else if (field.getClass().isArray()) {
          // primitive type
          if (field.getClass() == byte[].class) {
            byte[] array = (byte[]) field;
            Object[] formattedArray = new Object[array.length];
            for (int i = 0; i < array.length; i++) {
              formattedArray[i] =
                      formattedTimestamp(array[i], elementType, sessionTimeZone);
            }
            return formattedArray;
          } else if (field.getClass() == short[].class) {
            short[] array = (short[]) field;
            Object[] formattedArray = new Object[array.length];
            for (int i = 0; i < array.length; i++) {
              formattedArray[i] =
                      formattedTimestamp(array[i], elementType, sessionTimeZone);
            }
            return formattedArray;
          } else if (field.getClass() == int[].class) {
            int[] array = (int[]) field;
            Object[] formattedArray = new Object[array.length];
            for (int i = 0; i < array.length; i++) {
              formattedArray[i] =
                      formattedTimestamp(array[i], elementType, sessionTimeZone);
            }
            return formattedArray;
          } else if (field.getClass() == long[].class) {
            long[] array = (long[]) field;
            Object[] formattedArray = new Object[array.length];
            for (int i = 0; i < array.length; i++) {
              formattedArray[i] =
                      formattedTimestamp(array[i], elementType, sessionTimeZone);
            }
            return formattedArray;
          } else if (field.getClass() == float[].class) {
            float[] array = (float[]) field;
            Object[] formattedArray = new Object[array.length];
            for (int i = 0; i < array.length; i++) {
              formattedArray[i] =
                      formattedTimestamp(array[i], elementType, sessionTimeZone);
            }
            return formattedArray;
          } else if (field.getClass() == double[].class) {
            double[] array = (double[]) field;
            Object[] formattedArray = new Object[array.length];
            for (int i = 0; i < array.length; i++) {
              formattedArray[i] =
                      formattedTimestamp(array[i], elementType, sessionTimeZone);
            }
            return formattedArray;
          } else if (field.getClass() == boolean[].class) {
            boolean[] array = (boolean[]) field;
            Object[] formattedArray = new Object[array.length];
            for (int i = 0; i < array.length; i++) {
              formattedArray[i] =
                      formattedTimestamp(array[i], elementType, sessionTimeZone);
            }
            return formattedArray;
          } else if (field.getClass() == char[].class) {
            char[] array = (char[]) field;
            Object[] formattedArray = new Object[array.length];
            for (int i = 0; i < array.length; i++) {
              formattedArray[i] =
                      formattedTimestamp(array[i], elementType, sessionTimeZone);
            }
            return formattedArray;
          } else {
            // non-primitive type
            Object[] array = (Object[]) field;
            Object[] formattedArray = new Object[array.length];
            for (int i = 0; i < array.length; i++) {
              formattedArray[i] =
                      formattedTimestamp(array[i], elementType, sessionTimeZone);
            }
            return formattedArray;
          }
        } else {
          return field;
        }
      case ROW:
        if (fieldType instanceof RowType && field instanceof Row) {
          Row row = (Row) field;
          Row formattedRow = new Row(row.getKind(), row.getArity());
          for (int i = 0; i < ((RowType) fieldType).getFields().size(); i++) {
            LogicalType type = ((RowType) fieldType).getFields().get(i).getType();
            formattedRow.setField(
                    i, formattedTimestamp(row.getField(i), type, sessionTimeZone));
          }
          return formattedRow;

        } else if (fieldType instanceof RowType && field instanceof RowData) {
          RowData rowData = (RowData) field;
          Row formattedRow = new Row(rowData.getRowKind(), rowData.getArity());
          for (int i = 0; i < ((RowType) fieldType).getFields().size(); i++) {
            LogicalType type = ((RowType) fieldType).getFields().get(i).getType();
            RowData.FieldGetter fieldGetter = RowData.createFieldGetter(type, i);
            formattedRow.setField(
                    i,
                    formattedTimestamp(
                            fieldGetter.getFieldOrNull(rowData),
                            type,
                            sessionTimeZone));
          }
          return formattedRow;
        } else {
          return field;
        }
      case MAP:
        LogicalType keyType = ((MapType) fieldType).getKeyType();
        LogicalType valueType = ((MapType) fieldType).getValueType();
        if (fieldType instanceof MapType && field instanceof Map) {
          Map<Object, Object> map = ((Map) field);
          Map<Object, Object> formattedMap = new HashMap<>(map.size());
          for (Object key : map.keySet()) {
            formattedMap.put(
                    formattedTimestamp(key, keyType, sessionTimeZone),
                    formattedTimestamp(map.get(key), valueType, sessionTimeZone));
          }
          return formattedMap;
        } else if (fieldType instanceof MapType && field instanceof MapData) {
          MapData map = ((MapData) field);
          Map<Object, Object> formattedMap = new HashMap<>(map.size());
          Object[] keyArray =
                  (Object[]) formattedTimestamp(map.keyArray(), keyType, sessionTimeZone);
          Object[] valueArray =
                  (Object[])
                          formattedTimestamp(
                                  map.valueArray(), valueType, sessionTimeZone);
          for (int i = 0; i < keyArray.length; i++) {
            formattedMap.put(keyArray[i], valueArray[i]);
          }
          return formattedMap;
        } else {
          return field;
        }
      default:
        return field;
    }
  }

  /**
   * Formats the print content of TIMESTAMP and TIMESTAMP_LTZ type data, consider the user
   * configured time zone.
   */
  private static Object formatTimestampField(
          Object timestampField, LogicalType fieldType, ZoneId sessionTimeZone) {
    switch (fieldType.getTypeRoot()) {
      case TIMESTAMP_WITHOUT_TIME_ZONE:
        final int precision = getPrecision(fieldType);
        if (timestampField instanceof java.sql.Timestamp) {
          // conversion between java.sql.Timestamp and TIMESTAMP_WITHOUT_TIME_ZONE
          return timestampToString(
                  ((Timestamp) timestampField).toLocalDateTime(), precision);
        } else if (timestampField instanceof java.time.LocalDateTime) {
          return timestampToString(((LocalDateTime) timestampField), precision);
        } else if (timestampField instanceof TimestampData) {
          return timestampToString(
                  ((TimestampData) timestampField).toLocalDateTime(), precision);
        } else {
          return timestampField;
        }
      case TIMESTAMP_WITH_LOCAL_TIME_ZONE:
        Instant instant = null;
        if (timestampField instanceof java.time.Instant) {
          instant = ((Instant) timestampField);
        } else if (timestampField instanceof java.sql.Timestamp) {
          Timestamp timestamp = ((Timestamp) timestampField);
          // conversion between java.sql.Timestamp and TIMESTAMP_WITH_LOCAL_TIME_ZONE
          instant =
                  TimestampData.fromEpochMillis(
                                  timestamp.getTime(), timestamp.getNanos() % 1000_000)
                          .toInstant();
        } else if (timestampField instanceof TimestampData) {
          instant = ((TimestampData) timestampField).toInstant();
        } else if (timestampField instanceof Integer) {
          instant = Instant.ofEpochSecond((Integer) timestampField);
        } else if (timestampField instanceof Long) {
          instant = Instant.ofEpochMilli((Long) timestampField);
        }
        if (instant != null) {
          return timestampToString(
                  instant.atZone(sessionTimeZone).toLocalDateTime(),
                  getPrecision(fieldType));
        } else {
          return timestampField;
        }
      default:
        return timestampField;
    }
  }

  /** Formats the print content of TIME type data. */
  private static Object formatTimeField(Object timeField) {
    if (timeField.getClass().isAssignableFrom(int.class) || timeField instanceof Integer) {
      return unixTimeToString((int) timeField);
    } else if (timeField.getClass().isAssignableFrom(long.class) || timeField instanceof Long) {
      return unixTimeToString(((Long) timeField).intValue());
    } else if (timeField instanceof Time) {
      return unixTimeToString(timeToInternal((Time) timeField));
    } else if (timeField instanceof LocalTime) {
      return unixTimeToString(localTimeToUnixDate((LocalTime) timeField));
    } else {
      return timeField;
    }
  }
}
