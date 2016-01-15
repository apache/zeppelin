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

import static org.apache.commons.collections.CollectionUtils.isEmpty;
import static org.apache.commons.lang.StringUtils.isBlank;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.zeppelin.interpreter.remote.RemoteInterpreter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Throwables;

/**
 * Supper calls used by the Stream and Job interpreters to control (and own) the resources (streams
 * or jobs) deployed in SpringXD.
 * 
 * Zeppelin starts one {@link RemoteInterpreter} instance per interpreter type. This single instance
 * manages the resources for all notebooks and paragraphs. The
 * {@link AbstractSpringXdResourceManager} keeps the track of which resource in which notebook and
 * paragraph is created and consecutively allows its safe destruction.
 * 
 * The implementing class should use the SpringXD client API to deploy and/or destroy the XD
 * resources (e.g. streams or jobs).
 * 
 * Note: SpringXD requires an unique Stream and Job names. They must be unique across all Notes and
 * Paragraphs!
 */
public abstract class AbstractSpringXdResourceManager {

  public static final boolean DEPLOY = true;

  private Logger logger = LoggerFactory.getLogger(AbstractSpringXdResourceManager.class);

  private Map<String, Map<String, List<String>>> note2paragraph2Resources;

  public AbstractSpringXdResourceManager() {
    this.note2paragraph2Resources = new HashMap<String, Map<String, List<String>>>();
  }

  /**
   * Creates a new Stream or Job SpringXD resource.
   * 
   * @param name Resource (stream or job) name
   * 
   * @param definition Resource (stream or job) definition
   */
  public abstract void doCreateResource(String name, String definition);

  /**
   * Destroys stream or job resource by name
   * 
   * @param name Stream or Job name to destroy.
   */
  public abstract void doDestroyRsource(String name);

  public void deployResource(String noteId, String paragraphId, String resourceName,
      String resourceDefininition) {

    if (!isBlank(resourceName) && !isBlank(resourceDefininition)) {

      doCreateResource(resourceName, resourceDefininition);

      if (!note2paragraph2Resources.containsKey(noteId)) {
        note2paragraph2Resources.put(noteId, new HashMap<String, List<String>>());
      }

      if (!note2paragraph2Resources.get(noteId).containsKey(paragraphId)) {
        note2paragraph2Resources.get(noteId).put(paragraphId, new ArrayList<String>());
      }
      note2paragraph2Resources.get(noteId).get(paragraphId).add(resourceName);
    }
  }

  public List<String> getDeployedResourceBy(String noteId, String paragraphId) {
    if (note2paragraph2Resources.containsKey(noteId)
        && note2paragraph2Resources.get(noteId).containsKey(paragraphId)) {
      return note2paragraph2Resources.get(noteId).get(paragraphId);
    }
    return new ArrayList<String>();
  }

  public void destroyDeployedResourceBy(String noteId, String paragraphId) {
    if (note2paragraph2Resources.containsKey(noteId)
        && note2paragraph2Resources.get(noteId).containsKey(paragraphId)) {

      Iterator<String> it = note2paragraph2Resources.get(noteId).get(paragraphId).iterator();
      while (it.hasNext()) {
        String resourceName = it.next();
        try {
          doDestroyRsource(resourceName);
          it.remove();
          logger.debug("Destroyed :" + resourceName + " from [" + noteId + ":" + paragraphId + "]");
        } catch (Exception e) {
          logger.error("Failed to destroy resource: " + resourceName, Throwables.getRootCause(e));
        }
      }
    }
  }

  public void destroyDeployedResourceBy(String noteId) {
    if (note2paragraph2Resources.containsKey(noteId)) {
      Iterator<String> paragraphIds = note2paragraph2Resources.get(noteId).keySet().iterator();
      while (paragraphIds.hasNext()) {
        String paragraphId = paragraphIds.next();
        destroyDeployedResourceBy(noteId, paragraphId);
      }
    }
  }

  public void destroyAllNotebookDeployedResources() {
    if (!isEmpty(note2paragraph2Resources.keySet())) {
      Iterator<String> it = note2paragraph2Resources.keySet().iterator();
      while (it.hasNext()) {
        String noteId = it.next();
        destroyDeployedResourceBy(noteId);
      }
    }
  }
}
