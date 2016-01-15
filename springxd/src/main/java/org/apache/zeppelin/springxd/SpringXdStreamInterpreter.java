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
import org.springframework.xd.rest.domain.StreamDefinitionResource;

/**
 * SpringXD interpreter for Zeppelin.
 * 
 * <ul>
 * <li>{@code springxd.url} - JDBC URL to connect to.</li>
 * </ul>
 * 
 * <p>
 * How to use: <br/>
 * {@code %xd.stream} <br/>
 * {@code 
 *  tweets = twittersearch --query=Obama --outputType=application/json | gemfire-json-server 
 *    --useLocator=true --host=ambari.localdomain --port=10334 
 *    --regionName=regionTweet --keyExpression=payload.getField('id_str')
 *  tweetsCount = tap:stream:tweets > json-to-tuple | transform --expression='payload.id_str' 
 *    | counter --name=tweetCounter
 * }
 * </p>
 *
 */
public class SpringXdStreamInterpreter extends AbstractSpringXdInterpreter {

  private Logger logger = LoggerFactory.getLogger(SpringXdStreamInterpreter.class);

  static {
    Interpreter.register(
        "stream",
        "xd",
        SpringXdStreamInterpreter.class.getName(),
        new InterpreterPropertyBuilder().add(SPRINGXD_URL, DEFAULT_SPRINGXD_URL,
            "The URL for SpringXD REST API.").build());
  }

  public SpringXdStreamInterpreter(Properties property) {
    super(property);
    logger.info("Create SpringXdStreamInterpreter");
  }

  @Override
  public AbstractSpringXdResourceCompletion doCreateResourceCompletion() {
    return new AbstractSpringXdResourceCompletion() {
      @Override
      public List<String> doSpringXdCompletion(String completionPreffix) {
        return getXdTemplate().completionOperations().completions(CompletionKind.stream,
            completionPreffix, SINGLE_LEVEL_OF_DETAILS);
      }
    };
  }

  @Override
  public AbstractSpringXdResourceManager doCreateResourceManager() {
    return new AbstractSpringXdResourceManager() {
      
      @Override
      public void doCreateResource(String name, String definition) {
        @SuppressWarnings("unused")
        StreamDefinitionResource stream =
            getXdTemplate().streamOperations().createStream(name, definition, DEPLOY);
      }

      @Override
      public void doDestroyRsource(String name) {
        getXdTemplate().streamOperations().destroy(name);
      }
    };
  }
}
