/**
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.zeppelin.springxd;

import java.util.List;
import java.util.Properties;

import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterPropertyBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.xd.rest.domain.CompletionKind;


/**
 * SpringXD Job interpreter for Zeppelin.
 * 
 * <ul>
 * <li>{@code springxd.url} - SpringXD URL to connect to.</li>
 * </ul>
 * 
 * <p>
 * How to use: <br/>
 * {@code %xd.job} <br/>
 * {@code 
 * }
 * </p>
 *
 */
public class SpringXdJobInterpreter extends AbstractSpringXdInterpreter {

  private Logger logger = LoggerFactory.getLogger(SpringXdJobInterpreter.class);

  static {
    Interpreter.register(
        "job",
        "xd",
        SpringXdJobInterpreter.class.getName(),
        new InterpreterPropertyBuilder().add(SPRINGXD_URL, DEFAULT_SPRINGXD_URL,
            "The URL for SpringXD REST API.").build());
  }

  public SpringXdJobInterpreter(Properties property) {
    super(property);
    logger.info("Create SpringXdJobInterpreter");
  }

  @Override
  public AbstractSpringXdResourceCompletion doCreateResourceCompletion() {
    return new AbstractSpringXdResourceCompletion() {
      @Override
      public List<String> doSpringXdCompletion(String completionPreffix) {
        return getXdTemplate().completionOperations().completions(CompletionKind.job,
            completionPreffix, SINGLE_LEVEL_OF_DETAILS);
      }
    };
  }

  @Override
  public AbstractSpringXdResourceManager doCreateResourceManager() {
    return new AbstractSpringXdResourceManager() {
      @Override
      public void doCreateResource(String name, String definition) {
        getXdTemplate().jobOperations().createJob(name, definition, DEPLOY);
      }

      @Override
      public void doDestroyRsource(String name) {
        getXdTemplate().jobOperations().destroy(name);
      }
    };
  }
}
