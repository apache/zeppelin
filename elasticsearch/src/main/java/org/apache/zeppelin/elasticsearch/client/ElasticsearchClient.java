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
 * Interface that must be implemented by any kind of Elasticsearch client (transport, ...).
 */
public interface ElasticsearchClient {

  ActionResponse get(String index, String type, String id);

  ActionResponse index(String index, String type, String id, String data);

  ActionResponse delete(String index, String type, String id);

  ActionResponse search(String[] indices, String[] types, String query, int size);

  void close();
}
