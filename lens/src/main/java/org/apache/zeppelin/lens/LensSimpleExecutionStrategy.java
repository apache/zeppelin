/*
 * Copyright 2011-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.zeppelin.lens;

import  org.springframework.shell.core.*;

import java.util.logging.Logger;

import org.springframework.shell.event.ParseResult;
import org.springframework.shell.support.logging.HandlerUtils;
import org.springframework.util.Assert;
import org.springframework.util.ReflectionUtils;

/**
 * workaround for https://github.com/spring-projects/spring-shell/issues/73
 */
public class LensSimpleExecutionStrategy implements ExecutionStrategy {

  private static final Logger logger = HandlerUtils.getLogger(LensSimpleExecutionStrategy.class);

  public Object execute(ParseResult parseResult) throws RuntimeException {
    Assert.notNull(parseResult, "Parse result required");
    logger.info("LensSimpleExecutionStrategy execute method invoked");
    synchronized (this) {
      Assert.isTrue(isReadyForCommands(), "SimpleExecutionStrategy not yet ready for commands");
      Object target = parseResult.getInstance();
      if (target instanceof ExecutionProcessor) {
        ExecutionProcessor processor = ((ExecutionProcessor) target);
        parseResult = processor.beforeInvocation(parseResult);
        try {
          Object result = invoke(parseResult);
          processor.afterReturningInvocation(parseResult, result);
          return result;
        } catch (Throwable th) {
          processor.afterThrowingInvocation(parseResult, th);
          return handleThrowable(th);
        }
      }
      else {
        return invoke(parseResult);
      }
    }
  }

  private Object invoke(ParseResult parseResult) {
    try {
      return ReflectionUtils.invokeMethod(parseResult.getMethod(),
        parseResult.getInstance(), parseResult.getArguments());
    } catch (Throwable th) {
      logger.severe("Command failed " + th);
      return handleThrowable(th);
    }
  }

  private Object handleThrowable(Throwable th) {
    if (th instanceof Error) {
      throw ((Error) th);
    }
    if (th instanceof RuntimeException) {
      throw ((RuntimeException) th);
    }
    throw new RuntimeException(th);
  }

  public boolean isReadyForCommands() {
    return true;
  }

  public void terminate() {
    // do nothing
  }

}
