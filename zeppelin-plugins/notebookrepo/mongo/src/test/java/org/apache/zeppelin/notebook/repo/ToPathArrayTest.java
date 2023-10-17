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

package org.apache.zeppelin.notebook.repo;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import java.util.stream.Stream;


class ToPathArrayTest {

  private MongoNotebookRepo repo = new MongoNotebookRepo();

  private static Stream<Arguments> data() {
    return Stream.of(
      Arguments.of(null, true, null),
      Arguments.of(null, false, null),
      Arguments.of("", true, null),
      Arguments.of("", false, null),
      Arguments.of("/", true, new String[0]),
      Arguments.of("/", false, new String[0]),

      Arguments.of("/abc", true, new String[] { "abc" }),
      Arguments.of("/abc/", true, new String[] { "abc" }),
      Arguments.of("/a/b/c", true, new String[] { "a", "b", "c" }),
      Arguments.of("/a/b//c/", true, new String[] { "a", "b", "c" }),

      Arguments.of("/abc", false, new String[] {}),
      Arguments.of("/abc/", false, new String[] {}),
      Arguments.of("/a/b/c", false, new String[] { "a", "b" }),
      Arguments.of("/a/b//c/", false, new String[] { "a", "b" }),

      Arguments.of("abc", true, new String[] { "abc" }),
      Arguments.of("abc/", true, new String[] { "abc" }),
      Arguments.of("a/b/c", true, new String[] { "a", "b", "c" }),
      Arguments.of("a/b//c/", true, new String[] { "a", "b", "c" }),

      Arguments.of("abc", false, new String[] {}),
      Arguments.of("abc/", false, new String[] {}),
      Arguments.of("a/b/c", false, new String[] { "a", "b" }),
      Arguments.of("a/b//c/", false, new String[] { "a", "b" }));
  }


  @ParameterizedTest
  @MethodSource("data")
  void runTest(String pathStr, boolean includeLast, String[] expactPathArray) {
    if (expactPathArray == null) {
      runForThrow(pathStr, includeLast, expactPathArray);
    } else {
      runNormally(pathStr, includeLast, expactPathArray);
    }
  }

  private void runForThrow(String pathStr, boolean includeLast, String[] expactPathArray) {
    assertThrows(NullPointerException.class, () -> {
      runNormally(pathStr, includeLast, expactPathArray);
    });

  }

  private void runNormally(String pathStr, boolean includeLast, String[] expactPathArray) {
    String[] pathArray = repo.toPathArray(pathStr, includeLast);
    assertArrayEquals(expactPathArray, pathArray);
  }
}
