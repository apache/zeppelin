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
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResultMessage;
import org.apache.zeppelin.interpreter.RemoteZeppelinServerResource;
import org.apache.zeppelin.interpreter.thrift.RemoteInterpreterEvent;
import org.apache.zeppelin.interpreter.thrift.RemoteInterpreterEventType;
import org.apache.zeppelin.interpreter.thrift.ZeppelinServerResourceParagraphRunner;
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
  private final Logger logger = LoggerFactory.getLogger(RemoteInterpreterEventClient.class);
  private final List<RemoteInterpreterEvent> eventQueue = new LinkedList<>();
  private final List<ResourceSet> getAllResourceResponse = new LinkedList<>();
  private final Map<ResourceId, Object> getResourceResponse = new HashMap<>();
  private final Map<InvokeResourceMethodEventMessage, Object> getInvokeResponse = new HashMap<>();
  private final Gson gson = new Gson();

  /**
   * Run paragraph
   * @param runner
   */
  public void getZeppelinServerNoteRunner(
      String eventOwnerKey, ZeppelinServerResourceParagraphRunner runner) {
    RemoteZeppelinServerResource eventBody = new RemoteZeppelinServerResource();
    eventBody.setResourceType(RemoteZeppelinServerResource.Type.PARAGRAPH_RUNNERS);
    eventBody.setOwnerKey(eventOwnerKey);
    eventBody.setData(runner);

    sendEvent(new RemoteInterpreterEvent(
        RemoteInterpreterEventType.REMOTE_ZEPPELIN_SERVER_RESOURCE,
        gson.toJson(eventBody)));
  }

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
        RemoteInterpreterEventType.ANGULAR_OBJECT_ADD, object.toJson()));
  }

  /**
   * notify angularObject update
   */
  public void angularObjectUpdate(AngularObject object) {
    sendEvent(new RemoteInterpreterEvent(
        RemoteInterpreterEventType.ANGULAR_OBJECT_UPDATE, object.toJson()));
  }

  /**
   * notify angularObject removal
   */
  public void angularObjectRemove(String name, String noteId, String paragraphId) {
    Map<String, String> removeObject = new HashMap<>();
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
      sendEvent(new RemoteInterpreterEvent(
          RemoteInterpreterEventType.RESOURCE_GET,
          resourceId.toJson()));

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
   * Invoke method and save result in resourcePool as another resource
   * @param resourceId
   * @param methodName
   * @param paramTypes
   * @param params
   * @return
   */
  @Override
  public Object invokeMethod(
      ResourceId resourceId,
      String methodName,
      Class[] paramTypes,
      Object[] params) {
    logger.debug("Request Invoke method {} of Resource {}", methodName, resourceId.getName());

    InvokeResourceMethodEventMessage invokeMethod = new InvokeResourceMethodEventMessage(
        resourceId,
        methodName,
        paramTypes,
        params,
        null);

    synchronized (getInvokeResponse) {
      // wait for previous response consumed
      while (getInvokeResponse.containsKey(invokeMethod)) {
        try {
          getInvokeResponse.wait();
        } catch (InterruptedException e) {
          logger.warn(e.getMessage(), e);
        }
      }
      // send request
      sendEvent(new RemoteInterpreterEvent(
          RemoteInterpreterEventType.RESOURCE_INVOKE_METHOD,
          invokeMethod.toJson()));
      // wait for response
      while (!getInvokeResponse.containsKey(invokeMethod)) {
        try {
          getInvokeResponse.wait();
        } catch (InterruptedException e) {
          logger.warn(e.getMessage(), e);
        }
      }
      Object o = getInvokeResponse.remove(invokeMethod);
      getInvokeResponse.notifyAll();
      return o;
    }
  }

  /**
   * Invoke method and save result in resourcePool as another resource
   * @param resourceId
   * @param methodName
   * @param paramTypes
   * @param params
   * @param returnResourceName
   * @return
   */
  @Override
  public Resource invokeMethod(
      ResourceId resourceId,
      String methodName,
      Class[] paramTypes,
      Object[] params,
      String returnResourceName) {
    logger.debug("Request Invoke method {} of Resource {}", methodName, resourceId.getName());

    InvokeResourceMethodEventMessage invokeMethod = new InvokeResourceMethodEventMessage(
        resourceId,
        methodName,
        paramTypes,
        params,
        returnResourceName);

    synchronized (getInvokeResponse) {
      // wait for previous response consumed
      while (getInvokeResponse.containsKey(invokeMethod)) {
        try {
          getInvokeResponse.wait();
        } catch (InterruptedException e) {
          logger.warn(e.getMessage(), e);
        }
      }
      // send request
      sendEvent(new RemoteInterpreterEvent(
          RemoteInterpreterEventType.RESOURCE_INVOKE_METHOD,
          invokeMethod.toJson()));
      // wait for response
      while (!getInvokeResponse.containsKey(invokeMethod)) {
        try {
          getInvokeResponse.wait();
        } catch (InterruptedException e) {
          logger.warn(e.getMessage(), e);
        }
      }
      Resource o = (Resource) getInvokeResponse.remove(invokeMethod);
      getInvokeResponse.notifyAll();
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
      RemoteResource resource = RemoteResource.fromJson(res);
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
    ResourceId rid = ResourceId.fromJson(resourceId);

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
   * @param invokeMessage json serialized InvokeMessage
   * @param object java serialized of the object
   */
  public void putResponseInvokeMethod(
      InvokeResourceMethodEventMessage invokeMessage, ByteBuffer object) {
    Object o = null;
    try {
      o = Resource.deserializeObject(object);
    } catch (IOException e) {
      logger.error(e.getMessage(), e);
    } catch (ClassNotFoundException e) {
      logger.error(e.getMessage(), e);
    }

    synchronized (getInvokeResponse) {
      getInvokeResponse.put(invokeMessage, o);
      getInvokeResponse.notifyAll();
    }
  }

  /**
   * Supposed to call from RemoteInterpreterEventPoller
   * @param invokeMessage invoke message
   * @param resource remote resource
   */
  public void putResponseInvokeMethod(
      InvokeResourceMethodEventMessage invokeMessage, Resource resource) {
    synchronized (getInvokeResponse) {
      getInvokeResponse.put(invokeMessage, resource);
      getInvokeResponse.notifyAll();
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

  public void onInterpreterOutputAppend(
      String noteId, String paragraphId, int outputIndex, String output) {
    Map<String, String> appendOutput = new HashMap<>();
    appendOutput.put("noteId", noteId);
    appendOutput.put("paragraphId", paragraphId);
    appendOutput.put("index", Integer.toString(outputIndex));
    appendOutput.put("data", output);

    sendEvent(new RemoteInterpreterEvent(
        RemoteInterpreterEventType.OUTPUT_APPEND,
        gson.toJson(appendOutput)));
  }

  public void onInterpreterOutputUpdate(
      String noteId, String paragraphId, int outputIndex,
      InterpreterResult.Type type, String output) {
    Map<String, String> appendOutput = new HashMap<>();
    appendOutput.put("noteId", noteId);
    appendOutput.put("paragraphId", paragraphId);
    appendOutput.put("index", Integer.toString(outputIndex));
    appendOutput.put("type", type.name());
    appendOutput.put("data", output);

    sendEvent(new RemoteInterpreterEvent(
        RemoteInterpreterEventType.OUTPUT_UPDATE,
        gson.toJson(appendOutput)));
  }

  public void onInterpreterOutputUpdateAll(
      String noteId, String paragraphId, List<InterpreterResultMessage> messages) {
    Map<String, Object> appendOutput = new HashMap<>();
    appendOutput.put("noteId", noteId);
    appendOutput.put("paragraphId", paragraphId);
    appendOutput.put("messages", messages);

    sendEvent(new RemoteInterpreterEvent(
        RemoteInterpreterEventType.OUTPUT_UPDATE_ALL,
        gson.toJson(appendOutput)));
  }

  private void sendEvent(RemoteInterpreterEvent event) {
    logger.debug("Send Event: " + event);
    synchronized (eventQueue) {
      eventQueue.add(event);
      eventQueue.notifyAll();
    }
  }

  public void onAppOutputAppend(
      String noteId, String paragraphId, int index, String appId, String output) {
    Map<String, Object> appendOutput = new HashMap<>();
    appendOutput.put("noteId", noteId);
    appendOutput.put("paragraphId", paragraphId);
    appendOutput.put("index", Integer.toString(index));
    appendOutput.put("appId", appId);
    appendOutput.put("data", output);

    sendEvent(new RemoteInterpreterEvent(
        RemoteInterpreterEventType.OUTPUT_APPEND,
        gson.toJson(appendOutput)));
  }


  public void onAppOutputUpdate(
      String noteId, String paragraphId, int index, String appId,
      InterpreterResult.Type type, String output) {
    Map<String, Object> appendOutput = new HashMap<>();
    appendOutput.put("noteId", noteId);
    appendOutput.put("paragraphId", paragraphId);
    appendOutput.put("index", Integer.toString(index));
    appendOutput.put("appId", appId);
    appendOutput.put("type", type);
    appendOutput.put("data", output);
    logger.debug("onAppoutputUpdate = {}", output);
    sendEvent(new RemoteInterpreterEvent(
        RemoteInterpreterEventType.OUTPUT_UPDATE,
        gson.toJson(appendOutput)));
  }

  public void onAppStatusUpdate(String noteId, String paragraphId, String appId, String status) {
    Map<String, String> appendOutput = new HashMap<>();
    appendOutput.put("noteId", noteId);
    appendOutput.put("paragraphId", paragraphId);
    appendOutput.put("appId", appId);
    appendOutput.put("status", status);

    sendEvent(new RemoteInterpreterEvent(
        RemoteInterpreterEventType.APP_STATUS_UPDATE,
        gson.toJson(appendOutput)));
  }

  public void onMetaInfosReceived(Map<String, String> infos) {
    sendEvent(new RemoteInterpreterEvent(RemoteInterpreterEventType.META_INFOS,
        gson.toJson(infos)));
  }

  public void onParaInfosReceived(Map<String, String> infos) {
    sendEvent(new RemoteInterpreterEvent(RemoteInterpreterEventType.PARA_INFOS,
        gson.toJson(infos)));
  }
  /**
   * Wait for eventQueue becomes empty
   */
  public void waitForEventQueueBecomesEmpty(long atMost) {
    long startTime = System.currentTimeMillis();
    synchronized (eventQueue) {
      while (!eventQueue.isEmpty() && (System.currentTimeMillis() - startTime) < atMost) {
        try {
          eventQueue.wait(100);
        } catch (InterruptedException e) {
          // ignore exception
        }
      }
      if (!eventQueue.isEmpty())
        eventQueue.clear();
    }
  }
}
