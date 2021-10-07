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

package org.apache.zeppelin.search;

import javax.inject.Inject;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class NoSearchService extends SearchService {

  @Inject
  public NoSearchService() {
    super("NoSearchService");
  }

  @Override
  public List<Map<String, String>> query(String queryStr) {
    return Collections.emptyList();
  }

  @Override
  public void updateNoteIndex(String noteId) throws IOException {

  }

  @Override
  public void updateParagraphIndex(String noteId, String paragraphId) throws IOException {

  }

  @Override
  public void addNoteIndex(String noteId) throws IOException {

  }

  @Override
  public void addParagraphIndex(String noteId, String paragraphId) throws IOException {

  }

  @Override
  public void deleteNoteIndex(String noteId) throws IOException {

  }

  @Override
  public void deleteParagraphIndex(String noteId, String paragraphId) throws IOException {

  }
}
