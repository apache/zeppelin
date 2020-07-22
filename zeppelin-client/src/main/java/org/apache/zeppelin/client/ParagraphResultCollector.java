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

package org.apache.zeppelin.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class ParagraphResultCollector {

  private static final Logger LOGGER = LoggerFactory.getLogger(ParagraphResultCollector.class);

  private String noteId;
  private String paragraphId;
  private List<Result> results = new ArrayList<>();

  public void onOutputAppend(String noteId,
                             String paragraphId,
                             int index,
                             String output) {
    if (index > (results.size() - 1)) {
      LOGGER.warn("Get output append for index: " + index +
              ", but currently there's only " + results.size() + " results");
    }
    results.get(index).appendData(output);
  }


  public void onOutputUpdated(String noteId,
                              String paragraphId,
                              int index,
                              String type,
                              String output) {
    Result result = new Result(type, output);
    if (index < results.size()) {
      results.set(index, result);
    } else {
      results.add(result);
    }
  }

  public void onOutputClear(String noteId,
                            String paragraphId) {

  }

}
