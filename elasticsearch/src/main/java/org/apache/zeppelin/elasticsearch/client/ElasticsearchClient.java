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

package org.apache.zeppelin.elasticsearch.client;

import org.apache.zeppelin.elasticsearch.action.ActionResponse;

/**
 * Interface defining a contract for Elasticsearch client implementations.
 * <p>
 * Implementations may use Transport Client (deprecated), High Level REST Client,
 * or the new Elasticsearch Java API Client.
 * </p>
 *
 * <p>
 * This interface is intended to abstract the underlying Elasticsearch connection
 * mechanism, allowing Zeppelin interpreters to interact with different versions
 * of Elasticsearch transparently.
 * </p>
 *
 * @see org.apache.zeppelin.elasticsearch.action.ActionResponse
 */
public interface ElasticsearchClient {

  /**
   * Retrieves a document by its ID.
   *
   * @param index The index name.
   * @param type  The document type. (Note: deprecated in Elasticsearch 7+, use "_doc")
   * @param id    The unique document ID.
   * @return The response containing the document if found, or an error response.
   */
  ActionResponse get(String index, String type, String id);

  /**
   * Indexes a document (insert or update).
   *
   * @param index The index name.
   * @param type  The document type.
   * @param id    The document ID. If null, a random ID may be generated depending on
   *              implementation.
   * @param data  The JSON string of the document to index.
   * @return The response indicating success or failure.
   */
  ActionResponse index(String index, String type, String id, String data);

  /**
   * Deletes a document by its ID.
   *
   * @param index The index name.
   * @param type  The document type.
   * @param id    The document ID to delete.
   * @return The response indicating result of the deletion.
   */
  ActionResponse delete(String index, String type, String id);

  /**
   * Executes a search query on one or more indices and types.
   *
   * @param indices Array of index names to search.
   * @param types   Array of document types to search (can be null or ["_doc"] in ES 7+).
   * @param query   The raw JSON query string.
   * @param size    Maximum number of documents to return.
   * @return The response containing the search hits.
   */
  ActionResponse search(String[] indices, String[] types, String query, int size);

  /**
   * Closes any open connections and cleans up resources.
   * <p>
   * Must be called when the client is no longer needed.
   * </p>
   */
  void close();
}
