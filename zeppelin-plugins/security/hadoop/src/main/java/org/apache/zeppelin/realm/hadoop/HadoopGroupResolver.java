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
package org.apache.zeppelin.realm.hadoop;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.security.Groups;
import org.apache.zeppelin.realm.GroupResolver;

/** Hadoop-backed group resolver for realms that support Hadoop group mappings. */
public class HadoopGroupResolver implements GroupResolver {

  private final Groups groups;

  public HadoopGroupResolver() {
    Configuration configuration = new Configuration();
    configuration.setClassLoader(HadoopGroupResolver.class.getClassLoader());
    groups = new Groups(configuration);
  }

  @Override
  public Set<String> resolve(String principal) throws IOException {
    return new HashSet<>(groups.getGroups(principal));
  }
}
