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

package org.apache.zeppelin.sparql;

import org.apache.commons.lang3.StringUtils;

import org.apache.http.HttpStatus;

import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QueryParseException;
import org.apache.jena.query.ResultSet;
import org.apache.jena.query.ResultSetFormatter;
import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.sparql.ARQException;
import org.apache.jena.sparql.engine.http.QueryExceptionHTTP;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.util.Map;
import java.util.Properties;

import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterResult;


/**
 * Interpreter for SPARQL-Query via Apache Jena ARQ.
 */
public class SparqlInterpreter extends Interpreter {
  private static final Logger LOGGER = LoggerFactory.getLogger(SparqlInterpreter.class);

  public static final String SPARQL_SERVICE_ENDPOINT = "sparql.endpoint";
  public static final String SPARQL_REPLACE_URIS = "sparql.replaceURIs";
  public static final String SPARQL_REMOVE_DATATYPES = "sparql.removeDatatypes";

  private String serviceEndpoint;
  private boolean replaceURIs;
  private boolean removeDatatypes;

  private QueryExecution queryExecution;

  public SparqlInterpreter(Properties properties) {
    super(properties);
  }

  @Override
  public void open() {
    LOGGER.info("Properties: {}", getProperties());

    serviceEndpoint = getProperty(SPARQL_SERVICE_ENDPOINT);
    replaceURIs = getProperty(SPARQL_REPLACE_URIS) != null
        && getProperty(SPARQL_REPLACE_URIS).equals("true");
    removeDatatypes = getProperty(SPARQL_REMOVE_DATATYPES) != null
        && getProperty(SPARQL_REMOVE_DATATYPES).equals("true");

    queryExecution = null;
  }

  @Override
  public void close() {
    if (queryExecution != null) {
      queryExecution.close();
    }
  }

  @Override
  public InterpreterResult interpret(String query, InterpreterContext context) {
    LOGGER.info("SPARQL: Run Query '" + query + "' against " + serviceEndpoint);

    if (StringUtils.isEmpty(query) || StringUtils.isEmpty(query.trim())) {
      return new InterpreterResult(InterpreterResult.Code.SUCCESS);
    }

    try {
      queryExecution = QueryExecutionFactory.sparqlService(serviceEndpoint, query);
      PrefixMapping prefixMapping = queryExecution.getQuery().getPrefixMapping();

      // execute query and get Results
      ResultSet results = queryExecution.execSelect();

      // transform ResultSet to TSV-String
      ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
      ResultSetFormatter.outputAsTSV(outputStream, results);
      String tsv = new String(outputStream.toByteArray());

      if (replaceURIs) {
        LOGGER.info("SPARQL: Replacing URIs");
        tsv = replaceURIs(tsv, prefixMapping);
      }

      if (removeDatatypes) {
        LOGGER.info("SPARQL: Removing datatypes");
        tsv = removeDatatypes(tsv);
      }

      return new InterpreterResult(
              InterpreterResult.Code.SUCCESS,
              InterpreterResult.Type.TABLE,
              tsv);
    } catch (QueryParseException e) {
      LOGGER.error(e.toString());
      return new InterpreterResult(
        InterpreterResult.Code.ERROR,
        "Error: " + e.getMessage());
    } catch (QueryExceptionHTTP e) {
      LOGGER.error(e.toString());
      int responseCode = e.getResponseCode();

      if (responseCode == HttpStatus.SC_UNAUTHORIZED) {
        return new InterpreterResult(
          InterpreterResult.Code.ERROR,
            "Unauthorized.");
      } else if (responseCode == HttpStatus.SC_NOT_FOUND) {
        return new InterpreterResult(
          InterpreterResult.Code.ERROR,
            "Endpoint not found, please check endpoint in the configuration.");
      } else {
        return new InterpreterResult(
          InterpreterResult.Code.ERROR,
            "Error: " + e.getMessage());
      }
    } catch (ARQException e) {
      return new InterpreterResult(
        InterpreterResult.Code.INCOMPLETE,
          "Query cancelled.");
    }
  }

  @Override
  public void cancel(InterpreterContext context) {
    if (queryExecution != null) {
      queryExecution.abort();
    }
  }

  @Override
  public FormType getFormType() {
    return FormType.SIMPLE;
  }

  @Override
  public int getProgress(InterpreterContext context) {
    return 0;
  }

  private String replaceURIs(String tsv, PrefixMapping prefixMapping) {
    Map<String, String> pmap = prefixMapping.getNsPrefixMap();
    for (Map.Entry<String, String> entry : pmap.entrySet()) {
      tsv = tsv.replaceAll(entry.getValue(), entry.getKey() + ":");
    }
    return tsv;
  }

  private String removeDatatypes(String tsv) {
    // capture group: "($1)"^^<.+?>
    return tsv.replaceAll("\"(.+?)\"\\^\\^\\<.+?\\>", "$1");
  }
}
