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

package org.apache.zeppelin.pig;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

/**
 *
 */
public class PigUtils {
  private static final Logger LOGGER = LoggerFactory.getLogger(PigUtils.class);

  protected static final String DATE_FORMAT = "yyyy-MM-dd HH:mm:ss";

  public static File createTempPigScript(String content) throws IOException {
    File tmpFile = File.createTempFile("zeppelin", "pig");
    LOGGER.debug("Create pig script file:" + tmpFile.getAbsolutePath());
    FileWriter writer = new FileWriter(tmpFile);
    IOUtils.write(content, writer);
    writer.close();
    return tmpFile.getAbsoluteFile();
  }

  public static File createTempPigScript(List<String> lines) throws IOException {
    return createTempPigScript(StringUtils.join(lines, "\n"));
  }
}
