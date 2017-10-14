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

package org.apache.zeppelin.user;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.Arrays;

import org.bouncycastle.crypto.BufferedBlockCipher;
import org.bouncycastle.crypto.InvalidCipherTextException;
import org.bouncycastle.crypto.engines.AESEngine;
import org.bouncycastle.crypto.paddings.PaddedBufferedBlockCipher;
import org.bouncycastle.crypto.paddings.ZeroBytePadding;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.util.encoders.Base64;

/**
 * Encrypt/decrypt arrays of bytes!
 */
public class Encryptor {
  private final BufferedBlockCipher encryptCipher;
  private final BufferedBlockCipher decryptCipher;

  public Encryptor(String encryptKey) {
    encryptCipher = new PaddedBufferedBlockCipher(new AESEngine(), new ZeroBytePadding());
    encryptCipher.init(true, new KeyParameter(encryptKey.getBytes()));

    decryptCipher = new PaddedBufferedBlockCipher(new AESEngine(), new ZeroBytePadding());
    decryptCipher.init(false, new KeyParameter(encryptKey.getBytes()));
  }


  public String encrypt(String inputString) throws IOException {
    byte[] input = inputString.getBytes();
    byte[] result = new byte[encryptCipher.getOutputSize(input.length)];
    int size = encryptCipher.processBytes(input, 0, input.length, result, 0);

    try {
      size += encryptCipher.doFinal(result, size);

      byte[] out = new byte[size];
      System.arraycopy(result, 0, out, 0, size);
      return new String(Base64.encode(out));
    } catch (InvalidCipherTextException e) {
      throw new IOException("Cannot encrypt: " + e.getMessage(), e);
    }
  }

  public String decrypt(String base64Input) throws IOException {
    byte[] input = Base64.decode(base64Input);
    byte[] result = new byte[decryptCipher.getOutputSize(input.length)];
    int size = decryptCipher.processBytes(input, 0, input.length, result, 0);

    try {
      size += decryptCipher.doFinal(result, size);

      byte[] out = new byte[size];
      System.arraycopy(result, 0, out, 0, size);
      return new String(out);
    } catch (InvalidCipherTextException e) {
      throw new IOException("Cannot decrypt: " + e.getMessage(), e);
    }
  }
}
