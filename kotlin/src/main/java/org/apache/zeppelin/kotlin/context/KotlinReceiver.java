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

package org.apache.zeppelin.kotlin.context;

import org.apache.zeppelin.kotlin.repl.KotlinRepl;

/**
 * The implicit receiver for lines in Kotlin REPL.
 *  It is passed to the script as an implicit receiver, identical to:
 *  with (context) {
 *     ...
 *  }
 *
 *  KotlinReceiver can be inherited from and passed to REPL building properties,
 *  so other variables and functions can be accessed inside REPL.
 *  By default, it only has KotlinContext.
 *  Inherited KotlinReceivers should be in separate java file, they can't be inner or nested.
 */
public class KotlinReceiver {
  public KotlinRepl.KotlinContext kc;
}

