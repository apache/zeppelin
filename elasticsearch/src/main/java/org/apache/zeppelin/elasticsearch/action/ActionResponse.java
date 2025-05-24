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

package org.apache.zeppelin.elasticsearch.action;

import java.util.LinkedList;
import java.util.List;

/**
 * Contains the result of an action (hits, aggregations, ...).
 */
public class ActionResponse {

  private boolean succeeded;
  private long totalHits;
  private final List<HitWrapper> hits = new LinkedList<>();
  private final List<AggWrapper> aggregations = new LinkedList<>();


  public ActionResponse succeeded(boolean succeeded) {
    this.succeeded = succeeded;
    return this;
  }

  public boolean isSucceeded() {
    return succeeded;
  }

  public ActionResponse totalHits(long totalHits) {
    this.totalHits = totalHits;
    return this;
  }

  public long getTotalHits() {
    return totalHits;
  }

  public List<HitWrapper> getHits() {
    return hits;
  }

  public ActionResponse addHit(HitWrapper hit) {
    this.hits.add(hit);
    return this;
  }

  public List<AggWrapper> getAggregations() {
    return aggregations;
  }

  public ActionResponse addAggregation(AggWrapper aggregation) {
    this.aggregations.add(aggregation);
    return this;
  }

  public ActionResponse hit(HitWrapper hit) {
    this.addHit(hit);
    return this;
  }

  public HitWrapper getHit() {
    return this.hits.get(0);
  }
}
