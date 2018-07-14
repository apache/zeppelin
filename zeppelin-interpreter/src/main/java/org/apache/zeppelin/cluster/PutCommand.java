/*
 * Copyright 2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */
package org.apache.zeppelin.cluster;

import io.atomix.copycat.Command;
import org.apache.zeppelin.cluster.meta.ClusterMetaType;

import java.util.Map;

/**
 * Command to put a variable in cluster state machine
 */
public class PutCommand implements Command<Object> {
  private final ClusterMetaType type;
  private final String key;
  private final Object value;

  public PutCommand(ClusterMetaType type, String key, Map<String, Object> value){
    this.type = type;
    this.key = key;
    this.value = value;
  }

  public ClusterMetaType type(){
    return type;
  }

  public String key(){
    return key;
  }

  public Object value(){
    return value;
  }

}
