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
package org.apache.zeppelin.ksql;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.apache.commons.text.StringEscapeUtils.escapeHtml4;

public class QueryExecutor {
  private static final Logger LOGGER = LoggerFactory.getLogger(QueryExecutor.class);

  static final String HTML_MAGIC = "%html ";
  static final String TABLE_MAGIC = "%table ";

  static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  String queryEndpoint;
  String ksqlEndpoint;
  String statusEndpoint;

  String fetchSize = "10";

  // TODO(alex): Hack! get rid of it
  private static final String LIMIT_ERROR_MESSAGE = "LIMIT reached for the partition.";

  private static final Pattern LIMIT_REGEX = Pattern.compile("LIMIT\\s+\\d+",
      Pattern.CASE_INSENSITIVE);

  private static final Map<KsqlQuery.QueryType, Function<String, InterpreterResult>> HANDLERS =
      new TreeMap();

  static {
    Function<String, InterpreterResult> func = s -> QueryExecutor.formatTables("tables", s);
    HANDLERS.put(KsqlQuery.QueryType.SHOW_TABLES, func);
    func = s -> QueryExecutor.formatTables("streams", s);
    HANDLERS.put(KsqlQuery.QueryType.SHOW_STREAMS, func);
    func = QueryExecutor::formatProperties;
    HANDLERS.put(KsqlQuery.QueryType.SHOW_PROPS, func);
    func = QueryExecutor::formatTopics;
    HANDLERS.put(KsqlQuery.QueryType.SHOW_TOPICS, func);
    func = QueryExecutor::formatQueries;
    HANDLERS.put(KsqlQuery.QueryType.SHOW_QUERIES, func);
    func = QueryExecutor::formatFunctions;
    HANDLERS.put(KsqlQuery.QueryType.SHOW_FUNCTIONS, func);
    func = QueryExecutor::formatDescribe;
    HANDLERS.put(KsqlQuery.QueryType.DESCRIBE, func);
    func = QueryExecutor::formatDescribeFunction;
    HANDLERS.put(KsqlQuery.QueryType.DESCRIBE_FUNCTION, func);
    func = QueryExecutor::formatSelect;
    HANDLERS.put(KsqlQuery.QueryType.SELECT, func);
    func = QueryExecutor::formatStatus;
    HANDLERS.put(KsqlQuery.QueryType.DROP_TABLE, func);
    HANDLERS.put(KsqlQuery.QueryType.CREATE_TABLE_STREAM, func);
    HANDLERS.put(KsqlQuery.QueryType.TERMINATE, func);
    HANDLERS.put(KsqlQuery.QueryType.INSERT_INTO, func);

    OBJECT_MAPPER.registerModule(new Jdk8Module());
  }

  QueryExecutor(final String url, String fetchSize) {
    LOGGER.info("Initializing query executor for URL: {}", url);
    // TODO(alex): parse URL, normalize it, and then append endpoints...
    queryEndpoint = url + "/query";
    ksqlEndpoint = url + "/ksql";
    statusEndpoint = url + "/status";
    this.fetchSize = fetchSize;
  }

  public InterpreterResult execute(KsqlQuery query) {
    if (query.isUnsupported()) {
      return new InterpreterResult(InterpreterResult.Code.ERROR,
        "Query '" + query.getQuery() + "' isn't supported yet...");
    }
    InterpreterResult result = null;
    try {
      KsqlQuery.QueryType queryType = query.getType();
      String queryString = query.getQuery();
      final String endpoint;
      if (queryType == KsqlQuery.QueryType.SELECT) {
        endpoint = queryEndpoint;
        Matcher matcher = LIMIT_REGEX.matcher(queryString);
        if (!matcher.find()) {
          queryString = queryString.substring(0, queryString.length() - 1) + " LIMIT "
            + fetchSize + ";";
          LOGGER.info("Resulting query: '" + queryString + "'");
        }
      } else {
        endpoint = ksqlEndpoint;
      }
      // make a call to REST API & get answer...
      CloseableHttpClient httpclient = HttpClients.createDefault();
      HttpPost httpPost = new HttpPost(endpoint);
      StringEntity entity = new StringEntity(
              OBJECT_MAPPER.writeValueAsString(new Query(queryString)));
      httpPost.setEntity(entity);
      httpPost.addHeader("Content-Type", "application/json");

      CloseableHttpResponse response = httpclient.execute(httpPost);
      String body = "{}";
      try {
        StatusLine status = response.getStatusLine();
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        if (response.getEntity() != null) {
          response.getEntity().writeTo(os);
          body = os.toString("UTF-8");
        }
        os.close();
        if (status.getStatusCode() != 200) {
          try {
            Map<String, Object> m = OBJECT_MAPPER.readValue(body,
                new TypeReference<Map<String, Object>>() { });

            StringBuilder sb = new StringBuilder(HTML_MAGIC);
            sb.append("<span style=\"font-weight:bold;color:red;\">ERROR!</span> ")
              .append("<span style=\"font-weight:bold;\">HTTP Status</span>: ")
              .append(status.getStatusCode())
              .append(", <span style=\"font-weight:bold;\">Error code</span>: ")
              .append(m.getOrDefault("error_code", "Unknown error code"))
              .append(", <span style=\"font-weight:bold;\">Message</span>: ")
              .append(escapeHtml4(m.getOrDefault("message", "").toString()));

            return new InterpreterResult(InterpreterResult.Code.ERROR, sb.toString());
          } catch (Exception ex) {
            LOGGER.warn("Exception: " + ex.getMessage());
          }

          return new InterpreterResult(InterpreterResult.Code.ERROR,
            "Non-200 Answer from KSQL server: " + status.getStatusCode() +
              ". " + status.getReasonPhrase());
        }
      } finally {
        response.close();
      }
      LOGGER.debug("Got answer from server: {}", body);

      // handle results
      Function<String, InterpreterResult> handler = HANDLERS.get(queryType);
      if (handler != null) {
        result = handler.apply(body);
      } else {
        result = new InterpreterResult(InterpreterResult.Code.ERROR,
          "No handler for query type " + queryType.name());
      }
    } catch (Exception ex) {
      result = new InterpreterResult(InterpreterResult.Code.ERROR,
        "Exception: " + ex.getMessage());
      LOGGER.error("Exception: ", ex);
    }
    return result;
  }

  public static InterpreterResult formatTables(final String type, final String payload) {
    List<Object> values = null;
    try {
      values = getListFromSection(payload, type, type);
    } catch (ParsingException ex) {
      return new InterpreterResult(InterpreterResult.Code.ERROR, ex.getMessage());
    }

    boolean isTables = "tables".equalsIgnoreCase(type);

    StringBuilder sb = new StringBuilder(TABLE_MAGIC);
    sb.append("Name\tStream\tFormat");
    if (isTables) {
      sb.append("\tWindowed?");
    }
    sb.append('\n');
    for (Object obj : values) {
      Map<String, Object> entry = (Map<String, Object>) obj;
      sb.append(entry.getOrDefault("name", ""));
      sb.append('\t');
      sb.append(entry.getOrDefault("topic", ""));
      sb.append('\t');
      sb.append(entry.getOrDefault("format", ""));
      if (isTables) {
        sb.append('\t');
        sb.append(entry.getOrDefault("isWindowed", false));
      }
      sb.append('\n');
    }

    return new InterpreterResult(InterpreterResult.Code.SUCCESS, sb.toString());
  }

  public static InterpreterResult formatProperties(final String payload) {
    Map<String, Object> m = null;
    try {
      m = getSection(payload, "properties");
    } catch (ParsingException ex) {
      LOGGER.error("Exception: ", ex);
      return new InterpreterResult(InterpreterResult.Code.ERROR,
        "Exception: " + ex.getMessage());
    }

    Map<String, Object> values = (Map<String, Object>) (m.get("properties"));
    if (values == null) {
      return new InterpreterResult(InterpreterResult.Code.ERROR,
        "No 'properties' section in result!");
    }

    StringBuilder sb = new StringBuilder(TABLE_MAGIC);
    sb.append("Property\tValue\n");
    for (Map.Entry<String, Object> entry : values.entrySet()) {
      sb.append(entry.getKey());
      sb.append('\t');
      sb.append(entry.getValue());
      sb.append('\n');
    }

    return new InterpreterResult(InterpreterResult.Code.SUCCESS, sb.toString());
  }

  public static InterpreterResult formatTopics(final String payload) {
    List<Object> values = null;
    try {
      values = getListFromSection(payload, "kafka_topics", "topics");
    } catch (ParsingException ex) {
      return new InterpreterResult(InterpreterResult.Code.ERROR, ex.getMessage());
    }

    StringBuilder sb = new StringBuilder(TABLE_MAGIC);
    sb.append("Name\tRegistered?\tPartition count\tReplica Information");
    sb.append("\tConsumer count\tConsumer group count\n");
    Response.TopicInfo[] topics = OBJECT_MAPPER.convertValue(values, Response.TopicInfo[].class);
    for (Response.TopicInfo tinfo: topics) {
      sb.append(tinfo.name);
      sb.append('\t');
      sb.append(tinfo.registered);
      sb.append('\t');
      sb.append(tinfo.partitionCount);
      sb.append("\t[");
      sb.append(StringUtils.join(tinfo.replicaInfo, ','));
      sb.append("]\t");
      sb.append(tinfo.consumerCount);
      sb.append('\t');
      sb.append(tinfo.consumerGroupCount);
      sb.append('\n');
    }

    return new InterpreterResult(InterpreterResult.Code.SUCCESS, sb.toString());
  }

  public static InterpreterResult formatFunctions(final String payload) {
    List<Object> values = null;
    try {
      values = getListFromSection(payload, "function_names", "functions");
    } catch (ParsingException ex) {
      return new InterpreterResult(InterpreterResult.Code.ERROR, ex.getMessage());
    }

    StringBuilder sb = new StringBuilder(TABLE_MAGIC);
    sb.append("Name\tType\n");
    Response.FunctionInfo[] functions = OBJECT_MAPPER.convertValue(values,
            Response.FunctionInfo[].class);
    for (Response.FunctionInfo finfo: functions) {
      sb.append(finfo.name);
      sb.append('\t');
      sb.append(finfo.type);
      sb.append('\n');
    }

    return new InterpreterResult(InterpreterResult.Code.SUCCESS, sb.toString());
  }


  private static String formatSchema(Map<String, Object> m) {
    if (m == null) {
      return "UNKNOWN_TYPE";
    }

    String type = (String) m.getOrDefault("type", "UNKNOWN_TYPE");
    StringBuilder sb = new StringBuilder();
    sb.append(type);
    type.toUpperCase();
    // LOGGER.info("type='" + type + "'");
    if ("STRUCT".equals(type)) {
      sb.append('<');
      List<Object> schema = (List<Object>) m.get("fields");
      // LOGGER.info("Handling struct. Schema= " + schema);
      if (schema != null) {
        int cnt = 0;
        for (Object obj: schema) {
          Map<String, Object> entry = (Map<String, Object> ) obj;
          if (cnt > 0) {
            sb.append(", ");
          }
          String name = (String) entry.getOrDefault("name", "UNKNOWNO_NAME");
          sb.append(name);
          sb.append(' ');
          String tp = formatSchema((Map<String, Object> ) entry.get("schema"));
          sb.append(tp);
          // LOGGER.info("subtype. name=" + name + ", type=" + tp);
          cnt++;
        }
      } else {
        sb.append("Error getting schema for Struct!");
      }
      sb.append('>');
    } else if ("ARRAY".equals(type) || "MAP".equals(type)) {
      sb.append('<');
      sb.append('>');
    }

    return sb.toString();
  }

  private static void formatQueriesInDescribe(StringBuilder sb, String name, List<Object> objects) {
    sb.append("<tr><td colspan=\"2\">Read queries:</td></tr>\n");
    for (Object obj : objects) {
      Map<String, Object> m = (Map<String, Object>) obj;
      sb.append("<tr><td>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;");
      sb.append(escapeHtml4(m.getOrDefault("id", "UNKNOWN_ID").toString()));
      sb.append("</td><td>");
      sb.append(escapeHtml4(m.getOrDefault("queryString", "UNKNOWN_QUERY").toString()));
      sb.append("</td></tr>\n");
    }
  }

  public static InterpreterResult formatDescribe(final String payload) {
    Map<String, Object> m = null;
    try {
      m = getObjectFromSection(payload, "sourceDescription", "sourceDescription");
    } catch (ParsingException ex) {
      LOGGER.error("Exception: ", ex);
      return new InterpreterResult(InterpreterResult.Code.ERROR,
        "Exception: " + ex.getMessage());
    }

    StringBuilder sb = new StringBuilder(HTML_MAGIC);
    // TODO(alex): Use templates!
    sb.append("<style>table, th, td {border: 1px solid gray;}\n")
      .append("table { border-collapse: collapse; }")
      .append("th, td { padding: 3px; }</style>")
      .append("<table>");
    sb.append("<tr><td width=\"20%\">Name:</td><th>");
    sb.append(escapeHtml4(m.getOrDefault("name", "UNKNOWN NAME").toString()));
    sb.append("</th>").append("</tr>");
    sb.append("<tr><td>Type:</td><th>");
    sb.append(escapeHtml4(m.getOrDefault("type", "STREAM").toString()));
    sb.append("</th>").append("</tr>");
    sb.append("<tr><td>Topic:</td><th>");
    sb.append(escapeHtml4(m.getOrDefault("topic", "UNKNOWN TOPIC").toString()));
    sb.append("</th>").append("</tr>");
    sb.append("<tr><td>Format:</td><td>");
    sb.append(escapeHtml4(m.getOrDefault("format", "UNKNOWN FORMAT").toString()));
    sb.append("</td>").append("</tr>");

    List<Object> schemaList = (List<Object>) (m.get("fields"));
    if (schemaList != null) {
      sb.append("<tr><th colspan=\"2\"><br>Schema</th></tr>\n");
      sb.append("<tr><th>Name</th><th>Type</th></tr>\n");
      for (Object obj : schemaList) {
        Map<String, Object> entry = (Map<String, Object>) obj;
        sb.append("<tr><td>");
        sb.append(escapeHtml4(entry.getOrDefault("name", "").toString()));
        sb.append("</td><td>");
        sb.append(escapeHtml4(formatSchema((Map<String, Object>) entry.get("schema"))));
        sb.append("</td></tr>\n");
      }
      sb.append("<tr><td colspan=\"2\">&nbsp;</td></tr>\n");
    }

    List<Object> queries = (List<Object>) (m.get("readQueries"));
    if (!CollectionUtils.isEmpty(queries)) {
      formatQueriesInDescribe(sb, "Read queries", queries);
    }

    queries = (List<Object>) (m.get("writeQueries"));
    if (!CollectionUtils.isEmpty(queries)) {
      formatQueriesInDescribe(sb, "Write queries", queries);
    }

    sb.append("<tr><td>Key column:</td><td>");
    sb.append(escapeHtml4(m.getOrDefault("key", "").toString()));
    sb.append("</td>").append("</tr>");
    sb.append("<tr><td>Timestamp column:</td><td>");
    sb.append(escapeHtml4(m.getOrDefault("timestamp", "").toString()));
    sb.append("</td>").append("</tr>");

    // output extended data
    Object isExtended = m.get("extended");
    if (isExtended instanceof Boolean && (Boolean) isExtended) {
      sb.append("<tr><td>Replication:</td><td>");
      sb.append(m.getOrDefault("replication", 0));
      sb.append("</td></tr>");
      sb.append("<tr><td>Partitions:</td><td>");
      sb.append(m.getOrDefault("partitions", 0));
      sb.append("</td></tr>");

      sb.append("<tr><td>Statistics:</td><td>");
      sb.append(escapeHtml4(m.getOrDefault("statistics", "").toString()));
      sb.append("</td></tr>");
      sb.append("<tr><td>Error statistics:</td><td>");
      sb.append(escapeHtml4(m.getOrDefault("errorStats", "").toString()));
      sb.append("</td></tr>");

      sb.append("<tr><td>Topology:</td><td>");
      sb.append(escapeHtml4(m.getOrDefault("topology", "").toString()));
      sb.append("</td></tr>");
      sb.append("<tr><td>Execution plan:</td><td>");
      sb.append(escapeHtml4(m.getOrDefault("executionPlan", "").toString()));
      sb.append("</td></tr>");
    }

    sb.append("</table>");

    return new InterpreterResult(InterpreterResult.Code.SUCCESS, sb.toString());
  }

  public static InterpreterResult formatSelect(final String payload) {
    String[] rows = payload.split("\n");
    StringBuilder sb = new StringBuilder();
    int maxCellCount = 0;
    for (int i = 0; i < rows.length; i++) {
      try {
        String str = rows[i].trim();
        LOGGER.info("raw row=" + str);
        if (str.isEmpty()) {
          continue;
        }
        Map<String, Object> m = OBJECT_MAPPER.readValue(str,
            new TypeReference<Map<String, Object>>() {
            });
        Map<String, Object> row = (Map<String, Object>) m.get("row");
        LOGGER.info("row=" + row);
        if (row == null) {
          String finalMessage = (String) m.get("finalMessage");
          if (finalMessage != null) {
            break;
          }
          Map<String, Object> errorMessage = (Map<String, Object>) m.get("errorMessage");
          if (errorMessage != null) {
            String message = (String) errorMessage.getOrDefault("message", "");
            if (i == 0) {
              return new InterpreterResult(InterpreterResult.Code.ERROR,
                "Error executing query: " + message);
            } else {
              sb.append("Error processing row ");
              sb.append(i);
              sb.append(": ");
              sb.append(message);
              sb.append('\n');
            }
          }
        } else {
          List<Object> values = (List<Object>) row.get("columns");
          boolean isFirst = true;
          int count = 0;
          for (Object obj: values) {
            if (!isFirst) {
              sb.append('\t');
            }
            if (obj != null) {
              String objStr = obj.toString().replaceAll("[\t\n]", " ");
              sb.append(objStr);
            } else {
              sb.append("null");
            }
            isFirst = false;
            count++;
          }
          sb.append('\n');
          if (count > maxCellCount) {
            maxCellCount = count;
          }
        }
      } catch (Exception ex) {
        sb.append("Error processing row ");
        sb.append(i);
        sb.append(": ");
        sb.append(ex.getMessage());
        sb.append('\n');
      }
    }

    StringBuilder result =  new StringBuilder(TABLE_MAGIC);
    for (int i = 0; i < maxCellCount; i++) {
      if (i > 0) {
        result.append('\t');
      }
      result.append("Col " + i);
    }
    result.append('\n');
    result.append(sb.toString());
    LOGGER.info("Result='" + result + "'");
    return new InterpreterResult(InterpreterResult.Code.SUCCESS, result.toString());
  }

  private static Map<String, Object> getSection(final String payload, final String requiredType) {
    Map<String, Object> m;
    try {
      List<Object> results = OBJECT_MAPPER.readValue(payload, new TypeReference<List<Object>>() {
      });
      if (results.size() < 1) {
        throw new ParsingException("No data returned!");
      }
      m = (Map<String, Object>) (results.get(0));
      String type = (String) m.get("@type");
      if (!requiredType.equals(type)) {
        throw new ParsingException("Section type doesn't match! @type value is '" + type
          + "' instead of '" + requiredType + "'");
      }
    } catch (IOException ex) {
      LOGGER.error("Exception: ", ex);
      throw new ParsingException("Exception: " + ex.getMessage());
    }
    return m;
  }

  private static List<Object> getListFromSection(final String payload, final String requiredType,
                                                 final String section) {
    Map<String, Object> m = getSection(payload, requiredType);
    List<Object> values = (List<Object>) (m.get(section));
    if (values == null) {
      throw new ParsingException("No " + section + " section in result!");
    }

    return values;
  }

  private static Map<String, Object> getObjectFromSection(final String payload,
                                                          final String requiredType,
                                                          final String section) {
    Map<String, Object> m = getSection(payload, requiredType);
    Map<String, Object> values = (Map<String, Object>) (m.get(section));
    if (values == null) {
      throw new ParsingException("No " + section + " section in result!");
    }

    return values;
  }


  public static InterpreterResult formatQueries(final String payload) {
    List<Object> values = null;
    try {
      values = getListFromSection(payload, "queries", "queries");
    } catch (ParsingException ex) {
      return new InterpreterResult(InterpreterResult.Code.ERROR, ex.getMessage());
    }

    StringBuilder sb = new StringBuilder(TABLE_MAGIC);
    sb.append("ID\tSink\tQuery\n");
    for (Object obj : values) {
      Map<String, Object> entry = (Map<String, Object>) obj;
      sb.append(entry.getOrDefault("id", ""));
      sb.append('\t');
      List<String> sinks = (List<String>) entry.get("sinks");
      if (sinks != null) {
        sb.append(StringUtils.join(sinks, ", "));
      }
      sb.append('\t');
      sb.append(entry.getOrDefault("queryString", ""));
      sb.append('\n');
    }

    return new InterpreterResult(InterpreterResult.Code.SUCCESS, sb.toString());
  }

  public static InterpreterResult formatStatus(final String payload) {
    Map<String, Object> m = null;
    try {
      m = getObjectFromSection(payload, "currentStatus", "commandStatus");
    } catch (ParsingException ex) {
      LOGGER.error("Exception: ", ex);
      return new InterpreterResult(InterpreterResult.Code.ERROR,
        "Exception: " + ex.getMessage());
    }

    String status = m.getOrDefault("status", "UNKNOWN_STATUS").toString();
    String color = "green";
    InterpreterResult.Code code = InterpreterResult.Code.SUCCESS;
    if ("ERROR".equalsIgnoreCase(status) || "TERMINATED".equalsIgnoreCase(status)) {
      code = InterpreterResult.Code.ERROR;
      color = "red";
    }
    // TODO(alex): Should I set incomplete code for PARSING/... statuses?

    StringBuilder sb = new StringBuilder(HTML_MAGIC);
    sb.append("<span style=\"font-weight:bold;color:")
      .append(color).append(";\">").append(escapeHtml4(status)).append("</span>: ")
      .append(escapeHtml4(m.getOrDefault("message", "").toString()));

    return new InterpreterResult(code, sb.toString());
  }

  public static InterpreterResult formatDescribeFunction(final String payload) {
    Map<String, Object> m = null;
    try {
      m = getSection(payload, "describe_function");
    } catch (ParsingException ex) {
      LOGGER.error("Exception: ", ex);
      return new InterpreterResult(InterpreterResult.Code.ERROR,
        "Exception: " + ex.getMessage());
    }

    StringBuilder sb = new StringBuilder(HTML_MAGIC);
    // TODO(alex): Use templates!
    sb.append("<style>table, th, td {border: 1px solid gray;}\n")
      .append("table { border-collapse: collapse; }")
      .append("th, td { padding: 3px; }</style>")
      .append("<table>");
    sb.append("<tr><td width=\"20%\">Name:</td><th colspan=\"2\">");
    sb.append(escapeHtml4(m.getOrDefault("name", "UNKNOWN FUNCTION").toString()));
    sb.append("</th>").append("</tr>");
    sb.append("<tr><td>Type:</td><td colspan=\"2\">");
    sb.append(escapeHtml4(m.getOrDefault("type", "scalar").toString()));
    sb.append("</td>").append("</tr>");
    sb.append("<tr><td>Description:</td><td colspan=\"2\">");
    sb.append(escapeHtml4(m.getOrDefault("description", "").toString()));
    sb.append("</td>").append("</tr>");
    sb.append("<tr><td>Author:</td><td colspan=\"2\">");
    sb.append(escapeHtml4(m.getOrDefault("format", "Unknown").toString()));
    sb.append("</td>").append("</tr>");
    sb.append("<tr><td>Version:</td><td colspan=\"2\">");
    sb.append(escapeHtml4(m.getOrDefault("version", "Unknown").toString()));
    sb.append("</td>").append("</tr>");
    sb.append("<tr><td>Path:</td><td colspan=\"2\">");
    sb.append(escapeHtml4(m.getOrDefault("path", "Unknown").toString()));
    sb.append("</td>").append("</tr>");

    List<Object> values = (List<Object>) (m.get("functions"));
    if (values != null) {
      sb.append("<tr><td colspan=\"3\">&nbsp;</td></tr>");
      sb.append("<tr><th>Argument types</th><th>Return type</th><th>Description</th></tr>");
      for (Object obj: values) {
        Map<String, Object> entry = (Map<String, Object>) obj;
        sb.append("<tr><td>");
        List<String> types = (List<String>) entry.get("argumentTypes");
        if (types != null) {
          sb.append(escapeHtml4(StringUtils.join(types, ", ")));
        }
        sb.append("</td><td>");
        sb.append(escapeHtml4(entry.getOrDefault("returnType", "").toString()));
        sb.append("</td><td>");
        sb.append(escapeHtml4(entry.getOrDefault("description", "").toString()));
        sb.append("</td></tr>");
      }
    }

    return new InterpreterResult(InterpreterResult.Code.SUCCESS, sb.toString());
  }
}
