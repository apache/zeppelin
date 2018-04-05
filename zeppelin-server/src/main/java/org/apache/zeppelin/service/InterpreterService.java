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

import com.google.common.collect.Maps;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import jline.internal.Preconditions;
import org.apache.commons.io.FileUtils;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.dep.DependencyResolver;
import org.apache.zeppelin.interpreter.InterpreterSettingManager;
import org.apache.zeppelin.notebook.socket.Message;
import org.apache.zeppelin.notebook.socket.Message.OP;
import org.apache.zeppelin.rest.message.InterpreterInstallationRequest;
import org.apache.zeppelin.socket.NotebookServer;
import org.apache.zeppelin.utils.ThreadFactoryFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonatype.aether.RepositoryException;

/**
 * This class handles all of business logic for {@link org.apache.zeppelin.rest.InterpreterRestApi}
 */
public class InterpreterService {

  private static final Logger logger = LoggerFactory.getLogger(InterpreterService.class);
  private static final ExecutorService executorService = Executors.newCachedThreadPool(
      ThreadFactoryFactory.getThreadFactory(InterpreterService.class.getSimpleName()));

  private final ZeppelinConfiguration conf;
  private final NotebookServer notebookWsServer;
  private final InterpreterSettingManager interpreterSettingManager;

  public InterpreterService(ZeppelinConfiguration conf, NotebookServer notebookWsServer,
      InterpreterSettingManager interpreterSettingManager) {
    this.conf = conf;
    this.notebookWsServer = notebookWsServer;
    this.interpreterSettingManager = interpreterSettingManager;
  }


  public void installInterpreter(final InterpreterInstallationRequest request) throws Exception {
    Preconditions.checkNotNull(request);
    Preconditions.checkNotNull(request.getName());
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
        //TODO(jl): Not sure if it's good to raise an exception
        throw new Exception("Url is not valid format", e);
      }
    }

    final Path interpreterDir = Paths.get(interpreterBaseDir, request.getName());
    if (Files.exists(interpreterDir)) {
      throw new Exception("Interpreter " + request.getName() + " already exists");
    }

    try {
      Files.createDirectories(interpreterDir);
    } catch (Exception e) {
      throw new Exception("Cannot create " + interpreterDir.toString());
    }

    // It might take time to finish it
    executorService.submit(new Runnable() {
      @Override
      public void run() {
        try {
          logger.info("Start to download a dependency: {}", request.getName());
          dependencyResolver.load(request.getArtifact(), interpreterDir.toFile());
          interpreterSettingManager.refreshInterpreterTemplates();
          logger.info("Finish downloading a dependency: {}", request.getName());
        } catch (RepositoryException | IOException e) {
          logger.error("Error while downloading dependencies", e);
          try {
            FileUtils.deleteDirectory(interpreterDir.toFile());
          } catch (IOException e1) {
            logger.error("Error while removing directory. You should handle it manually: {}",
                interpreterDir.toString(), e1);
          }
          Message m = new Message(OP.INTERPRETER_INSTALL_RESULT);
          Map<String, Object> result = Maps.newHashMap();
          result.put("result", "Failed");
          result.put("message", "Please try it again");
          notebookWsServer.broadcast(m);
        }
      }
    });
  }
}
