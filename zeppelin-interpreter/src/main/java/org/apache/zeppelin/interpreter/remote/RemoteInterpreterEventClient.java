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
import java.net.InetAddress;
import java.net.UnknownHostException;
import org.apache.thrift.TException;
import org.apache.zeppelin.display.AngularObject;
import org.apache.zeppelin.display.AngularObjectRegistryListener;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResultMessage;
import org.apache.zeppelin.interpreter.thrift.AppOutputAppendEvent;
import org.apache.zeppelin.interpreter.thrift.AppOutputUpdateEvent;
import org.apache.zeppelin.interpreter.thrift.AppStatusUpdateEvent;
import org.apache.zeppelin.interpreter.thrift.OutputAppendEvent;
import org.apache.zeppelin.interpreter.thrift.OutputUpdateAllEvent;
import org.apache.zeppelin.interpreter.thrift.OutputUpdateEvent;
import org.apache.zeppelin.interpreter.thrift.ParagraphInfo;
import org.apache.zeppelin.interpreter.thrift.RemoteInterpreterEventService;
import org.apache.zeppelin.interpreter.thrift.RestApiInfo;
import org.apache.zeppelin.interpreter.thrift.RunParagraphsEvent;
import org.apache.zeppelin.interpreter.thrift.ServiceException;
import org.apache.zeppelin.resource.RemoteResource;
import org.apache.zeppelin.resource.Resource;
import org.apache.zeppelin.resource.ResourceId;
import org.apache.zeppelin.resource.ResourcePoolConnector;
import org.apache.zeppelin.resource.ResourceSet;
import org.apache.zeppelin.serving.RestApiServer;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * This class is used to communicate with ZeppelinServer via thrift.
 * All the methods are synchronized because thrift client is not thread safe.
 */
public class RemoteInterpreterEventClient implements ResourcePoolConnector,
    AngularObjectRegistryListener {
  private final Logger LOGGER = LoggerFactory.getLogger(RemoteInterpreterEventClient.class);
  private final Gson gson = new Gson();

  private RemoteInterpreterEventService.Client intpEventServiceClient;
  private String intpGroupId;

  public RemoteInterpreterEventClient(RemoteInterpreterEventService.Client intpEventServiceClient) {
    this.intpEventServiceClient = intpEventServiceClient;
  }

  public void setIntpGroupId(String intpGroupId) {
    this.intpGroupId = intpGroupId;
  }

  /**
   * Get all resources except for specific resourcePool
   *
   * @return
   */
  @Override
  public synchronized ResourceSet getAllResources() {
    try {
      List<String> resources = intpEventServiceClient.getAllResources(intpGroupId);
      ResourceSet resourceSet = new ResourceSet();
      for (String res : resources) {
        RemoteResource resource = RemoteResource.fromJson(res);
        resource.setResourcePoolConnector(this);
        resourceSet.add(resource);
      }
      return resourceSet;
    } catch (TException e) {
      LOGGER.warn("Fail to getAllResources", e);
      return null;
    }
  }

  public synchronized List<ParagraphInfo> getParagraphList(String user, String noteId)
      throws TException, ServiceException {
    List<ParagraphInfo> paragraphList = intpEventServiceClient.getParagraphList(user, noteId);
    return paragraphList;
  }

  @Override
  public synchronized Object readResource(ResourceId resourceId) {
    try {
      ByteBuffer buffer = intpEventServiceClient.getResource(resourceId.toJson());
      Object o = Resource.deserializeObject(buffer);
      return o;
    } catch (TException | IOException | ClassNotFoundException e) {
      LOGGER.warn("Failt to readResource: " + resourceId, e);
      return null;
    }
  }

  /**
   * Invoke method and save result in resourcePool as another resource
   *
   * @param resourceId
   * @param methodName
   * @param paramTypes
   * @param params
   * @return
   */
  @Override
  public synchronized Object invokeMethod(
      ResourceId resourceId,
      String methodName,
      Class[] paramTypes,
      Object[] params) {
    LOGGER.debug("Request Invoke method {} of Resource {}", methodName, resourceId.getName());

    InvokeResourceMethodEventMessage invokeMethod = new InvokeResourceMethodEventMessage(
            resourceId,
            methodName,
            paramTypes,
            params,
            null);
    try {
      ByteBuffer buffer = intpEventServiceClient.invokeMethod(intpGroupId, invokeMethod.toJson());
      Object o = Resource.deserializeObject(buffer);
      return o;
    } catch (TException | IOException | ClassNotFoundException e) {
      LOGGER.error("Failed to invoke method", e);
      return null;
    }
  }

  /**
   * Invoke method and save result in resourcePool as another resource
   *
   * @param resourceId
   * @param methodName
   * @param paramTypes
   * @param params
   * @param returnResourceName
   * @return
   */
  @Override
  public synchronized Resource invokeMethod(
      ResourceId resourceId,
      String methodName,
      Class[] paramTypes,
      Object[] params,
      String returnResourceName) {
    LOGGER.debug("Request Invoke method {} of Resource {}", methodName, resourceId.getName());

    InvokeResourceMethodEventMessage invokeMethod = new InvokeResourceMethodEventMessage(
            resourceId,
            methodName,
            paramTypes,
            params,
            returnResourceName);

    try {
      ByteBuffer serializedResource = intpEventServiceClient.invokeMethod(intpGroupId, invokeMethod.toJson());
      Resource deserializedResource = (Resource) Resource.deserializeObject(serializedResource);
      RemoteResource remoteResource = RemoteResource.fromJson(gson.toJson(deserializedResource));
      remoteResource.setResourcePoolConnector(this);

      return remoteResource;
    } catch (TException | IOException | ClassNotFoundException e) {
      LOGGER.error("Failed to invoke method", e);
      return null;
    }
  }

  public synchronized void onInterpreterOutputAppend(
      String noteId, String paragraphId, int outputIndex, String output) {
    try {
      intpEventServiceClient.appendOutput(
          new OutputAppendEvent(noteId, paragraphId, outputIndex, output, null));
    } catch (TException e) {
      LOGGER.warn("Fail to appendOutput", e);
    }
  }

  public synchronized void onInterpreterOutputUpdate(
      String noteId, String paragraphId, int outputIndex,
      InterpreterResult.Type type, String output) {
    try {
      intpEventServiceClient.updateOutput(
          new OutputUpdateEvent(noteId, paragraphId, outputIndex, type.name(), output, null));
    } catch (TException e) {
      LOGGER.warn("Fail to updateOutput", e);
    }
  }

  public synchronized void onInterpreterOutputUpdateAll(
      String noteId, String paragraphId, List<InterpreterResultMessage> messages) {
    try {
      intpEventServiceClient.updateAllOutput(
          new OutputUpdateAllEvent(noteId, paragraphId, convertToThrift(messages)));
    } catch (TException e) {
      LOGGER.warn("Fail to updateAllOutput", e);
    }
  }

  private List<org.apache.zeppelin.interpreter.thrift.RemoteInterpreterResultMessage>
        convertToThrift(List<InterpreterResultMessage> messages) {
    List<org.apache.zeppelin.interpreter.thrift.RemoteInterpreterResultMessage> thriftMessages =
        new ArrayList<>();
    for (InterpreterResultMessage message : messages) {
      thriftMessages.add(
          new org.apache.zeppelin.interpreter.thrift.RemoteInterpreterResultMessage(
              message.getType().name(), message.getData()));
    }
    return thriftMessages;
  }

  public synchronized void runParagraphs(String noteId,
                                         List<String> paragraphIds,
                                         List<Integer> paragraphIndices,
                                         String curParagraphId) {
    RunParagraphsEvent event =
        new RunParagraphsEvent(noteId, paragraphIds, paragraphIndices, curParagraphId);
    try {
      intpEventServiceClient.runParagraphs(event);
    } catch (TException e) {
      LOGGER.warn("Fail to runParagraphs: " + event, e);
    }
  }

  public synchronized void onAppOutputAppend(
      String noteId, String paragraphId, int index, String appId, String output) {
    AppOutputAppendEvent event =
        new AppOutputAppendEvent(noteId, paragraphId, appId, index, output);
    try {
      intpEventServiceClient.appendAppOutput(event);
    } catch (TException e) {
      LOGGER.warn("Fail to appendAppOutput: " + event, e);
    }
  }


  public synchronized void onAppOutputUpdate(
      String noteId, String paragraphId, int index, String appId,
      InterpreterResult.Type type, String output) {
    AppOutputUpdateEvent event =
        new AppOutputUpdateEvent(noteId, paragraphId, appId, index, type.name(), output);
    try {
      intpEventServiceClient.updateAppOutput(event);
    } catch (TException e) {
      LOGGER.warn("Fail to updateAppOutput: " + event, e);
    }
  }

  public synchronized void onAppStatusUpdate(String noteId, String paragraphId, String appId,
                                             String status) {
    AppStatusUpdateEvent event = new AppStatusUpdateEvent(noteId, paragraphId, appId, status);
    try {
      intpEventServiceClient.updateAppStatus(event);
    } catch (TException e) {
      LOGGER.warn("Fail to updateAppStatus: " + event, e);
    }
  }

  public synchronized void onParaInfosReceived(Map<String, String> infos) {
    try {
      intpEventServiceClient.sendParagraphInfo(intpGroupId, gson.toJson(infos));
    } catch (TException e) {
      LOGGER.warn("Fail to onParaInfosReceived: " + infos, e);
    }
  }

  public synchronized void addRestApi(String noteId, String endpointName) {
    int port = RestApiServer.getPort();
    try {
      String hostname = InetAddress.getLocalHost().getHostName();
      RestApiInfo apiInfo = new RestApiInfo(
              intpGroupId,
              noteId,
              endpointName,
              hostname,
              port
      );
      intpEventServiceClient.addRestApi(apiInfo);
    } catch (TException e) {
      LOGGER.warn("Fail to add rest api endpoint", e);
    } catch (UnknownHostException e) {
      LOGGER.error("Can't get host name", e);
    }
  }

  @Override
  public synchronized void onAdd(String interpreterGroupId, AngularObject object) {
    try {
      intpEventServiceClient.addAngularObject(intpGroupId, object.toJson());
    } catch (TException e) {
      LOGGER.warn("Fail to add AngularObject: " + object, e);
    }
  }

  @Override
  public synchronized void onUpdate(String interpreterGroupId, AngularObject object) {
    try {
      intpEventServiceClient.updateAngularObject(intpGroupId, object.toJson());
    } catch (TException e) {
      LOGGER.warn("Fail to update AngularObject: " + object, e);
    }
  }

  @Override
  public synchronized void onRemove(String interpreterGroupId, String name, String noteId,
                                    String paragraphId) {
    try {
      intpEventServiceClient.removeAngularObject(intpGroupId, noteId, paragraphId, name);
    } catch (TException e) {
      LOGGER.warn("Fail to remove AngularObject", e);
    }
  }
}
