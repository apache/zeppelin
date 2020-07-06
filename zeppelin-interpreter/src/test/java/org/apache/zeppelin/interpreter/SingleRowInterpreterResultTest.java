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

import com.google.common.collect.Lists;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class SingleRowInterpreterResultTest {

  @Test
  public void testHtml() {
    List list = Lists.newArrayList("2020-01-01", 10);
    String template = "Total count:{1} for {0}";
    InterpreterContext context = InterpreterContext.builder().build();
    SingleRowInterpreterResult singleRowInterpreterResult = new SingleRowInterpreterResult(list, template, context);
    String htmlOutput = singleRowInterpreterResult.toHtml();
    assertEquals("%html Total count:10 for 2020-01-01", htmlOutput);
  }

  @Test
  public void testAngular() {
    List list = Lists.newArrayList("2020-01-01", 10);
    String template = "Total count:{1} for {0}";
    InterpreterContext context = InterpreterContext.builder().build();
    SingleRowInterpreterResult singleRowInterpreterResult = new SingleRowInterpreterResult(list, template, context);
    String angularOutput = singleRowInterpreterResult.toAngular();
    assertEquals("%angular Total count:{{value_1}} for {{value_0}}", angularOutput);
  }
}
