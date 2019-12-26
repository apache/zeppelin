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
 * Cluster Event
 */
public enum ClusterEvent {
  // CLUSTER_INTP_EVENT_TOPIC
  CREATE_INTP_PROCESS,
  // CLUSTER_NOTE_EVENT_TOPIC
  BROADCAST_NOTE,
  BROADCAST_NOTE_LIST,
  BROADCAST_PARAGRAPH,
  BROADCAST_PARAGRAPHS,
  BROADCAST_NEW_PARAGRAPH,
  UPDATE_NOTE_PERMISSIONS,
  // CLUSTER_AUTH_EVENT_TOPIC
  SET_ROLES,
  SET_READERS_PERMISSIONS,
  SET_RUNNERS_PERMISSIONS,
  SET_WRITERS_PERMISSIONS,
  SET_OWNERS_PERMISSIONS,
  CLEAR_PERMISSION,
  // CLUSTER_NBAUTH_EVENT_TOPIC
  SET_NEW_NOTE_PERMISSIONS,
  // CLUSTER_INTP_SETTING_EVENT_TOPIC
  CREATE_INTP_SETTING,
  UPDATE_INTP_SETTING,
  DELETE_INTP_SETTING,
}
