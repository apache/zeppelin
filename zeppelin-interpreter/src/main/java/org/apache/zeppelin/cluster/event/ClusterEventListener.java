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
package org.apache.zeppelin.cluster.event;

/**
 * Listen for the NEW_NOTE、DEL_NOTE、REMOVE_NOTE_TO_TRASH ... event
 * of the notebook in the NotebookServer#onMessage() function.
 */
public interface ClusterEventListener {
  public static final String CLUSTER_EVENT = "CLUSTER_EVENT";
  public static final String CLUSTER_EVENT_MSG = "CLUSTER_EVENT_MSG";
  
  void onClusterEvent(String msg);
}
