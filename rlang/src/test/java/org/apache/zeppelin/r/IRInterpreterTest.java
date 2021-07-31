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

package org.apache.zeppelin.r;

import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterOutput;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResultMessage;
import org.apache.zeppelin.jupyter.IRKernelTest;
import org.junit.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;


public class IRInterpreterTest extends IRKernelTest {

  @Override
  protected Interpreter createInterpreter(Properties properties) {
    return new IRInterpreter(properties);
  }

  @Override
  protected InterpreterContext getInterpreterContext() {
    InterpreterContext context = InterpreterContext.builder()
            .setNoteId("note_1")
            .setParagraphId("paragraph_1")
            .setInterpreterOut(new InterpreterOutput())
            .setLocalProperties(new HashMap<>())
            .build();
    return context;
  }

  @Test
  public void testZShow() throws InterpreterException, IOException {
    InterpreterContext context = getInterpreterContext();
    InterpreterResult result = interpreter.interpret(
            "df=data.frame(country=c(\"US\", \"GB\", \"BR\"),\n" +
            "val1=c(10,13,14),\n" +
            "val2=c(23,12,32))", context);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());

    context = getInterpreterContext();
    result = interpreter.interpret("z.show(df)", context);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    List<InterpreterResultMessage> resultMessages = context.out.toInterpreterResultMessage();
    assertEquals(1, resultMessages.size());
    assertEquals(resultMessages.toString(),
            InterpreterResult.Type.TABLE, resultMessages.get(0).getType());
    assertEquals("country\tval1\tval2\n" +
                    "3\t10\t23\n" +
                    "2\t13\t12\n" +
                    "1\t14\t32\n" +
                    "%text ",
            resultMessages.get(0).getData());
  }
}
