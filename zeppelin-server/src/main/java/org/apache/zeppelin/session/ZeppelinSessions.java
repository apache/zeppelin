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
package org.apache.zeppelin.session;

import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.dep.DependencyResolver;
import org.apache.zeppelin.helium.Helium;
import org.apache.zeppelin.helium.HeliumApplicationFactory;
import org.apache.zeppelin.interpreter.InterpreterFactory;
import org.apache.zeppelin.notebook.Notebook;
import org.apache.zeppelin.notebook.NotebookAuthorization;
import org.apache.zeppelin.notebook.repo.NotebookRepo;
import org.apache.zeppelin.notebook.repo.NotebookRepoSync;
import org.apache.zeppelin.scheduler.SchedulerFactory;
import org.apache.zeppelin.search.LuceneSearch;
import org.apache.zeppelin.search.SearchService;
import org.apache.zeppelin.server.ZeppelinServer;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.apache.zeppelin.user.Credentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Maintain HTTP user session states.
 */
public class ZeppelinSessions {
  private static final Logger LOGGER = LoggerFactory.getLogger(ZeppelinSessions.class);

  public static final String ZEPPELIN_AUTH_USER_KEY = "_authUser";
  public static final String INTERPRETER_SHARED_FACTORIES_KEY = "_sharedFactories";

  private static final ZeppelinConfiguration conf = ZeppelinServer.conf;
  private static NotebookAuthorization notebookAuthorization = new NotebookAuthorization(conf, null);

  public static Map<String, Component> components = new HashMap();

  public static Notebook notebook(String principal) {
    return component(principal).notebook;
  }

  public static Credentials credentials(String principal) {
    return component(principal).credentials;
  }

  public static NotebookAuthorization notebookAuthorization(String principal) {
    return component(principal).notebookAuthorization;
  }

  public static Helium helium(String principal) {
    return component(principal).helium;
  }

  public static HeliumApplicationFactory heliumApplicationFactory(String principal) {
    return component(principal).heliumApplicationFactory;
  }

  public static InterpreterFactory interpreterFactory(String principal) {
    return component(principal).interpreterFactory;
  }

  private static Component component(String principal) {

    String componentKey = conf.isInterpreterPeruserFactories() ?
            principal :
            INTERPRETER_SHARED_FACTORIES_KEY;

    Component component = components.get(componentKey);

    if (component == null) {

      try {

        AuthenticationInfo authenticationInfo = new AuthenticationInfo(principal);

        component = new Component();

        component.depResolver = new DependencyResolver(
                conf.getString(ZeppelinConfiguration.ConfVars.ZEPPELIN_INTERPRETER_LOCALREPO));

        component.helium = new Helium(conf.getHeliumConfPath(),
            conf.getHeliumDefaultLocalRegistryPath());
        component.heliumApplicationFactory = new HeliumApplicationFactory();
        component.schedulerFactory = new SchedulerFactory();

        component.interpreterFactory = new InterpreterFactory(conf,
                ZeppelinServer.notebookWsServer,
                ZeppelinServer.notebookWsServer, component.heliumApplicationFactory,
                component.depResolver, authenticationInfo);

        component.notebookRepo = new NotebookRepoSync(conf, authenticationInfo);
        component.searchService = new LuceneSearch();
        component.notebookAuthorization = notebookAuthorization;
        component.credentials = new Credentials(
            conf.credentialsPersist(), conf.getCredentialsPath());

        component.notebook = new Notebook(conf,
            component.notebookRepo, component.schedulerFactory,
            component.interpreterFactory, ZeppelinServer.notebookWsServer,
            component.searchService, component.notebookAuthorization, component.credentials);

        // to update notebook from application event from remote process.
        component.heliumApplicationFactory.setNotebook(component.notebook);

        // to update fire websocket event on application event.
        component.heliumApplicationFactory
            .setApplicationEventListener(ZeppelinServer.notebookWsServer);

        component.notebook.addNotebookEventListener(component.heliumApplicationFactory);

        components.put(componentKey, component);

      }
      catch (Exception e) {
        e.printStackTrace();
        throw new RuntimeException(e);
      }

    }

    return component;

  }

  /**
   * Container for Zeppelin server components.
   */
  public static class Component {
    public SchedulerFactory schedulerFactory;
    public InterpreterFactory interpreterFactory;
    public NotebookRepo notebookRepo;
    public SearchService searchService;
    public NotebookAuthorization notebookAuthorization;
    public Credentials credentials;
    public DependencyResolver depResolver;
    public Helium helium;
    public HeliumApplicationFactory heliumApplicationFactory;
    public Notebook notebook;
  }

}
