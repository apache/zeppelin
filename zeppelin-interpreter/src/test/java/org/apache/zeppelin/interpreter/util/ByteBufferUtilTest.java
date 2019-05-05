package org.apache.zeppelin.interpreter.util;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ByteBufferUtilTest {

  @Test
  public void fromByteBufferToByteBuffer() {
    String str = "Hello world";
    ByteBuffer byteBuffer = ByteBufferUtils.stringToByteBuffer(str, Charset.defaultCharset());
    assertEquals(str, ByteBufferUtils.ByteBufferToString(byteBuffer, Charset.defaultCharset()));
  }
}
