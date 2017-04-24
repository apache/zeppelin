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

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.util.LinkedList;
import java.util.List;

/**
 * Simple Helium registry on local filesystem
 */
public class HeliumLocalRegistry extends HeliumRegistry {
  Logger logger = LoggerFactory.getLogger(HeliumLocalRegistry.class);

  private final Gson gson;

  public HeliumLocalRegistry(String name, String uri) {
    super(name, uri);
    gson = new Gson();

  }

  @Override
  public synchronized List<HeliumPackage> getAll() throws IOException {
    List<HeliumPackage> result = new LinkedList<>();

    File file = new File(uri());
    File [] files = file.listFiles();
    if (files == null) {
      return result;
    }

    for (File f : files) {
      if (f.getName().startsWith(".")) {
        continue;
      }

      HeliumPackage pkgInfo = readPackageInfo(f);
      if (pkgInfo != null) {
        result.add(pkgInfo);
      }
    }
    return result;
  }

  private HeliumPackage readPackageInfo(File f) {
    try {
      JsonReader reader = new JsonReader(new StringReader(FileUtils.readFileToString(f)));
      reader.setLenient(true);

      return gson.fromJson(reader, HeliumPackage.class);
    } catch (IOException e) {
      logger.error(e.getMessage(), e);
      return null;
    }
  }

}
