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
