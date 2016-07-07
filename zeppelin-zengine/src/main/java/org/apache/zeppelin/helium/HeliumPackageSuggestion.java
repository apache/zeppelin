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
package org.apache.zeppelin.helium;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

/**
 * Suggested apps
 */
public class HeliumPackageSuggestion {
  private final List<HeliumPackageSearchResult> available =
      new LinkedList<HeliumPackageSearchResult>();

  /*
   * possible future improvement
   * provides n - 'favorite' list, based on occurrence of apps in notebook
   */

  public HeliumPackageSuggestion() {

  }

  public void addAvailablePackage(HeliumPackageSearchResult r) {
    available.add(r);

  }

  public void sort() {
    Collections.sort(available, new Comparator<HeliumPackageSearchResult>() {
      @Override
      public int compare(HeliumPackageSearchResult o1, HeliumPackageSearchResult o2) {
        return o1.getPkg().getName().compareTo(o2.getPkg().getName());
      }
    });
  }
}
