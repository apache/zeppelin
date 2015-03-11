package com.nflabs.zeppelin.interpreter.remote;

import static org.junit.Assert.assertTrue;

import java.io.IOException;

import org.junit.Test;

public class RemoteInterpreterUtilsTest {

  @Test
  public void testFindRandomAvailablePortOnAllLocalInterfaces() throws IOException {
    assertTrue(RemoteInterpreterUtils.findRandomAvailablePortOnAllLocalInterfaces() > 0);
  }

}
