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
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterProcess;
import org.apache.zeppelin.interpreter.thrift.RemoteApplicationResult;
import org.apache.zeppelin.interpreter.thrift.RemoteInterpreterService;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.Paragraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * HeliumApplicationFactory
 */
public class HeliumApplicationFactory {
  Logger logger = LoggerFactory.getLogger(HeliumApplicationFactory.class);

  private final Gson gson;
  Map<String, RunningApplicationInfo> apps =
      Collections.synchronizedMap(new HashMap<String, RunningApplicationInfo>());


  public HeliumApplicationFactory() {
    gson = new Gson();
  }

  private static class RunningApplicationInfo {
    HeliumPackage pkg;
    Paragraph paragraph;
    RemoteInterpreterProcess process;

    public RunningApplicationInfo(HeliumPackage pkg,
                                  RemoteInterpreterProcess process,
                                  Paragraph paragraph) {
      this.pkg = pkg;
      this.paragraph = paragraph;
      this.process = process;
    }

    public HeliumPackage getPkg() {
      return pkg;
    }

    public Paragraph getParagraph() {
      return paragraph;
    }

    public RemoteInterpreterProcess getProcess() {
      return process;
    }
  }



  private static String generateApplicationId() {
    return "app_" + System.currentTimeMillis() + "_"
        + new Random(System.currentTimeMillis()).nextInt();
  }


  /**
   * Load pkg
   */
  public void load(HeliumPackage pkg, Paragraph paragraph) throws ApplicationException {
    Interpreter intp = paragraph.getRepl(paragraph.getRequiredReplName());
    RemoteInterpreterProcess intpProcess = intp.getInterpreterGroup().getRemoteInterpreterProcess();

    RemoteInterpreterService.Client client;
    try {
      client = intpProcess.getClient();
    } catch (Exception e) {
      throw new ApplicationException(e);
    }

    String appId = generateApplicationId();
    String pkgInfo = gson.toJson(pkg);
    try {
      RemoteApplicationResult ret = client.loadApplication(
          appId,
          pkgInfo,
          paragraph.getNote().getId(),
          paragraph.getId());

      if (ret.isSuccess()) {
        RunningApplicationInfo appInfo = new RunningApplicationInfo(pkg, intpProcess, paragraph);
        apps.put(appId, appInfo);
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


  /**
   * Unload pkg
   */
  public void unload(String appId) throws ApplicationException {
    RunningApplicationInfo appInfo = apps.get(appId);
    if (appInfo == null) {
      return;
    }

    RemoteInterpreterProcess intpProcess = appInfo.getProcess();

    RemoteInterpreterService.Client client;
    try {
      client = intpProcess.getClient();
    } catch (Exception e) {
      throw new ApplicationException(e);
    }

    try {
      RemoteApplicationResult ret = client.unloadApplication(appId);

      if (ret.isSuccess()) {
        apps.remove(appId);
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

  /**
   * run pkg
   */
  public void run(String appId) throws ApplicationException {
    RunningApplicationInfo appInfo = apps.get(appId);
    if (appInfo == null) {
      return;
    }

    RemoteInterpreterProcess intpProcess = appInfo.getProcess();

    RemoteInterpreterService.Client client;
    try {
      client = intpProcess.getClient();
    } catch (Exception e) {
      throw new ApplicationException(e);
    }

    try {
      RemoteApplicationResult ret = client.runApplication(appId);

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

  public void unloadAllInTheInteprreterProcess(RemoteInterpreterProcess process) {
    for (String appId : apps.keySet()) {
      RunningApplicationInfo app = apps.get(appId);
      if (app.getProcess() == process) {
        try {
          unload(appId);
        } catch (ApplicationException e) {
          logger.error(e.getMessage(), e);
        }
      }
    }
  }

  public void unloadAllInTheNote(Note note) {
    for (String appId : apps.keySet()) {
      RunningApplicationInfo app = apps.get(appId);
      if (app.getParagraph().getNote().getId().equals(note.getId())) {
        try {
          unload(appId);
        } catch (ApplicationException e) {
          logger.error(e.getMessage(), e);
        }
      }
    }
  }

  public void unloadAllInTheParagraph(Paragraph paragraph) {
    for (String appId : apps.keySet()) {
      RunningApplicationInfo app = apps.get(appId);
      if (app.getParagraph().getId().equals(paragraph.getId())) {
        try {
          unload(appId);
        } catch (ApplicationException e) {
          logger.error(e.getMessage(), e);
        }
      }
    }
  }
}
