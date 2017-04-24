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

package org.apache.zeppelin.markdown;

import org.markdown4j.Markdown4jProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Markdown Parser using markdown4j processor.
 */
public class Markdown4jParser implements MarkdownParser {
  private Markdown4jProcessor processor;

  public Markdown4jParser() {
    processor = new Markdown4jProcessor();
  }

  @Override
  public String render(String markdownText) {
    String html = "";

    try {
      html = processor.process(markdownText);
    } catch (IOException e) {
      // convert checked exception to non-checked exception
      throw new RuntimeException(e);
    }

    return html;
  }
}
