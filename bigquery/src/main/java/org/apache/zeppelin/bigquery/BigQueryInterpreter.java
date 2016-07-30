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


import static org.apache.commons.lang.StringUtils.containsIgnoreCase;

import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.json.jackson2.JacksonFactory;

import com.google.api.services.bigquery.Bigquery;
import com.google.api.services.bigquery.BigqueryScopes;
import com.google.api.client.json.GenericJson;
import com.google.api.services.bigquery.Bigquery.Datasets;
import com.google.api.services.bigquery.BigqueryRequest;
import com.google.api.services.bigquery.model.DatasetList;
import com.google.api.services.bigquery.model.Job;
import com.google.api.services.bigquery.model.TableCell;
import com.google.api.services.bigquery.model.TableFieldSchema;
import com.google.api.services.bigquery.model.TableRow;
import com.google.api.services.bigquery.model.TableSchema;
import com.google.api.services.bigquery.Bigquery.Jobs.GetQueryResults;
import com.google.api.services.bigquery.model.GetQueryResultsResponse;
import com.google.api.services.bigquery.model.QueryRequest;
import com.google.api.services.bigquery.model.QueryResponse;
import com.google.api.services.bigquery.model.JobCancelResponse;
import com.google.gson.Gson;

import java.io.IOException;
import java.util.Collection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.Properties;
import java.util.Set;

import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterPropertyBuilder;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResult.Code;
import org.apache.zeppelin.interpreter.thrift.InterpreterCompletion;
import org.apache.zeppelin.scheduler.Scheduler;
import org.apache.zeppelin.scheduler.SchedulerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Function;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;
import java.io.PrintStream;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * BigQuery interpreter for Zeppelin.
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
 *  FROM [bigquery-samples:airline_ontime_data.flights] 
 *  group by departure_airport 
 *  order by 2 desc 
 *  limit 10
 * }
 * </p>
 * 
 */


public class BigQueryInterpreter extends Interpreter {

  private Logger logger = LoggerFactory.getLogger(BigQueryInterpreter.class);
  private static final char NEWLINE = '\n';
  private static final char TAB = '\t';
  private static Bigquery service = null;
  //Mutex created to create the singleton in thread-safe fashion.
  private static Object serviceLock = new Object();

  static final String PROJECT_ID = "zeppelin.bigquery.project_id";
  static final String WAIT_TIME = "zeppelin.bigquery.wait_time";
  static final String MAX_ROWS = "zeppelin.bigquery.max_no_of_rows";

  private static String jobId = null;
  private static String projectId = null;

  private static final List NO_COMPLETION = new ArrayList<>();
  private Exception exceptionOnConnect;

  private static final Function<CharSequence, String> sequenceToStringTransformer =
      new Function<CharSequence, String>() {
      public String apply(CharSequence seq) {
        return seq.toString();
      }
    };

  public BigQueryInterpreter(Properties property) {
    super(property);
  }


  //Function to return valid BigQuery Service
  @Override
  public void open() {
    if (service == null) {
      synchronized (serviceLock) {
        if (service == null) {
          try {
            service = createAuthorizedClient();
            exceptionOnConnect = null;
            logger.info("Opened BigQuery SQL Connection");
          } catch (IOException e) {
            logger.error("Cannot open connection", e);
            exceptionOnConnect = e;   
            close();
          }
        }
      }
    }
  }

  //Function that Creates an authorized client to Google Bigquery.
  private static Bigquery createAuthorizedClient() throws IOException {
    HttpTransport transport = new NetHttpTransport();
    JsonFactory jsonFactory = new JacksonFactory();
    GoogleCredential credential =  GoogleCredential.getApplicationDefault(transport, jsonFactory);

    if (credential.createScopedRequired()) {
      Collection<String> bigqueryScopes = BigqueryScopes.all();
      credential = credential.createScoped(bigqueryScopes);
    }

    return new Bigquery.Builder(transport, jsonFactory, credential)
        .setApplicationName("Zeppelin/1.0 (GPN:Apache Zeppelin;)").build();
  }

  //Function that generates and returns the schema and the rows as string
  public static String printRows(final GetQueryResultsResponse response) {
    StringBuilder msg = null;
    msg = new StringBuilder();
    try {
      for (TableFieldSchema schem: response.getSchema().getFields()) {
        msg.append(schem.getName());
        msg.append(TAB);
      }      
      msg.append(NEWLINE);
      for (TableRow row : response.getRows()) {
        for (TableCell field : row.getF()) {
          msg.append(field.getV().toString());
          msg.append(TAB);
        }
        msg.append(NEWLINE);
      }
      return msg.toString();
    } catch ( NullPointerException ex ) {
      throw new NullPointerException("SQL Execution returned an error!");
    }
  }

  //Function to poll a job for completion. Future use
  public static Job pollJob(final Bigquery.Jobs.Get request, final long interval) 
      throws IOException, InterruptedException {
    Job job = request.execute();
    while (!job.getStatus().getState().equals("DONE")) {
      System.out.println("Job is " 
          + job.getStatus().getState() 
          + " waiting " + interval + " milliseconds...");
      Thread.sleep(interval);
      job = request.execute();
    }
    return job;
  }

  //Function to page through the results of an arbitrary bigQuery request
  public static <T extends GenericJson> Iterator<T> getPages(
      final BigqueryRequest<T> requestTemplate) {
    class PageIterator implements Iterator<T> {
      private BigqueryRequest<T> request;
      private boolean hasNext = true;
      public PageIterator(final BigqueryRequest<T> requestTemplate) {
        this.request = requestTemplate;
      }
      public boolean hasNext() {
        return hasNext;
      }
      public T next() {
        if (!hasNext) {
          throw new NoSuchElementException();
        }
        try {
          T response = request.execute();
          if (response.containsKey("pageToken")) {
            request = request.set("pageToken", response.get("pageToken"));
          } else {
            hasNext = false;
          }
          return response;
        } catch (IOException e) {
          return null;
        }
      }

      public void remove() {
        this.next();
      }
    }

    return new PageIterator(requestTemplate);
  }
  
  //Function to call bigQuery to run SQL and return results to the Interpreter for output
  private InterpreterResult executeSql(String sql) {
    int counter = 0;
    StringBuilder finalmessage = null;
    finalmessage = new StringBuilder("%table ");
    String projId = getProperty(PROJECT_ID);
    long wTime = Long.parseLong(getProperty(WAIT_TIME));
    long maxRows = Long.parseLong(getProperty(MAX_ROWS));
    Iterator<GetQueryResultsResponse> pages;
    try {
      pages = run(sql, projId, wTime, maxRows);
    } catch ( IOException ex ) {
      logger.error(ex.getMessage());
      return new InterpreterResult(Code.ERROR, ex.getMessage());
    }
    try {
      while (pages.hasNext()) {
        finalmessage.append(printRows(pages.next()));
      }
      return new InterpreterResult(Code.SUCCESS, finalmessage.toString());
    } catch ( NullPointerException ex ) {
      return new InterpreterResult(Code.ERROR, ex.getMessage());
    }
  }

  //Function to run the SQL on bigQuery service
  public static Iterator<GetQueryResultsResponse> run(final String queryString, 
    final String projId, final long wTime, final long maxRows) 
      throws IOException {
    try {
      QueryResponse query = service.jobs().query(
          projId,
          new QueryRequest().setTimeoutMs(wTime).setQuery(queryString).setMaxResults(maxRows))
          .execute();
      jobId = query.getJobReference().getJobId();
      projectId = query.getJobReference().getProjectId();
      GetQueryResults getRequest = service.jobs().getQueryResults(
          projectId,
          jobId);
      return getPages(getRequest);
    } catch (IOException ex) {
      throw ex;
    }
  }

  @Override
  public void close() {

    logger.info("Close bqsql connection!");

    service = null;
  }

  @Override
  public InterpreterResult interpret(String sql, InterpreterContext contextInterpreter) {
    logger.info("Run SQL command '{}'", sql);
    return executeSql(sql);
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

    logger.info("Trying to Cancel current query statement.");

    if (service != null && jobId != null && projectId != null) {
      try {
        Bigquery.Jobs.Cancel request = service.jobs().cancel(projectId, jobId);
        JobCancelResponse response = request.execute();
        jobId = null;
        logger.info("Query Execution cancelled");
      } catch (IOException ex) {
        logger.error("Could not cancel the SQL execution");
      }
    } else {
      logger.info("Query Execution was already cancelled");
    }
  }

  @Override
  public List<InterpreterCompletion> completion(String buf, int cursor) {
    return NO_COMPLETION;
  }
}
