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

package org.apache.zeppelin.bigquery;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.BigQueryOptions;
import com.google.cloud.bigquery.Field;
import com.google.cloud.bigquery.FieldValue;
import com.google.cloud.bigquery.FieldValueList;
import com.google.cloud.bigquery.Job;
import com.google.cloud.bigquery.JobId;
import com.google.cloud.bigquery.JobInfo;
import com.google.cloud.bigquery.QueryJobConfiguration;
import com.google.cloud.bigquery.TableResult;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResult.Code;
import org.apache.zeppelin.interpreter.thrift.InterpreterCompletion;
import org.apache.zeppelin.scheduler.Scheduler;
import org.apache.zeppelin.scheduler.SchedulerFactory;
import org.apache.zeppelin.user.AuthenticationInfo;

/**
 * BigQuery interpreter for Zeppelin using modern google-cloud-bigquery client.
 *
 * <ul>
 * <li>{@code zeppelin.bigquery.project_id} - Project ID in GCP</li>
 * <li>{@code zeppelin.bigquery.wait_time} - Query Timeout in ms</li>
 * <li>{@code zeppelin.bigquery.max_no_of_rows} - Max Result size</li>
 * </ul>
 *
 * <p>
 * How to use: <br/>
 * {@code %bigquery.sql<br/>
 * {@code
 *  SELECT departure_airport,count(case when departure_delay>0 then 1 else 0 end) as no_of_delays
 *  FROM `bigquery-samples.airline_ontime_data.flights`
 *  group by departure_airport
 *  order by 2 desc
 *  limit 10
 * }
 * </p>
 *
 */
public class BigQueryInterpreter extends Interpreter {
  private static final Logger LOGGER = LoggerFactory.getLogger(BigQueryInterpreter.class);
  private static final char NEWLINE = '\n';
  private static final char TAB = '\t';

  private BigQuery service = null;

  static final String PROJECT_ID = "zeppelin.bigquery.project_id";
  static final String WAIT_TIME = "zeppelin.bigquery.wait_time";
  static final String MAX_ROWS = "zeppelin.bigquery.max_no_of_rows";
  static final String SQL_DIALECT = "zeppelin.bigquery.sql_dialect";
  static final String REGION = "zeppelin.bigquery.region";

  private static final String SA_JSON_FORM_KEY = "GCP Service Account JSON";

  // Tracks running queries per paragraph so concurrent paragraphs in shared
  // interpreter mode don't clobber each other's job/cancel state.
  private final ConcurrentMap<String, RunningQuery> runningQueries = new ConcurrentHashMap<>();
  private Exception exceptionOnConnect;

  private static final class RunningQuery {
    private final BigQuery client;
    private final JobId jobId;

    RunningQuery(BigQuery client, JobId jobId) {
      this.client = client;
      this.jobId = jobId;
    }
  }

  private static final List<InterpreterCompletion> NO_COMPLETION = new ArrayList<>();

  private static final List<String> BQ_SCOPES = Collections.singletonList(
      "https://www.googleapis.com/auth/bigquery"
  );

  public BigQueryInterpreter(Properties property) {
    super(property);
  }

  @Override
  public void open() {
    LOGGER.info("Opening BigQuery SQL Connection...");
    // Service initialization is lazy and depends on InterpreterContext in interpret()
    // However, if we can init with ADC, we do it here.
    try {
      if (service == null) {
        service = createDefaultClient();
        exceptionOnConnect = null;
        LOGGER.info("Opened BigQuery SQL Connection with ADC");
      }
    } catch (Exception e) {
      LOGGER.warn("Cannot open connection with Application Default Credentials. " +
          "Will try user credentials on interpret.", e);
      exceptionOnConnect = e;
    }
  }

  private BigQuery createDefaultClient() throws IOException {
    GoogleCredentials credentials = GoogleCredentials.getApplicationDefault();
    if (credentials.createScopedRequired()) {
      credentials = credentials.createScoped(BQ_SCOPES);
    }

    BigQueryOptions.Builder builder = BigQueryOptions.newBuilder()
        .setCredentials(credentials);

    String projId = getProperty(PROJECT_ID);
    if (StringUtils.isNotBlank(projId)) {
      builder.setProjectId(projId);
    }

    return builder.build().getService();
  }

  private BigQuery getClientForUser(InterpreterContext context) throws IOException {
    AuthenticationInfo authInfo = context.getAuthenticationInfo();

    // Check if user has provided credentials via Zeppelin Credentials manager
    if (authInfo != null && authInfo.getTicket() != null) {
      // Typically we'd use something from credential manager, but let's assume JSON might be passed
      // String userKey = authInfo.getTicket();
    }

    if (service != null) {
      return service;
    }

    if (exceptionOnConnect != null) {
      throw new IOException("Failed to initialize BigQuery client with ADC", exceptionOnConnect);
    }

    return createDefaultClient();
  }

  private InterpreterResult executeSql(String sql, InterpreterContext context) {
    BigQuery bqClient;
    try {
      bqClient = getClientForUser(context);
    } catch (IOException e) {
      // Fallback: read a previously-supplied Service Account JSON from paragraph
      // form params, or render a masked password form to collect it.
      LOGGER.error("Authentication failed. Falling back to user-supplied SA JSON via GUI", e);
      Object existing = context.getGui().getParams().get(SA_JSON_FORM_KEY);
      String saJson = existing == null ? "" : existing.toString();
      if (StringUtils.isBlank(saJson)) {
        // No value yet: render the masked form so the user can enter the key.
        context.getGui().password(SA_JSON_FORM_KEY);
        return new InterpreterResult(Code.ERROR, "%html ⚠️ <b>Authentication Required</b><br/>" +
            "Could not find Application Default Credentials. Please input your " +
            "Service Account JSON key in the form and run again.");
      }
      try {
        GoogleCredentials credentials = ServiceAccountCredentials.fromStream(
            new ByteArrayInputStream(saJson.getBytes(StandardCharsets.UTF_8)));
        if (credentials.createScopedRequired()) {
          credentials = credentials.createScoped(BQ_SCOPES);
        }

        BigQueryOptions.Builder builder = BigQueryOptions.newBuilder()
            .setCredentials(credentials);

        String projId = getProperty(PROJECT_ID);
        if (StringUtils.isNotBlank(projId)) {
          builder.setProjectId(projId);
        }

        bqClient = builder.build().getService();
        // Do not cache this client in a shared field to avoid leaking user credentials
        exceptionOnConnect = null;
      } catch (IOException ex) {
        // Re-render the masked form so the user can correct an invalid key.
        context.getGui().password(SA_JSON_FORM_KEY);
        return new InterpreterResult(Code.ERROR, "Failed to parse Service Account JSON: " +
            ex.getMessage());
      }
    }

    long wTime = Long.parseLong(getProperty(WAIT_TIME, "5000"));
    long maxRows = Long.parseLong(getProperty(MAX_ROWS, "100000"));
    String sqlDialect = getProperty(SQL_DIALECT, "").toLowerCase();
    String region = getProperty(REGION, null);

    QueryJobConfiguration.Builder queryConfigBuilder = QueryJobConfiguration.newBuilder(sql)
        .setJobTimeoutMs(wTime);

    switch (sqlDialect) {
      case "standardsql":
        queryConfigBuilder.setUseLegacySql(false);
        break;
      case "legacysql":
        queryConfigBuilder.setUseLegacySql(true);
        break;
      default:
        // Use default (Usually Standard SQL if not specified)
        queryConfigBuilder.setUseLegacySql(null);
    }

    QueryJobConfiguration queryConfig = queryConfigBuilder.build();

    String jobIdStr = UUID.randomUUID().toString();
    JobId jobId;
    if (StringUtils.isNotBlank(region)) {
      jobId = JobId.newBuilder().setLocation(region).setJob(jobIdStr).build();
    } else {
      jobId = JobId.of(jobIdStr);
    }

    String paragraphId = context.getParagraphId();
    runningQueries.put(paragraphId, new RunningQuery(bqClient, jobId));
    try {
      LOGGER.info("Executing query: {}", sql);
      Job queryJob = bqClient.create(
          JobInfo.newBuilder(queryConfig).setJobId(jobId).build());

      // Wait for the query to complete
      queryJob = queryJob.waitFor();

      if (queryJob == null) {
        return new InterpreterResult(Code.ERROR, "Job no longer exists");
      } else if (queryJob.getStatus().getError() != null) {
        return new InterpreterResult(Code.ERROR, queryJob.getStatus().getError().toString());
      }

      TableResult result = queryJob.getQueryResults();

      StringBuilder msg = new StringBuilder("%table ");

      // Get Schema
      List<String> schemaNames = new ArrayList<>();
      for (Field field : result.getSchema().getFields()) {
        schemaNames.add(field.getName());
      }
      msg.append(StringUtils.join(schemaNames, TAB)).append(NEWLINE);

      // Get Data
      long count = 0;
      for (FieldValueList row : result.iterateAll()) {
        if (count >= maxRows) {
          break;
        }
        List<String> fieldValues = new ArrayList<>();
        for (FieldValue field : row) {
          fieldValues.add(field.isNull() ? "null" : field.getValue().toString());
        }
        msg.append(StringUtils.join(fieldValues, TAB)).append(NEWLINE);
        count++;
      }

      return new InterpreterResult(Code.SUCCESS, msg.toString());

    } catch (Exception ex) {
      LOGGER.error("Query execution failed", ex);
      return new InterpreterResult(Code.ERROR, ex.getMessage());
    } finally {
      runningQueries.remove(paragraphId);
    }
  }

  @Override
  public void close() {
    LOGGER.info("Close bqsql connection!");
    service = null;
  }

  @Override
  public InterpreterResult interpret(String sql, InterpreterContext contextInterpreter) {
    LOGGER.info("Run SQL command '{}'", sql);
    return executeSql(sql, contextInterpreter);
  }

  @Override
  public Scheduler getScheduler() {
    return SchedulerFactory.singleton().createOrGetFIFOScheduler(
        BigQueryInterpreter.class.getName() + this.hashCode());
  }

  @Override
  public FormType getFormType() {
    return FormType.SIMPLE;
  }

  @Override
  public int getProgress(InterpreterContext context) {
    return 0;
  }

  @Override
  public void cancel(InterpreterContext context) {
    String paragraphId = context.getParagraphId();
    LOGGER.info("Trying to cancel query for paragraph {}", paragraphId);
    RunningQuery running = runningQueries.remove(paragraphId);
    if (running == null) {
      LOGGER.info("Query Execution was already cancelled or not started");
      return;
    }
    try {
      boolean cancelled = running.client.cancel(running.jobId);
      if (cancelled) {
        LOGGER.info("Query Execution cancelled");
      } else {
        LOGGER.warn("Query Execution cancellation returned false");
      }
    } catch (RuntimeException e) {
      LOGGER.warn("Failed to cancel BigQuery job {}", running.jobId, e);
    }
  }

  @Override
  public List<InterpreterCompletion> completion(String buf, int cursor,
      InterpreterContext interpreterContext) {
    return NO_COMPLETION;
  }
}
