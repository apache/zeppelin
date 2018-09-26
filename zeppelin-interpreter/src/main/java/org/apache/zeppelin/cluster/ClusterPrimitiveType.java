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
package org.apache.zeppelin.cluster;

import io.atomix.primitive.PrimitiveBuilder;
import io.atomix.primitive.PrimitiveManagementService;
import io.atomix.primitive.PrimitiveType;
import io.atomix.primitive.config.PrimitiveConfig;
import io.atomix.primitive.service.PrimitiveService;
import io.atomix.primitive.service.ServiceConfig;

/**
 * Cluster primitive type
 * Creating a custom distributed primitive is defining the primitive type.
 * To create a new type, implement the PrimitiveType interface
 */
public class ClusterPrimitiveType implements PrimitiveType {
  public static final ClusterPrimitiveType INSTANCE = new ClusterPrimitiveType();

  public static final String PRIMITIVE_NAME = "CLUSTER_PRIMITIVE";

  @Override
  public String name() {
    return PRIMITIVE_NAME;
  }

  @Override
  public PrimitiveConfig newConfig() {
    throw new UnsupportedOperationException();
  }

  @Override
  public PrimitiveBuilder newBuilder(String primitiveName,
                                     PrimitiveConfig config,
                                     PrimitiveManagementService managementService) {
    throw new UnsupportedOperationException();
  }

  @Override
  public PrimitiveService newService(ServiceConfig config) {
    return new ClusterStateMachine();
  }
}
