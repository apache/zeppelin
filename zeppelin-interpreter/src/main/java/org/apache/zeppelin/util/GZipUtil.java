/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.zeppelin.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class GZipUtil {
  private static final Logger LOGGER = LoggerFactory.getLogger(GZipUtil.class);

  public static String gzip(String context) {
    if (context == null || context.length() == 0) {
      LOGGER.warn("compressed context is null.");
      return context;
    }

    ByteArrayOutputStream out = new ByteArrayOutputStream();

    GZIPOutputStream gzip = null;
    try {
      gzip = new GZIPOutputStream(out);
      gzip.write(context.getBytes());
    } catch (IOException e) {
      LOGGER.error(e.getMessage(), e);
    }finally{
      if(gzip != null){
        try {
          gzip.close();
        } catch (IOException e) {
          LOGGER.error(e.getMessage(), e);
        }
      }
    }

    return new sun.misc.BASE64Encoder().encode(out.toByteArray());
  }

  public static String gunzip(String compressedStr){
    if(compressedStr == null){
      LOGGER.warn("decompressed context is null.");
      return null;
    }

    ByteArrayOutputStream out= new ByteArrayOutputStream();
    ByteArrayInputStream in = null;
    GZIPInputStream ginzip = null;
    byte[] compressed = null;
    String decompressed = null;
    try {
      compressed = new sun.misc.BASE64Decoder().decodeBuffer(compressedStr);
      in = new ByteArrayInputStream(compressed);
      ginzip = new GZIPInputStream(in);

      byte[] buffer = new byte[1024];
      int offset = -1;
      while ((offset = ginzip.read(buffer)) != -1) {
        out.write(buffer, 0, offset);
      }
      decompressed = out.toString();
    } catch (IOException e) {
      LOGGER.error(e.getMessage(), e);
    } finally {
      if (ginzip != null) {
        try {
          ginzip.close();
        } catch (IOException e) {
          LOGGER.error(e.getMessage(), e);
        }
      }
      if (in != null) {
        try {
          in.close();
        } catch (IOException e) {
          LOGGER.error(e.getMessage(), e);
        }
      }
      if (out != null) {
        try {
          out.close();
        } catch (IOException e) {
          LOGGER.error(e.getMessage(), e);
        }
      }
    }

    return decompressed;
  }
}
