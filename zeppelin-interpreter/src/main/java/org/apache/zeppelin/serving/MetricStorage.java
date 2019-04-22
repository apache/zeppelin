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

import java.util.Date;
import java.util.List;
import java.util.Map;

public interface MetricStorage {
  double incr(Date date, String endpoint, String key, double n);
  void set(Date date, String endpoint, String key, String value);
  Object get(Date date, String endpoint, String key);
  Map<String, String> get(Date date, String endpoint);
  Map<String, String> get(Date date, String noteId, String revId, String endpoint);
  List<Map<String, Object>> get(Date from, Date to, String noteId, String revId, String endpoint);
}
