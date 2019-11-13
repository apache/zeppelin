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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;

import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterResult;


/**
 * Interpreter for SPARQL-Query via Apache Jena ARQ.
 */
public class SparqlInterpreter extends Interpreter {
  private static final Logger LOGGER = LoggerFactory.getLogger(SparqlInterpreter.class);

  public static final String SPARQL_ENGINE_TYPE = "sparql.engine";
  public static final String SPARQL_SERVICE_ENDPOINT = "sparql.endpoint";
  public static final String SPARQL_REPLACE_URIS = "sparql.replaceURIs";
  public static final String SPARQL_REMOVE_DATATYPES = "sparql.removeDatatypes";

  public static final String ENGINE_TYPE_JENA = "jena";

  public SparqlEngine engine;
  
  /**
   * Sparql Engine Type.
   */
  public enum SparqlEngineType {
    JENA {
      @Override
      public String toString() {
        return ENGINE_TYPE_JENA;
      }
    }
  }

  public SparqlInterpreter(Properties properties) {
    super(properties);
  }

  @Override
  public void open() {
    LOGGER.info("Properties: {}", getProperties());

    String serviceEndpoint = getProperty(SPARQL_SERVICE_ENDPOINT);
    boolean replaceURIs = getProperty(SPARQL_REPLACE_URIS) != null
        && getProperty(SPARQL_REPLACE_URIS).equals("true");
    boolean removeDatatypes = getProperty(SPARQL_REMOVE_DATATYPES) != null
        && getProperty(SPARQL_REMOVE_DATATYPES).equals("true");
    String engineType = getProperty(SPARQL_ENGINE_TYPE);

    if (SparqlEngineType.JENA.toString().equals(engineType)) {
      engine = new JenaInterpreter(serviceEndpoint, replaceURIs, removeDatatypes);
    } 
  }

  @Override
  public void close() {
    engine.close();
  }

  @Override
  public InterpreterResult interpret(String query, InterpreterContext context) {
    if (StringUtils.isEmpty(query) || StringUtils.isEmpty(query.trim())) {
      return new InterpreterResult(InterpreterResult.Code.SUCCESS);
    }

    return engine.query(query);
  }

  @Override
  public void cancel(InterpreterContext context) {
    engine.cancel();
  }

  @Override
  public FormType getFormType() {
    return FormType.SIMPLE;
  }

  @Override
  public int getProgress(InterpreterContext context) {
    return 0;
  }
}
