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
package org.apache.zeppelin.interpreter.launcher;

import com.hubspot.jinjava.Jinjava;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.HashMap;

public class K8sSpecTemplate extends HashMap<String, Object> {
  private final Jinjava jinja = new Jinjava();

  public String render(File templateFile) throws IOException {
    String template = FileUtils.readFileToString(templateFile, Charset.defaultCharset());
    return render(template);
  }

  public String render(String template) {
    return jinja.render(template, this);
  }
}
