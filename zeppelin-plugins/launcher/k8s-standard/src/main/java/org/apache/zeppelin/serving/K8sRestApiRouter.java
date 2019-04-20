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
package org.apache.zeppelin.serving;

import java.io.IOException;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.interpreter.launcher.Kubectl;
import org.apache.zeppelin.util.Util;

/**
 * Configure note serving RestAPI router in k8s environment.
 * RestAPI router will construct routing table based on informations in labels of Service of Interpreter Pod,
 * after this class's addRoute() update labels of Service.
 */
public class K8sRestApiRouter implements RestApiRouter {
  private final Kubectl kubectl;

  public K8sRestApiRouter(ZeppelinConfiguration zConf) {
    kubectl = new Kubectl(zConf.getK8sKubectlCmd());
  }

  /**
   * Add labels to kubernetes Service of Interpreter Pod.
   * So API router can scan lables of all the Services and construct routing table.
   */
  @Override
  public void addRoute(String noteId, String revId, String dnsName, String hostname, int port, String endpoint) throws IOException {
    String randomEndpointId = Util.getRandomString(5);

    String serviceName = hostname;

    kubectl.label("service", serviceName, "serving", "true");
    kubectl.label("service", serviceName, "noteId", noteId);
    kubectl.label("service", serviceName, "revId", revId);
    kubectl.label("service", serviceName, String.format("endpoint-%s", randomEndpointId), endpoint);
  }

  @Override
  public void removeRoute(String noteId, String revId) throws IOException {
  }
}
