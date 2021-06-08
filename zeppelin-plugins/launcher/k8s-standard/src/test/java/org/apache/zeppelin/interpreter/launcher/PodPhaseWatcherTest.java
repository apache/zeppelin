/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.zeppelin.interpreter.launcher;

import static org.junit.Assert.*;

import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.StringUtils;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.api.model.PodStatus;
import io.fabric8.kubernetes.api.model.PodStatusBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.server.mock.KubernetesServer;

public class PodPhaseWatcherTest {

  @Rule
  public KubernetesServer server = new KubernetesServer(false, true);

  @Test
  @Ignore("Reamer - ZEPPELIN-5403")
  public void testPhase() throws InterruptedException {
    KubernetesClient client = server.getClient();
    // CREATE
    client.pods().inNamespace("ns1")
        .create(new PodBuilder().withNewMetadata().withName("pod1").endMetadata().build());
    // READ
    PodList podList = client.pods().inNamespace("ns1").list();
    assertNotNull(podList);
    assertEquals(1, podList.getItems().size());
    Pod pod = podList.getItems().get(0);
    // WATCH
    PodPhaseWatcher podWatcher = new PodPhaseWatcher(
        phase -> StringUtils.equalsAnyIgnoreCase(phase, "Succeeded", "Failed", "Running"));
    Watch watch = client.pods().inNamespace("ns1").withName("pod1").watch(podWatcher);

    // Update Pod to "pending" phase
    pod.setStatus(new PodStatus(null, null, null, null, null, null, null, "Pending", null, null,
        null, null, null));
    client.pods().inNamespace("ns1").updateStatus(pod);

    // Update Pod to "Running" phase
    pod.setStatus(new PodStatusBuilder(new PodStatus(null, null, null, null, null, null, null,
        "Running", null, null, null, null, null)).build());
    client.pods().inNamespace("ns1").updateStatus(pod);

    assertTrue(podWatcher.getCountDownLatch().await(1, TimeUnit.SECONDS));
    watch.close();
  }

  @Test
  @Ignore("Reamer - ZEPPELIN-5403")
  public void testPhaseWithError() throws InterruptedException {
    KubernetesClient client = server.getClient();
    // CREATE
    client.pods().inNamespace("ns1")
        .create(new PodBuilder().withNewMetadata().withName("pod1").endMetadata().build());
    // READ
    PodList podList = client.pods().inNamespace("ns1").list();
    assertNotNull(podList);
    assertEquals(1, podList.getItems().size());
    // WATCH
    PodPhaseWatcher podWatcher = new PodPhaseWatcher(
        phase -> StringUtils.equalsAnyIgnoreCase(phase, "Succeeded", "Failed", "Running"));
    Watch watch = client.pods().inNamespace("ns1").withName("pod1").watch(podWatcher);

    // In the case of close, we do not block thread execution
    watch.close();
    assertTrue(podWatcher.getCountDownLatch().await(1, TimeUnit.SECONDS));
  }
}
