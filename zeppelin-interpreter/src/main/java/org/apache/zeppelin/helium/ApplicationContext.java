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
package org.apache.zeppelin.helium;

import org.apache.zeppelin.display.AngularObjectRegistry;
import org.apache.zeppelin.interpreter.InterpreterOutput;

/**
 * ApplicationContext
 */
public class ApplicationContext {
  private final String noteId;
  private final String paragraphId;
  private final String applicationInstanceId;
  private final HeliumAppAngularObjectRegistry angularObjectRegistry;
  public final InterpreterOutput out;


  public ApplicationContext(String noteId,
                            String paragraphId,
                            String applicationInstanceId,
                            HeliumAppAngularObjectRegistry angularObjectRegistry,
                            InterpreterOutput out) {
    this.noteId = noteId;
    this.paragraphId = paragraphId;
    this.applicationInstanceId = applicationInstanceId;
    this.angularObjectRegistry = angularObjectRegistry;
    this.out = out;
  }

  public String getNoteId() {
    return noteId;
  }

  public String getParagraphId() {
    return paragraphId;
  }

  public String getApplicationInstanceId() {
    return applicationInstanceId;
  }

  public HeliumAppAngularObjectRegistry getAngularObjectRegistry() {
    return angularObjectRegistry;
  }
}
