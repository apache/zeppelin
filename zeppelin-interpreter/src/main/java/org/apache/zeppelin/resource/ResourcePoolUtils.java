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

package org.apache.zeppelin.resource;

import com.google.gson.Gson;
import org.apache.zeppelin.interpreter.InterpreterGroup;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterProcess;
import org.apache.zeppelin.interpreter.thrift.RemoteInterpreterService;
import org.slf4j.Logger;

import java.util.List;

/**
 * Utilities for ResourcePool
 */
public class ResourcePoolUtils {
  static Logger logger = org.slf4j.LoggerFactory.getLogger(ResourcePoolUtils.class);

  public static ResourceSet getAllResources() {
    return getAllResourcesExcept(null);
  }

  public static ResourceSet getAllResourcesExcept(String interpreterGroupExcludsion) {
    ResourceSet resourceSet = new ResourceSet();
    for (InterpreterGroup intpGroup : InterpreterGroup.getAll()) {
      if (interpreterGroupExcludsion != null &&
          intpGroup.getId().equals(interpreterGroupExcludsion)) {
        continue;
      }

      RemoteInterpreterProcess remoteInterpreterProcess = intpGroup.getRemoteInterpreterProcess();
      if (remoteInterpreterProcess == null) {
        ResourcePool localPool = intpGroup.getResourcePool();
        if (localPool != null) {
          resourceSet.addAll(localPool.getAll());
        }
      } else if (remoteInterpreterProcess.isRunning()) {
        RemoteInterpreterService.Client client = null;
        boolean broken = false;
        try {
          client = remoteInterpreterProcess.getClient();
          List<String> resourceList = client.resourcePoolGetAll();
          Gson gson = new Gson();
          for (String res : resourceList) {
            resourceSet.add(gson.fromJson(res, Resource.class));
          }
        } catch (Exception e) {
          logger.error(e.getMessage(), e);
          broken = true;
        } finally {
          if (client != null) {
            intpGroup.getRemoteInterpreterProcess().releaseClient(client, broken);
          }
        }
      }
    }
    return resourceSet;
  }

  public static void removeResourcesBelongsToNote(String noteId) {
    removeResourcesBelongsToParagraph(noteId, null);
  }

  public static void removeResourcesBelongsToParagraph(String noteId, String paragraphId) {
    for (InterpreterGroup intpGroup : InterpreterGroup.getAll()) {
      ResourceSet resourceSet = new ResourceSet();
      RemoteInterpreterProcess remoteInterpreterProcess = intpGroup.getRemoteInterpreterProcess();
      if (remoteInterpreterProcess == null) {
        ResourcePool localPool = intpGroup.getResourcePool();
        if (localPool != null) {
          resourceSet.addAll(localPool.getAll());
        }
        if (noteId != null) {
          resourceSet = resourceSet.filterByNoteId(noteId);
        }
        if (paragraphId != null) {
          resourceSet = resourceSet.filterByParagraphId(paragraphId);
        }

        for (Resource r : resourceSet) {
          localPool.remove(
              r.getResourceId().getNoteId(),
              r.getResourceId().getParagraphId(),
              r.getResourceId().getName());
        }
      } else if (remoteInterpreterProcess.isRunning()) {
        RemoteInterpreterService.Client client = null;
        boolean broken = false;
        try {
          client = remoteInterpreterProcess.getClient();
          List<String> resourceList = client.resourcePoolGetAll();
          Gson gson = new Gson();
          for (String res : resourceList) {
            resourceSet.add(gson.fromJson(res, Resource.class));
          }

          if (noteId != null) {
            resourceSet = resourceSet.filterByNoteId(noteId);
          }
          if (paragraphId != null) {
            resourceSet = resourceSet.filterByParagraphId(paragraphId);
          }

          for (Resource r : resourceSet) {
            client.resourceRemove(
                r.getResourceId().getNoteId(),
                r.getResourceId().getParagraphId(),
                r.getResourceId().getName());
          }
        } catch (Exception e) {
          logger.error(e.getMessage(), e);
          broken = true;
        } finally {
          if (client != null) {
            intpGroup.getRemoteInterpreterProcess().releaseClient(client, broken);
          }
        }
      }
    }
  }
}
