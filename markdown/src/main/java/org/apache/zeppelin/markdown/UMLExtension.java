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

import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.data.MutableDataHolder;
import com.vladsch.flexmark.util.builder.Extension;


/**
 * Extension to support YUML and web sequnce diagram.
 */
public class UMLExtension implements Parser.ParserExtension, HtmlRenderer.HtmlRendererExtension {

  private UMLExtension() {

  }

  public static Extension create() {
    return new UMLExtension();
  }

  @Override
  public void rendererOptions(MutableDataHolder options) {

  }

  @Override
  public void extend(HtmlRenderer.Builder rendererBuilder, String rendererType) {
    rendererBuilder.nodeRendererFactory(new UMLNodeRenderer.Factory());
  }

  @Override
  public void parserOptions(MutableDataHolder options) {
  }

  @Override
  public void extend(Parser.Builder parserBuilder) {
    parserBuilder.customBlockParserFactory(new UMLBlockQuoteParser.Factory());
  }
}
