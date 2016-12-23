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

package org.apache.zeppelin.dep;

import org.junit.Test;

import java.nio.file.Paths;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public class BooterTest {

  @Test
  public void should_return_absolute_path() {
    String resolvedPath = Booter.resolveLocalRepoPath("path");
    assertTrue(Paths.get(resolvedPath).isAbsolute());
  }

  @Test
  public void should_not_change_absolute_path() {
    String absolutePath
        = Paths.get("first", "second").toAbsolutePath().toString();
    String resolvedPath = Booter.resolveLocalRepoPath(absolutePath);

    assertThat(resolvedPath, equalTo(absolutePath));
  }

  @Test(expected = NullPointerException.class)
  public void should_throw_exception_for_null() {
    Booter.resolveLocalRepoPath(null);
  }
}
