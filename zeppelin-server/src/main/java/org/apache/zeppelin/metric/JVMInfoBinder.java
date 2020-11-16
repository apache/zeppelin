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
package org.apache.zeppelin.metric;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;

public class JVMInfoBinder implements MeterBinder {
  private static final String UNKNOWN = "unknown";

  @Override
  public void bindTo(MeterRegistry registry) {
      Counter.builder("jvm.info")
              .description("JVM version info")
              .tags("version", System.getProperty("java.runtime.version", UNKNOWN),
                      "vendor", System.getProperty("java.vm.vendor", UNKNOWN),
                      "runtime", System.getProperty("java.runtime.name", UNKNOWN))
              .register(registry)
              .increment();
  }
}
