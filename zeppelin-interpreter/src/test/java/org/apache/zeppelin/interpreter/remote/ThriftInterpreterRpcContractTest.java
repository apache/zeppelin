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

package org.apache.zeppelin.interpreter.remote;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.apache.thrift.transport.TTransportException;
import org.apache.zeppelin.interpreter.thrift.InterpreterRPCException;
import org.junit.jupiter.api.Test;

/** Runs the shared Server-to-Interpreter control RPC contract against the current Thrift wire. */
public class ThriftInterpreterRpcContractTest extends AbstractInterpreterRpcContractTest {

  private ThriftInterpreterRpcContractDriver thriftDriver;

  @Override
  protected InterpreterRpcContractDriver createDriver(Path localRepository, String probeId) {
    thriftDriver = new ThriftInterpreterRpcContractDriver(localRepository, probeId);
    return thriftDriver;
  }

  @Test
  void shouldSurfaceClosedSocketAsRawThriftTransportFailure() {
    thriftDriver.closeControlTransportForWireTest();
    assertThrows(TTransportException.class, thriftDriver::getRawStatusForWireTest);
  }

  @Test
  void shouldSurfaceDeclaredThriftInterpreterFailureOnTheWire() {
    InterpreterRPCException failure = assertThrows(
        InterpreterRPCException.class,
        thriftDriver::getRawMissingInterpreterFormTypeForWireTest);
    assertTrue(failure.getErrorMessage().contains("not initialized"));
  }
}
