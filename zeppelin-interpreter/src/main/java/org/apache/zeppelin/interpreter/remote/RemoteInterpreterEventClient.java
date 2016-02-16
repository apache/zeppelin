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
package org.apache.zeppelin.interpreter.remote;

import com.google.gson.Gson;
import org.apache.zeppelin.display.AngularObject;
import org.apache.zeppelin.interpreter.InterpreterContextRunner;
import org.apache.zeppelin.interpreter.thrift.RemoteInterpreterEvent;
import org.apache.zeppelin.interpreter.thrift.RemoteInterpreterEventType;
import org.apache.zeppelin.resource.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Thread connection ZeppelinServer -> RemoteInterpreterServer does not provide
 * remote method invocation from RemoteInterpreterServer -> ZeppelinServer
 *
 * This class provides event send and get response from RemoteInterpreterServer to
 * ZeppelinServer.
 *
 * RemoteInterpreterEventPoller is counter part in ZeppelinServer
 */
public class RemoteInterpreterEventClient implements ResourcePoolConnector {
  private final Logger logger = LoggerFactory.getLogger(RemoteInterpreterEvent.class);
  private final List<RemoteInterpreterEvent> eventQueue = new LinkedList<RemoteInterpreterEvent>();
  private final List<ResourceSet> getAllResourceResponse = new LinkedList<ResourceSet>();
  private final Map<ResourceId, Object> getResourceResponse = new HashMap<ResourceId, Object>();
  private final Gson gson = new Gson();

  /**
   * Run paragraph
   * @param runner
   */
  public void run(InterpreterContextRunner runner) {
    sendEvent(new RemoteInterpreterEvent(
        RemoteInterpreterEventType.RUN_INTERPRETER_CONTEXT_RUNNER,
        gson.toJson(runner)));
  }

  /**
   * notify new angularObject creation
   * @param object
   */
  public void angularObjectAdd(AngularObject object) {
    sendEvent(new RemoteInterpreterEvent(
        RemoteInterpreterEventType.ANGULAR_OBJECT_ADD, gson.toJson(object)));
  }

  /**
   * notify angularObject update
   */
  public void angularObjectUpdate(AngularObject object) {
    sendEvent(new RemoteInterpreterEvent(
        RemoteInterpreterEventType.ANGULAR_OBJECT_UPDATE, gson.toJson(object)));
  }

  /**
   * notify angularObject removal
   */
  public void angularObjectRemove(String name, String noteId, String paragraphId) {
    Map<String, String> removeObject = new HashMap<String, String>();
    removeObject.put("name", name);
    removeObject.put("noteId", noteId);
    removeObject.put("paragraphId", paragraphId);

    sendEvent(new RemoteInterpreterEvent(
        RemoteInterpreterEventType.ANGULAR_OBJECT_REMOVE, gson.toJson(removeObject)));
  }


  /**
   * Get all resources except for specific resourcePool
   * @return
   */
  @Override
  public ResourceSet getAllResources() {
    // request
    sendEvent(new RemoteInterpreterEvent(RemoteInterpreterEventType.RESOURCE_POOL_GET_ALL, null));

    synchronized (getAllResourceResponse) {
      while (getAllResourceResponse.isEmpty()) {
        try {
          getAllResourceResponse.wait();
        } catch (InterruptedException e) {
          logger.warn(e.getMessage(), e);
        }
      }
      ResourceSet resourceSet = getAllResourceResponse.remove(0);
      return resourceSet;
    }
  }

  @Override
  public Object readResource(ResourceId resourceId) {
    logger.debug("Request Read Resource {} from ZeppelinServer", resourceId.getName());
    synchronized (getResourceResponse) {
      // wait for previous response consumed
      while (getResourceResponse.containsKey(resourceId)) {
        try {
          getResourceResponse.wait();
        } catch (InterruptedException e) {
          logger.warn(e.getMessage(), e);
        }
      }

      // send request
      Gson gson = new Gson();
      sendEvent(new RemoteInterpreterEvent(
          RemoteInterpreterEventType.RESOURCE_GET,
          gson.toJson(resourceId)));

      // wait for response
      while (!getResourceResponse.containsKey(resourceId)) {
        try {
          getResourceResponse.wait();
        } catch (InterruptedException e) {
          logger.warn(e.getMessage(), e);
        }
      }
      Object o = getResourceResponse.remove(resourceId);
      getResourceResponse.notifyAll();
      return o;
    }
  }

  /**
   * Supposed to call from RemoteInterpreterEventPoller
   */
  public void putResponseGetAllResources(List<String> resources) {
    logger.debug("ResourceSet from ZeppelinServer");
    ResourceSet resourceSet = new ResourceSet();

    for (String res : resources) {
      RemoteResource resource = gson.fromJson(res, RemoteResource.class);
      resource.setResourcePoolConnector(this);
      resourceSet.add(resource);
    }

    synchronized (getAllResourceResponse) {
      getAllResourceResponse.add(resourceSet);
      getAllResourceResponse.notify();
    }
  }

  /**
   * Supposed to call from RemoteInterpreterEventPoller
   * @param resourceId json serialized ResourceId
   * @param object java serialized of the object
   */
  public void putResponseGetResource(String resourceId, ByteBuffer object) {
    ResourceId rid = gson.fromJson(resourceId, ResourceId.class);

    logger.debug("Response resource {} from RemoteInterpreter", rid.getName());

    Object o = null;
    try {
      o = Resource.deserializeObject(object);
    } catch (IOException e) {
      logger.error(e.getMessage(), e);
    } catch (ClassNotFoundException e) {
      logger.error(e.getMessage(), e);
    }

    synchronized (getResourceResponse) {
      getResourceResponse.put(rid, o);
      getResourceResponse.notifyAll();
    }
  }


  /**
   * Supposed to call from RemoteInterpreterEventPoller
   * @return next available event
   */
  public RemoteInterpreterEvent pollEvent() {
    synchronized (eventQueue) {
      if (eventQueue.isEmpty()) {
        try {
          eventQueue.wait(1000);
        } catch (InterruptedException e) {
        }
      }

      if (eventQueue.isEmpty()) {
        return new RemoteInterpreterEvent(RemoteInterpreterEventType.NO_OP, "");
      } else {
        RemoteInterpreterEvent event = eventQueue.remove(0);
        logger.debug("Send event {}", event.getType());
        return event;
      }
    }
  }

  public void onInterpreterOutputAppend(String noteId, String paragraphId, String output) {
    Map<String, String> appendOutput = new HashMap<String, String>();
    appendOutput.put("noteId", noteId);
    appendOutput.put("paragraphId", paragraphId);
    appendOutput.put("data", output);

    sendEvent(new RemoteInterpreterEvent(
        RemoteInterpreterEventType.OUTPUT_APPEND,
        gson.toJson(appendOutput)));
  }

  public void onInterpreterOutputUpdate(String noteId, String paragraphId, String output) {
    Map<String, String> appendOutput = new HashMap<String, String>();
    appendOutput.put("noteId", noteId);
    appendOutput.put("paragraphId", paragraphId);
    appendOutput.put("data", output);

    sendEvent(new RemoteInterpreterEvent(
        RemoteInterpreterEventType.OUTPUT_UPDATE,
        gson.toJson(appendOutput)));
  }


  private void sendEvent(RemoteInterpreterEvent event) {
    synchronized (eventQueue) {
      eventQueue.add(event);
      eventQueue.notifyAll();
    }
  }

}
