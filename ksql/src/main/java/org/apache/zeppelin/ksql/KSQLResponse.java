/*
 * Copyright 2016 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0

 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.zeppelin.ksql;

import java.util.AbstractMap;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class KSQLResponse {
  private final Map<String, Object> row;
  private final String finalMessage;
  private final String errorMessage;
  private final boolean terminal;

  private <T, K, U> Collector<T, ?, Map<K, U>>
      toLinkedHashMap(Function<? super T, ? extends K> keyMapper,
        Function<? super T, ? extends U> valueMapper) {
    return Collectors.toMap(
        keyMapper,
        valueMapper,
        (u, v) -> { throw new IllegalStateException(String.format("Duplicate key %s", u)); },
        LinkedHashMap::new);
  }

  KSQLResponse(final List<String> fields, final Map<String, Object> row,
         final String finalMessage, final String errorMessage, boolean terminal) {
    List<Object> columns = row == null ? null : (List<Object>) row.getOrDefault("columns",
        Collections.emptyList());
    this.row = row == null ? null : IntStream.range(0, columns.size())
        .mapToObj(index -> new AbstractMap.SimpleEntry<>(fields.get(index),
            columns.get(index)))
        .collect(toLinkedHashMap(e -> e.getKey(), e -> e.getValue()));
    this.finalMessage = finalMessage;
    this.errorMessage = errorMessage;
    this.terminal = terminal;
  }

  KSQLResponse(final List<String> fields, final Map<String, Object> resp) {
    this(fields, (Map<String, Object>) resp.get("row"),
        (String) resp.get("finalMessage"),
        (String) resp.get("errorMessage"),
        (boolean) resp.get("terminal"));
  }

  KSQLResponse(final Map<String, Object> resp) {
    this.row = resp;
    this.finalMessage = null;
    this.errorMessage = null;
    this.terminal = true;
  }

  public Map<String, Object> getRow() {
    return row;
  }

  public String getFinalMessage() {
    return finalMessage;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public boolean isTerminal() {
    return terminal;
  }
}
