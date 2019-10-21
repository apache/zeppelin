/*
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

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

public class KSQLRequest {

  private static final String EXPLAIN_QUERY = "EXPLAIN %s";
  private final String ksql;
  private final Map<String, String> streamsProperties;

  KSQLRequest(final String ksql, final Map<String, String> streamsProperties) {
    String inputQuery = Objects.requireNonNull(ksql, "ksql")
        .replaceAll("[\\n\\t\\r]", " ")
        .trim();
    this.ksql = inputQuery.endsWith(";") ? inputQuery : inputQuery + ";";
    this.streamsProperties = streamsProperties;
  }

  KSQLRequest(final String ksql) {
    this(ksql, Collections.emptyMap());
  }

  KSQLRequest toExplainRequest() {
    return new KSQLRequest(String.format(EXPLAIN_QUERY, this.ksql), this.streamsProperties);
  }

  public String getKsql() {
    return ksql;
  }

  public Map<String, String> getStreamsProperties() {
    return streamsProperties;
  }
}
