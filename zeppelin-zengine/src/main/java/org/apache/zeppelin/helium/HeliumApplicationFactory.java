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

import com.google.gson.Gson;
import org.apache.thrift.TException;
import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterGroup;
import org.apache.zeppelin.interpreter.remote.RemoteAngularObjectRegistry;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterProcess;
import org.apache.zeppelin.interpreter.thrift.RemoteApplicationResult;
import org.apache.zeppelin.interpreter.thrift.RemoteInterpreterService;
import org.apache.zeppelin.notebook.ApplicationState;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.Notebook;
import org.apache.zeppelin.notebook.Paragraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * HeliumApplicationFactory
 *
 * 1. sync api -> async api
 * 2. unload app when paragraph / note / interpreter remove
 * 3. front-end job
 * 4. example app
 * 5. dev mode
 * 6. app launcher
 */
public class HeliumApplicationFactory implements ApplicationEventListener {
  Logger logger = LoggerFactory.getLogger(HeliumApplicationFactory.class);

  private final Gson gson;
  private Notebook notebook;
  private ApplicationEventListener applicationEventListener;

  public HeliumApplicationFactory() {
    gson = new Gson();
  }


  private static String generateApplicationId(HeliumPackage pkg, Paragraph paragraph) {
    return "app_" + paragraph.getNote().getId() + "-" + paragraph.getId() + pkg.getName();
  }

  private boolean isRemote(InterpreterGroup group) {
    return group.getAngularObjectRegistry() instanceof RemoteAngularObjectRegistry;
  }

  /**
   * Load pkg
   */
  public String load(HeliumPackage pkg, Paragraph paragraph) throws ApplicationException {
    Interpreter intp = paragraph.getRepl(paragraph.getRequiredReplName());
    InterpreterGroup intpGroup = intp.getInterpreterGroup();
    RemoteInterpreterProcess intpProcess = intpGroup.getRemoteInterpreterProcess();
    if (intpProcess == null) {
      throw new ApplicationException("Target interpreter process is not running");
    }

    RemoteInterpreterService.Client client;
    try {
      client = intpProcess.getClient();
    } catch (Exception e) {
      throw new ApplicationException(e);
    }

    String appId = generateApplicationId(pkg, paragraph);
    String pkgInfo = gson.toJson(pkg);

    ApplicationState appState = null;
    synchronized (paragraph.apps) {
      for (ApplicationState as : paragraph.apps) {
        if (as.getName().equals(pkg.getName())) {
          appState = as;
          break;
        }
      }

      if (appState == null) {
        appState = new ApplicationState(appId, pkg.getName());
        paragraph.apps.add(appState);
      }
    }

    synchronized (appState) {
      if (appState.getStatus() == ApplicationState.ApplicationStatus.LOADED) {
        logger.info("Application {} already loaded on paragraph {}",
            pkg.getName(), paragraph.getId());
        return appId;
      }
      appState.setStatus(ApplicationState.ApplicationStatus.LOADING);
      try {
        RemoteApplicationResult ret = client.loadApplication(
            appId,
            pkgInfo,
            paragraph.getNote().getId(),
            paragraph.getId());

        if (ret.isSuccess()) {
          appState.setStatus(ApplicationState.ApplicationStatus.LOADED);
        } else {
          appState.setStatus(ApplicationState.ApplicationStatus.UNLOADED);
          throw new ApplicationException(ret.getMsg());
        }
        return appId;
      } catch (TException e) {
        intpProcess.releaseBrokenClient(client);
        appState.setStatus(ApplicationState.ApplicationStatus.UNLOADED);
        throw new ApplicationException(e);
      } finally {
        intpProcess.releaseClient(client);
      }
    }
  }

  /**
   * Unload pkg
   */
  public void unload(Paragraph paragraph, String appName) throws ApplicationException {

    ApplicationState appsToUnload = null;

    synchronized (paragraph.apps) {
      for (ApplicationState as : paragraph.apps) {
        if (as.getName().equals(appName)) {
          appsToUnload = as;
          break;
        }
      }
    }

    if (appsToUnload == null) {
      logger.warn("Can not find {} to unload from {}", appName, paragraph.getId());
      return;
    }

    synchronized (appsToUnload) {
      if (appsToUnload.getStatus() != ApplicationState.ApplicationStatus.LOADED) {
        throw new ApplicationException(
            "Can't unload application status " + appsToUnload.getStatus());
      }
      appsToUnload.setStatus(ApplicationState.ApplicationStatus.UNLOADING);
      RemoteInterpreterProcess intpProcess =
          paragraph.getCurrentRepl().getInterpreterGroup().getRemoteInterpreterProcess();

      RemoteInterpreterService.Client client;
      try {
        client = intpProcess.getClient();
      } catch (Exception e) {
        throw new ApplicationException(e);
      }

      try {
        RemoteApplicationResult ret = client.unloadApplication(appsToUnload.getId());

        if (ret.isSuccess()) {
          appsToUnload.setStatus(ApplicationState.ApplicationStatus.UNLOADED);
        } else {
          appsToUnload.setStatus(ApplicationState.ApplicationStatus.LOADED);
          throw new ApplicationException(ret.getMsg());
        }
      } catch (TException e) {
        intpProcess.releaseBrokenClient(client);
        appsToUnload.setStatus(ApplicationState.ApplicationStatus.LOADED);
        throw new ApplicationException(e);
      } finally {
        intpProcess.releaseClient(client);
      }
    }
  }

  /**
   * run pkg
   */
  public void run(Paragraph paragraph, String appName) throws ApplicationException {
    ApplicationState app = null;
    synchronized (paragraph.apps) {
      for (ApplicationState as : paragraph.apps) {
        if (as.getName().equals(appName)) {
          app = as;
          break;
        }
      }
    }

    if (app == null) {
      logger.warn("Can not find app {} from {}", appName, paragraph.getId());
      return;
    }

    synchronized (app) {
      if (app.getStatus() != ApplicationState.ApplicationStatus.LOADED) {
        throw new ApplicationException(
            "Can't run application status " + app.getStatus());
      }
      RemoteInterpreterProcess intpProcess =
          paragraph.getCurrentRepl().getInterpreterGroup().getRemoteInterpreterProcess();

      RemoteInterpreterService.Client client;
      try {
        client = intpProcess.getClient();
      } catch (Exception e) {
        throw new ApplicationException(e);
      }

      try {
        RemoteApplicationResult ret = client.runApplication(app.getId());

        if (ret.isSuccess()) {
          // success
        } else {
          throw new ApplicationException(ret.getMsg());
        }
      } catch (TException e) {
        intpProcess.releaseBrokenClient(client);
        throw new ApplicationException(e);
      } finally {
        intpProcess.releaseClient(client);
      }
    }
  }

  public void unloadAllInTheInterpreterProcess(RemoteInterpreterProcess process) {
    // TODO
  }

  public void unloadAllInTheNote(Note note) {
    // TODO
  }

  public void unloadAllInTheParagraph(Paragraph paragraph) {
    // TODO
  }


  @Override
  public void onOutputAppend(String noteId, String paragraphId, String appId, String output) {
    ApplicationState appToUpdate = getAppState(noteId, paragraphId, appId);

    if (appToUpdate != null) {
      appToUpdate.appendOutput(output);
    } else {
      logger.error("Can't find app {}", appId);
    }

    if (applicationEventListener != null) {
      applicationEventListener.onOutputAppend(noteId, paragraphId, appId, output);
    }
  }

  @Override
  public void onOutputUpdated(String noteId, String paragraphId, String appId, String output) {
    ApplicationState appToUpdate = getAppState(noteId, paragraphId, appId);

    if (appToUpdate != null) {
      appToUpdate.setOutput(output);
    } else {
      logger.error("Can't find app {}", appId);
    }

    if (applicationEventListener != null) {
      applicationEventListener.onOutputUpdated(noteId, paragraphId, appId, output);
    }
  }

  private ApplicationState getAppState(String noteId, String paragraphId, String appId) {
    if (notebook == null) {
      return null;
    }

    Note note = notebook.getNote(noteId);
    if (note == null) {
      logger.error("Can't get note {}", noteId);
      return null;
    }
    Paragraph paragraph = note.getParagraph(paragraphId);
    if (paragraph == null) {
      logger.error("Can't get paragraph {}", paragraphId);
      return null;
    }

    ApplicationState appFound = null;
    synchronized (paragraph.apps) {
      for (ApplicationState app : paragraph.apps) {
        if (app.getId().equals(appId)) {
          appFound = app;
          break;
        }
      }
    }

    return appFound;
  }

  public Notebook getNotebook() {
    return notebook;
  }

  public void setNotebook(Notebook notebook) {
    this.notebook = notebook;
  }

  public ApplicationEventListener getApplicationEventListener() {
    return applicationEventListener;
  }

  public void setApplicationEventListener(ApplicationEventListener applicationEventListener) {
    this.applicationEventListener = applicationEventListener;
  }
}
