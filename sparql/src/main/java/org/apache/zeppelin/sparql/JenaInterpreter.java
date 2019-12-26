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

import org.apache.zeppelin.interpreter.InterpreterResult;


/**
 * Interpreter for SPARQL-Query via Apache Jena ARQ.
 */
public class JenaInterpreter implements SparqlEngine {
  private static final Logger LOGGER = LoggerFactory.getLogger(JenaInterpreter.class);

  private String serviceEndpoint;
  private boolean replaceURIs;
  private boolean removeDatatypes;

  private QueryExecution queryExecution;

  public JenaInterpreter(String serviceEndpoint, boolean replaceURIs, boolean removeDatatypes) {
    this.serviceEndpoint = serviceEndpoint;
    this.replaceURIs = replaceURIs;
    this.removeDatatypes = removeDatatypes;
  }

  @Override
  public InterpreterResult query(String query) {
    LOGGER.info("SPARQL: Run Query '" + query + "' against " + serviceEndpoint);

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
  public void cancel() {
    if (queryExecution != null) {
      queryExecution.abort();
    }
  }

  @Override
  public void close() {
    if (queryExecution != null) {
      queryExecution.close();
    }
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
