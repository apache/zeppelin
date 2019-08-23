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

package org.apache.zeppelin.sap.universe;

/**
 * Info about parameter of universe query
 */
public class UniverseQueryPrompt {
  private Integer id;
  private String name;
  private String cardinality;
  private String constrained;
  private String type;
  private String value;
  private String technicalName;
  private String keepLastValues;

  public UniverseQueryPrompt() {
  }

  public UniverseQueryPrompt(Integer id, String name, String cardinality, String constrained,
                             String type, String technicalName, String keepLastValues) {
    this.id = id;
    this.name = name;
    this.cardinality = cardinality;
    this.constrained = constrained;
    this.type = type;
    this.technicalName = technicalName;
    this.keepLastValues = keepLastValues;
  }

  public Integer getId() {
    return id;
  }

  public void setId(Integer id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getCardinality() {
    return cardinality;
  }

  public void setCardinality(String cardinality) {
    this.cardinality = cardinality;
  }

  public String getValue() {
    return value;
  }

  public void setValue(String value) {
    this.value = value;
  }

  public String getConstrained() {
    return constrained;
  }

  public void setConstrained(String constrained) {
    this.constrained = constrained;
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public String getTechnicalName() {
    return technicalName;
  }

  public void setTechnicalName(String technicalName) {
    this.technicalName = technicalName;
  }

  public String getKeepLastValues() {
    return keepLastValues;
  }

  public void setKeepLastValues(String keepLastValues) {
    this.keepLastValues = keepLastValues;
  }
}
