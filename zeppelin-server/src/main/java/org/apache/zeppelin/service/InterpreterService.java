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

package org.apache.zeppelin.service;

import com.google.common.collect.Lists;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.inject.Inject;
import jline.internal.Preconditions;
import org.apache.commons.io.FileUtils;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.dep.DependencyResolver;
import org.apache.zeppelin.interpreter.InterpreterSettingManager;
import org.apache.zeppelin.rest.message.InterpreterInstallationRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonatype.aether.RepositoryException;

/**
 * This class handles all of business logic for {@link org.apache.zeppelin.rest.InterpreterRestApi}
 */
public class InterpreterService {

  private static final String ZEPPELIN_ARTIFACT_PREFIX = "zeppelin-";
  private static final Logger logger = LoggerFactory.getLogger(InterpreterService.class);
  private static final ExecutorService executorService =
      Executors.newSingleThreadExecutor(
          new ThreadFactoryBuilder()
              .setNameFormat(InterpreterService.class.getSimpleName() + "-")
              .build());

  private final ZeppelinConfiguration conf;
  private final InterpreterSettingManager interpreterSettingManager;

  @Inject
  public InterpreterService(
      ZeppelinConfiguration conf, InterpreterSettingManager interpreterSettingManager) {
    this.conf = conf;
    this.interpreterSettingManager = interpreterSettingManager;
  }

  public void installInterpreter(
      final InterpreterInstallationRequest request, final ServiceCallback serviceCallback)
      throws Exception {
    Preconditions.checkNotNull(request);
    String interpreterName = request.getName();
    Preconditions.checkNotNull(interpreterName);
    Preconditions.checkNotNull(request.getArtifact());

    String interpreterBaseDir = conf.getInterpreterDir();
    String localRepoPath = conf.getInterpreterLocalRepoPath();

    final DependencyResolver dependencyResolver = new DependencyResolver(localRepoPath);

    String proxyUrl = conf.getZeppelinProxyUrl();
    if (null != proxyUrl) {
      String proxyUser = conf.getZeppelinProxyUser();
      String proxyPassword = conf.getZeppelinProxyPassword();
      try {
        dependencyResolver.setProxy(new URL(proxyUrl), proxyUser, proxyPassword);
      } catch (MalformedURLException e) {
        // TODO(jl): Not sure if it's good to raise an exception
        throw new Exception("Url is not valid format", e);
      }
    }

    // TODO(jl): Make a rule between an interpreter name and an installation directory
    List<String> possibleInterpreterDirectories = Lists.newArrayList();
    possibleInterpreterDirectories.add(interpreterName);
    if (interpreterName.startsWith(ZEPPELIN_ARTIFACT_PREFIX)) {
      possibleInterpreterDirectories.add(interpreterName.replace(ZEPPELIN_ARTIFACT_PREFIX, ""));
    } else {
      possibleInterpreterDirectories.add(ZEPPELIN_ARTIFACT_PREFIX + interpreterName);
    }

    for (String pn : possibleInterpreterDirectories) {
      Path testInterpreterDir = Paths.get(interpreterBaseDir, pn);
      if (Files.exists(testInterpreterDir)) {
        throw new Exception("Interpreter " + interpreterName + " already exists with " + pn);
      }
    }

    final Path interpreterDir = Paths.get(interpreterBaseDir, interpreterName);

    try {
      Files.createDirectories(interpreterDir);
    } catch (Exception e) {
      throw new Exception("Cannot create " + interpreterDir.toString());
    }

    // It might take time to finish it
    executorService.execute(
        new Runnable() {
          @Override
          public void run() {
            downloadInterpreter(request, dependencyResolver, interpreterDir, serviceCallback);
          }
        });
  }

  void downloadInterpreter(
      InterpreterInstallationRequest request,
      DependencyResolver dependencyResolver,
      Path interpreterDir,
      ServiceCallback<String> serviceCallback) {
    try {
      logger.info("Start to download a dependency: {}", request.getName());
      if (null != serviceCallback) {
        serviceCallback.onStart("Starting to download " + request.getName() + " interpreter", null);
      }

      dependencyResolver.load(request.getArtifact(), interpreterDir.toFile());
      interpreterSettingManager.refreshInterpreterTemplates();
      logger.info(
          "Finish downloading a dependency {} into {}",
          request.getName(),
          interpreterDir.toString());
      if (null != serviceCallback) {
        serviceCallback.onSuccess(request.getName() + " downloaded", null);
      }
    } catch (RepositoryException | IOException e) {
      logger.error("Error while downloading dependencies", e);
      try {
        FileUtils.deleteDirectory(interpreterDir.toFile());
      } catch (IOException e1) {
        logger.error(
            "Error while removing directory. You should handle it manually: {}",
            interpreterDir.toString(),
            e1);
      }
      if (null != serviceCallback) {
        try {
          serviceCallback.onFailure(
              new Exception("Error while downloading " + request.getName() + " as " +
                  e.getMessage()), null);
        } catch (IOException e1) {
          logger.error("ServiceCallback failure", e1);
        }
      }
    }
  }
}
