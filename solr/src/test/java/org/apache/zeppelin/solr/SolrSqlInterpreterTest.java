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

package org.apache.zeppelin.solr;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

import java.util.Properties;

import org.apache.lucene.util.TestUtil;
import org.apache.lucene.util.LuceneTestCase.Slow;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.request.CollectionAdminRequest;
import org.apache.solr.client.solrj.request.UpdateRequest;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.response.UpdateResponse;
import org.apache.solr.cloud.AbstractDistribZkTestBase;
import org.apache.solr.cloud.SolrCloudTestCase;
import org.apache.solr.common.SolrInputDocument;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterResult;

/**
 * Tests for Solr SQL Interpreter
 */
@Slow
public class SolrSqlInterpreterTest extends SolrCloudTestCase {
  private static Logger logger = LoggerFactory.getLogger(SolrSqlInterpreterTest.class);

  private static final String COLLECTION = "collection1";
  private static final String id = "id";
  private static final String CONF_DIR = "src/test/resources/solr/" + COLLECTION + "/conf";
  private static final int TIMEOUT = 30;

  @BeforeClass
  public static void setupCluster() throws Exception {
    configureCluster(3)
        .addConfig("conf",getFile(CONF_DIR).toPath())
        .configure();

    CollectionAdminRequest.createCollection(COLLECTION, "conf", 2, 1)
        .process(cluster.getSolrClient());
    AbstractDistribZkTestBase.waitForRecoveriesToFinish(
        COLLECTION,
        cluster.getSolrClient().getZkStateReader(),
        false,
        true,
        TIMEOUT);
  }

  @Before
  public void prepareIndex() throws Exception {
    new UpdateRequest()
        .deleteByQuery("*:*")
        .commit(cluster.getSolrClient(), COLLECTION);

    new UpdateRequest()
        .add("id", "0", "text", "hello")
        .add("id", "1", "text", "solr")
        .commit(cluster.getSolrClient(), COLLECTION);
  }

  @Test
  public void updateTest() throws Exception {
    QueryResponse resp = cluster.getSolrClient().query(COLLECTION, new SolrQuery("*:*"));
    assertEquals(2, resp.getResults().getNumFound());
  }

  @Test
  public void sampleTest() throws Exception {
    Properties properties = new Properties();
    properties.setProperty(SolrSqlInterpreter.DEFAULT_DRIVER, SolrSqlInterpreter.DRIVER_DEFAUL);
    properties.setProperty(SolrSqlInterpreter.DEFAULT_ZK_HOST, cluster.getZkServer().getZkAddress());
    properties.setProperty(SolrSqlInterpreter.DEFAULT_COLLECTION, SolrSqlInterpreter.COLLECTION_DEFAULT);
    properties.setProperty(SolrSqlInterpreter.DEFAULT_AGGREGATION_MODE, SolrSqlInterpreter.AGGREGATION_MODE_DEFAULT);
    properties.setProperty(SolrSqlInterpreter.DEFAULT_NUM_WORKERS, SolrSqlInterpreter.NUM_WORKERS_DEFAULT);
    properties.setProperty(SolrSqlInterpreter.COMMON_MAX_COUNT, SolrSqlInterpreter.MAX_COUNT_DEFAULT);
    SolrSqlInterpreter solrSqlInterpreter = new SolrSqlInterpreter(properties);
    solrSqlInterpreter.open();

    String sqlQuery = "SELECT id, text FROM collection1 WHERE id = '1'";
    InterpreterResult interpreterResult = solrSqlInterpreter.interpret(sqlQuery, new InterpreterContext("", "1", "", "", null, null, null, null, null, null, null));

    assertEquals(InterpreterResult.Code.SUCCESS, interpreterResult.code());
    assertEquals(InterpreterResult.Type.TABLE, interpreterResult.type());
    assertEquals("id\ttext\n1\tsolr\n", interpreterResult.message());
    
    solrSqlInterpreter.close();
  }
}
