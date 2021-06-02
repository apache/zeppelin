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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;

public class YarnLauncherUtil {

  private static final Logger LOGGER = LoggerFactory.getLogger(YarnLauncherUtil.class);

  public static URI resolveURI(String path) {
    try {
      URI uri = new URI(path);
      if (uri.getScheme() != null) {
        return uri;
      }
      // make sure to handle if the path has a fragment (applies to yarn
      // distributed cache)
      if (uri.getFragment() != null) {
        URI absoluteURI = new File(uri.getPath()).getAbsoluteFile().toURI();
        return new URI(absoluteURI.getScheme(), absoluteURI.getHost(), absoluteURI.getPath(),
                uri.getFragment());
      }
    } catch (URISyntaxException e) {
      LOGGER.warn("Exception when resolveURI: {}, cause : {}", path, e);
    }
    return new File(path).getAbsoluteFile().toURI();
  }
}
