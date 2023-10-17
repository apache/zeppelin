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

package org.apache.zeppelin.interpreter;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class InterpreterResultTest {

  @Test
  void testTextType() {

    InterpreterResult result = new InterpreterResult(InterpreterResult.Code.SUCCESS,
        "this is a TEXT type");
    assertEquals(InterpreterResult.Type.TEXT, result.message().get(0).getType(), "No magic");
    result = new InterpreterResult(InterpreterResult.Code.SUCCESS, "%this is a TEXT type");
    assertEquals(InterpreterResult.Type.TEXT, result.message().get(0).getType(), "No magic");
    result = new InterpreterResult(InterpreterResult.Code.SUCCESS, "%\n");
    assertEquals(0, result.message().size());
  }

  @Test
  void testSimpleMagicType() {
    InterpreterResult result = null;

    result = new InterpreterResult(InterpreterResult.Code.SUCCESS,
        "%table col1\tcol2\naaa\t123\n");
    assertEquals(InterpreterResult.Type.TABLE, result.message().get(0).getType());
    result = new InterpreterResult(InterpreterResult.Code.SUCCESS,
        "%table\ncol1\tcol2\naaa\t123\n");
    assertEquals(InterpreterResult.Type.TABLE, result.message().get(0).getType());
    result = new InterpreterResult(InterpreterResult.Code.SUCCESS,
        "some text before magic word\n%table col1\tcol2\naaa\t123\n");
    assertEquals(InterpreterResult.Type.TABLE, result.message().get(1).getType());
  }

  @Test
  void testComplexMagicType() {
    InterpreterResult result = null;

    result = new InterpreterResult(InterpreterResult.Code.SUCCESS,
        "some text before %table col1\tcol2\naaa\t123\n");
    assertEquals(InterpreterResult.Type.TEXT, result.message().get(0).getType(),
        "some text before magic return magic");
    result = new InterpreterResult(InterpreterResult.Code.SUCCESS,
        "some text before\n%table col1\tcol2\naaa\t123\n");
    assertEquals(InterpreterResult.Type.TEXT, result.message().get(0).getType(),
        "some text before magic return magic");
    assertEquals(InterpreterResult.Type.TABLE, result.message().get(1).getType(),
        "some text before magic return magic");
    result = new InterpreterResult(InterpreterResult.Code.SUCCESS,
        "%html  <h3> This is a hack </h3> %table\n col1\tcol2\naaa\t123\n");
    assertEquals(InterpreterResult.Type.HTML, result.message().get(0).getType(),
        "magic A before magic B return magic A");
    result = new InterpreterResult(InterpreterResult.Code.SUCCESS,
        "some text before magic word %table col1\tcol2\naaa\t123\n %html  " +
            "<h3> This is a hack </h3>");
    assertEquals(InterpreterResult.Type.TEXT, result.message().get(0).getType(),
        "text & magic A before magic B return magic A");
    result = new InterpreterResult(InterpreterResult.Code.SUCCESS,
        "%table col1\tcol2\naaa\t123\n %html  <h3> This is a hack </h3> %table col1\naaa\n123\n");
    assertEquals(InterpreterResult.Type.TABLE, result.message().get(0).getType(),
        "magic A, magic B, magic a' return magic A");
  }

  @Test
  void testSimpleMagicData() {

    InterpreterResult result = null;

    result = new InterpreterResult(InterpreterResult.Code.SUCCESS,
        "%table col1\tcol2\naaa\t123\n");
    assertEquals("col1\tcol2\naaa\t123\n", result.message().get(0).getData(),
        "%table col1\tcol2\naaa\t123\n");
    result = new InterpreterResult(InterpreterResult.Code.SUCCESS,
        "%table\ncol1\tcol2\naaa\t123\n");
    assertEquals("col1\tcol2\naaa\t123\n", result.message().get(0).getData(),
        "%table\ncol1\tcol2\naaa\t123\n");
    result = new InterpreterResult(InterpreterResult.Code.SUCCESS,
        "some text before magic word\n%table col1\tcol2\naaa\t123\n");
    assertEquals("col1\tcol2\naaa\t123\n", result.message().get(1).getData(),
        "some text before magic word\n%table col1\tcol2\naaa\t123\n");
  }

  @Test
  void testComplexMagicData() {

    InterpreterResult result = null;

    result = new InterpreterResult(InterpreterResult.Code.SUCCESS,
        "some text before\n%table col1\tcol2\naaa\t123\n");
    assertEquals("some text before\n", result.message().get(0).getData(), "text before %table");
    assertEquals("col1\tcol2\naaa\t123\n", result.message().get(1).getData(), "text after %table");
    result = new InterpreterResult(InterpreterResult.Code.SUCCESS,
        "%html  <h3> This is a hack </h3>\n%table\ncol1\tcol2\naaa\t123\n");
    assertEquals(" <h3> This is a hack </h3>\n", result.message().get(0).getData());
    assertEquals("col1\tcol2\naaa\t123\n", result.message().get(1).getData());
    result = new InterpreterResult(InterpreterResult.Code.SUCCESS,
        "some text before magic word\n%table col1\tcol2\naaa\t123\n\n%html " +
            "<h3> This is a hack </h3>");
    assertEquals("<h3> This is a hack </h3>", result.message().get(2).getData());
    result = new InterpreterResult(InterpreterResult.Code.SUCCESS,
        "%table col1\tcol2\naaa\t123\n\n%html  <h3> This is a hack </h3>\n%table col1\naaa\n123\n");
    assertEquals("col1\naaa\n123\n", result.message().get(2).getData());
    result = new InterpreterResult(InterpreterResult.Code.SUCCESS,
        "%table " + "col1\tcol2\naaa\t123\n\n%table col1\naaa\n123\n");
    assertEquals("col1\tcol2\naaa\t123\n", result.message().get(0).getData());
    assertEquals("col1\naaa\n123\n", result.message().get(1).getData());
  }

  @Test
  void testToString() {
    assertEquals("%html hello", new InterpreterResult(InterpreterResult.Code.SUCCESS,
        "%html hello").toString());
  }

}
