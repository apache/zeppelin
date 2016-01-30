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

import static java.lang.String.format;
import static org.apache.commons.lang.StringUtils.isBlank;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Properties;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.zeppelin.display.AngularObjectWatcher;
import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResult.Code;
import org.apache.zeppelin.springxd.AngularBinder.ResourceStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.xd.rest.client.impl.SpringXDTemplate;

import com.google.common.base.Joiner;
import com.google.common.base.Throwables;

/**
 * SpringXD {@link Interpreter} supper class extended by both {@link SpringXdStreamInterpreter} and
 * {@link SpringXdJobInterpreter}.
 */
public abstract class AbstractSpringXdInterpreter extends Interpreter {

  private Logger logger = LoggerFactory.getLogger(AbstractSpringXdInterpreter.class);

  public static final String SPRINGXD_URL = "springxd.url";
  public static final String DEFAULT_SPRINGXD_URL = "http://ambari.localdomain:9393";

  private Exception exceptionOnConnect;

  private SpringXDTemplate xdTemplate;

  private AbstractSpringXdResourceManager xdResourcesManager;

  private AbstractSpringXdResourceCompletion xdResourceCompletion;

  public AbstractSpringXdInterpreter(Properties property) {
    super(property);
  }

  /**
   * @return Returns a Resource (Stream or Job) specific completion implementation
   */
  public abstract AbstractSpringXdResourceCompletion doCreateResourceCompletion();

  /**
   * @return Returns a Resource (stream or job) specific deployed resource manager.
   */
  public abstract AbstractSpringXdResourceManager doCreateResourceManager();

  protected SpringXDTemplate doCreateSpringXDTemplate(URI uri) {
    return new SpringXDTemplate(uri);
  }

  @Override
  public void open() {
    // Destroy any previously deployed resources
    close();
    try {
      String springXdUrl = getProperty(SPRINGXD_URL);
      xdTemplate = doCreateSpringXDTemplate(new URI(springXdUrl));
      xdResourcesManager = doCreateResourceManager();
      xdResourceCompletion = doCreateResourceCompletion();
      exceptionOnConnect = null;
    } catch (URISyntaxException e) {
      logger.error("Failed to connect to the SpringXD cluster", e);
      exceptionOnConnect = e;
      close();
    }
  }

  @Override
  public void close() {
    if (xdResourcesManager != null) {
      xdResourcesManager.destroyAllNotebookDeployedResources();
    }
  }

  @Override
  public InterpreterResult interpret(String multiLineResourceDefinitions, InterpreterContext ctx) {

    if (exceptionOnConnect != null) {
      return new InterpreterResult(Code.ERROR, exceptionOnConnect.getMessage());
    }

    // (Re)deploying jobs means that any previous instances (created by the same
    // notebook/paragraph) will be destroyed
    xdResourcesManager.destroyDeployedResourceBy(ctx.getNoteId(), ctx.getParagraphId());

    String errorMessage = "";
    try {
      if (!isBlank(multiLineResourceDefinitions)) {
        for (String line : multiLineResourceDefinitions
            .split(AbstractSpringXdResourceCompletion.LINE_SEPARATOR)) {

          Pair<String, String> namedDefinition = NamedDefinitionParser.getNamedDefinition(line);

          String resourceName = namedDefinition.getLeft();
          String resourceDefinition = namedDefinition.getRight();

          if (!isBlank(resourceName) && !isBlank(resourceDefinition)) {

            xdResourcesManager.deployResource(ctx.getNoteId(), ctx.getParagraphId(), resourceName,
                resourceDefinition);

            logger.info("Deployed: [" + resourceName + "]:[" + resourceDefinition + "]");
          } else {
            logger.info("Skipped Line:" + line);
          }
        }
      }

      String angularDestroyButton = doCreateAngularResponse(ctx);

      logger.info(angularDestroyButton);

      return new InterpreterResult(Code.SUCCESS, angularDestroyButton);

    } catch (Exception e) {
      logger.error("Failed to deploy xd resource!", e);
      errorMessage = Throwables.getRootCause(e).getMessage();
      xdResourcesManager.destroyDeployedResourceBy(ctx.getNoteId(), ctx.getParagraphId());
    }

    return new InterpreterResult(Code.ERROR, "Failed to deploy XD resource: " + errorMessage);
  }

  protected String doCreateAngularResponse(InterpreterContext ctx) {

    // Use the Angualr response to hook a resource destroy button
    String xdResourceStatusId = "rxdResourceStatus_" + ctx.getParagraphId().replace("-", "_");

    AngularBinder.bind(ctx, xdResourceStatusId, ResourceStatus.DEPLOYED.name(), ctx.getNoteId(),
        ctx.getParagraphId(), new DestroyEventWatcher(ctx));

    List<String> deployedResources =
        xdResourcesManager.getDeployedResourceBy(ctx.getNoteId(), ctx.getParagraphId());

    String destroyButton =
        format("%%angular <button ng-click='%s = \"%s\"'> " + " [%s] : {{%s}} </button>",
            xdResourceStatusId, ResourceStatus.DESTROYED.name(),
            Joiner.on(", ").join(deployedResources).toString(), xdResourceStatusId);

    return destroyButton;
  }

  @Override
  public void cancel(InterpreterContext context) {
    if (xdResourcesManager != null) {
      xdResourcesManager.destroyDeployedResourceBy(context.getNoteId(), context.getParagraphId());
    }
  }

  @Override
  public FormType getFormType() {
    return FormType.SIMPLE;
  }

  @Override
  public int getProgress(InterpreterContext context) {
    return 0;
  }

  @Override
  public List<String> completion(String buf, int cursor) {
    return xdResourceCompletion.completion(buf, cursor);
  }

  private class DestroyEventWatcher extends AngularObjectWatcher {

    public DestroyEventWatcher(InterpreterContext context) {
      super(context);
    }

    @Override
    public void watch(Object oldValue, Object newValue, InterpreterContext context) {
      if (ResourceStatus.DESTROYED.name().equals("" + newValue)) {
        xdResourcesManager.destroyDeployedResourceBy(context.getNoteId(), context.getParagraphId());
      }
    }
  }

  protected SpringXDTemplate getXdTemplate() {
    return xdTemplate;
  }
}
