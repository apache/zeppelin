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

package org.apache.zeppelin.util;

import org.apache.commons.codec.binary.Base64;

/**
 * Simple encoder for string
 */
public class StringXORer {

  public static String encode(String s, String key) {
    return Base64.encodeBase64String(xorWithKey(s.getBytes(), key.getBytes()));
  }

  public static String decode(String s, String key) {
    return new String(xorWithKey(Base64.decodeBase64(s.getBytes()), key.getBytes()));
  }

  private static byte[] xorWithKey(byte[] a, byte[] key) {
    byte[] out = new byte[a.length];
    for (int i = 0; i < a.length; i++) {
      out[i] = (byte) (a[i] ^ key[i % key.length]);
    }
    return out;
  }
}
