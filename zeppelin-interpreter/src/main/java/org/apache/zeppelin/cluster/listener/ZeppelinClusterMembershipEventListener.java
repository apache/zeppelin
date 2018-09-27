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
package org.apache.zeppelin.cluster.listener;

import io.atomix.cluster.ClusterMembershipEvent;
import io.atomix.cluster.ClusterMembershipEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entity capable of receiving device cluster-related events.
 * Listen for new zeppelin servers to join or leave the cluster,
 * Monitor whether the metadata in the cluster server changes
 */
public class ZeppelinClusterMembershipEventListener implements ClusterMembershipEventListener {
  private static Logger logger
      = LoggerFactory.getLogger(ZeppelinClusterMembershipEventListener.class);

  @Override
  public void event(ClusterMembershipEvent event) {
    switch (event.type()) {
      case MEMBER_ADDED:
        logger.info(event.subject().id() + " joined the cluster.");
        break;
      case MEMBER_REMOVED:
        logger.info(event.subject().id() + " left the cluster.");
        break;
      case METADATA_CHANGED:
        logger.info(event.subject().id() + " meta data changed.");
        break;
      case REACHABILITY_CHANGED:
        logger.info(event.subject().id() + " reachability changed.");
        break;
    }
  }
}
