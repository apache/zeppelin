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
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.transport.TSaslClientTransport;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransport;
import org.apache.thrift.transport.TTransportException;
import org.apache.zeppelin.display.AngularObject;
import org.apache.zeppelin.display.AngularObjectRegistryListener;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResultMessage;
import org.apache.zeppelin.interpreter.thrift.AppOutputAppendEvent;
import org.apache.zeppelin.interpreter.thrift.AppOutputUpdateEvent;
import org.apache.zeppelin.interpreter.thrift.AppStatusUpdateEvent;
import org.apache.zeppelin.interpreter.thrift.LibraryMetadata;
import org.apache.zeppelin.interpreter.thrift.OutputAppendEvent;
import org.apache.zeppelin.interpreter.thrift.OutputUpdateAllEvent;
import org.apache.zeppelin.interpreter.thrift.OutputUpdateEvent;
import org.apache.zeppelin.interpreter.thrift.ParagraphInfo;
import org.apache.zeppelin.interpreter.thrift.RegisterInfo;
import org.apache.zeppelin.interpreter.thrift.RemoteInterpreterEventService;
import org.apache.zeppelin.interpreter.thrift.RunParagraphsEvent;
import org.apache.zeppelin.interpreter.thrift.WebUrlInfo;
import org.apache.zeppelin.resource.RemoteResource;
import org.apache.zeppelin.resource.Resource;
import org.apache.zeppelin.resource.ResourceId;
import org.apache.zeppelin.resource.ResourcePoolConnector;
import org.apache.zeppelin.resource.ResourceSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.sasl.RealmCallback;
import javax.security.sasl.RealmChoiceCallback;
import javax.security.sasl.Sasl;
import javax.security.sasl.SaslException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * This class is used to communicate with ZeppelinServer via thrift.
 * All the methods are synchronized because thrift client is not thread safe.
 */
public class RemoteInterpreterEventClient implements ResourcePoolConnector,
    AngularObjectRegistryListener, AutoCloseable {
  private static final Logger LOGGER = LoggerFactory.getLogger(RemoteInterpreterEventClient.class);
  private static final Gson GSON = new Gson();

  public static final String CALLBACK_TOKEN_ENV = "ZEPPELIN_INTERPRETER_EVENT_TOKEN";
  public static final String CALLBACK_TOKEN_PROPERTY = "zeppelin.interpreter.event.token";
  public static final String INTERPRETER_GROUP_PROPERTY = "zeppelin.interpreter.group.id";

  public static final String SASL_MECHANISM = "DIGEST-MD5";
  public static final String SASL_PROTOCOL = "zeppelin";
  public static final String SASL_SERVER_NAME = "interpreter-event";
  public static final Map<String, String> SASL_PROPERTIES = Map.of(
      Sasl.QOP, "auth-int",
      Sasl.SERVER_AUTH, "true");

  private PooledRemoteClient<RemoteInterpreterEventService.Client> remoteClient;
  private String intpGroupId;

  public RemoteInterpreterEventClient(String intpEventHost,
                                      int intpEventPort,
                                      int connectionPoolSize,
                                      String intpGroupId,
                                      String callbackToken) {
    if (intpGroupId == null || intpGroupId.isEmpty()) {
      throw new IllegalArgumentException("Interpreter group id must not be empty");
    }
    if (callbackToken == null || callbackToken.isEmpty()) {
      throw new IllegalArgumentException("Interpreter event callback token must not be empty");
    }
    this.intpGroupId = intpGroupId;
    String authenticationId = callbackAuthenticationId(intpGroupId, callbackToken);
    this.remoteClient = new PooledRemoteClient<>(() -> {
      TSocket socket = new TSocket(intpEventHost, intpEventPort);
      TTransport transport;
      try {
        transport = new TSaslClientTransport(
            SASL_MECHANISM,
            authenticationId,
            SASL_PROTOCOL,
            SASL_SERVER_NAME,
            SASL_PROPERTIES,
            clientCallbackHandler(authenticationId, callbackToken),
            socket);
        transport.open();
      } catch (SaslException | TTransportException e) {
        throw new IOException(e);
      }
      TProtocol protocol = new TBinaryProtocol(transport);
      return new RemoteInterpreterEventService.Client(protocol);
    }, connectionPoolSize);
  }

  public static String callbackAuthenticationId(String intpGroupId, String callbackToken) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      digest.update(intpGroupId.getBytes(StandardCharsets.UTF_8));
      digest.update((byte) 0);
      byte[] authenticationId = digest.digest(callbackToken.getBytes(StandardCharsets.UTF_8));
      return Base64.getUrlEncoder().withoutPadding().encodeToString(authenticationId);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is unavailable", e);
    }
  }

  private static CallbackHandler clientCallbackHandler(String authenticationId,
                                                       String callbackToken) {
    return callbacks -> {
      for (Callback callback : callbacks) {
        if (callback instanceof NameCallback) {
          ((NameCallback) callback).setName(authenticationId);
        } else if (callback instanceof PasswordCallback) {
          ((PasswordCallback) callback).setPassword(callbackToken.toCharArray());
        } else if (callback instanceof RealmCallback) {
          RealmCallback realmCallback = (RealmCallback) callback;
          realmCallback.setText(realmCallback.getDefaultText());
        } else if (callback instanceof RealmChoiceCallback) {
          ((RealmChoiceCallback) callback).setSelectedIndex(0);
        } else {
          throw new UnsupportedCallbackException(callback);
        }
      }
    };
  }

  public <R> R callRemoteFunction(
      PooledRemoteClient.RemoteFunction<R, RemoteInterpreterEventService.Client> func) {
    return remoteClient.callRemoteFunction(func);
  }

  public void registerInterpreterProcess(RegisterInfo registerInfo) {
    callRemoteFunction(client -> {
      client.registerInterpreterProcess(registerInfo);
      return null;
    });
  }

  public void unRegisterInterpreterProcess() {
    callRemoteFunction(client -> {
      client.unRegisterInterpreterProcess(intpGroupId);
      return null;
    });
  }

  public void sendWebUrlInfo(String webUrl) {
    callRemoteFunction(client -> {
      client.sendWebUrl(new WebUrlInfo(intpGroupId, webUrl));
      return null;
    });
  }

  /**
   * Get all resources except for specific resourcePool
   *
   * @return
   */
  @Override
  public ResourceSet getAllResources() {
    try {
      List<String> resources = callRemoteFunction(client -> client.getAllResources(intpGroupId));
      ResourceSet resourceSet = new ResourceSet();
      for (String res : resources) {
        RemoteResource resource = RemoteResource.fromJson(res);
        resource.setResourcePoolConnector(this);
        resourceSet.add(resource);
      }
      return resourceSet;
    } catch (Exception e) {
      LOGGER.warn("Fail to getAllResources", e);
      return null;
    }
  }

  public List<ParagraphInfo> getParagraphList(String user, String noteId) {
    return callRemoteFunction(client -> client.getParagraphList(user, noteId));
  }

  public List<LibraryMetadata> getAllLibraryMetadatas(String interpreter) {
    return callRemoteFunction(client -> client.getAllLibraryMetadatas(interpreter));
  }

  public ByteBuffer getLibrary(String interpreter, String libraryName) {
    return callRemoteFunction(client -> client.getLibrary(interpreter, libraryName));
  }

  @Override
  public Object readResource(ResourceId resourceId) {
    try {
      ByteBuffer buffer = callRemoteFunction(client -> client.getResource(resourceId.toJson()));
      return Resource.deserializeObject(buffer);
    } catch (IOException | ClassNotFoundException e) {
      LOGGER.warn("Fail to readResource: {}", resourceId, e);
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
  public Object invokeMethod(
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
      ByteBuffer buffer = callRemoteFunction(client -> client.invokeMethod(intpGroupId, invokeMethod.toJson()));
      return Resource.deserializeObject(buffer);
    } catch (IOException | ClassNotFoundException e) {
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
  public Resource invokeMethod(
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
      ByteBuffer serializedResource = callRemoteFunction(client -> client.invokeMethod(intpGroupId, invokeMethod.toJson()));
      Resource deserializedResource = (Resource) Resource.deserializeObject(serializedResource);
      RemoteResource remoteResource = RemoteResource.fromJson(GSON.toJson(deserializedResource));
      remoteResource.setResourcePoolConnector(this);

      return remoteResource;
    } catch (IOException | ClassNotFoundException e) {
      LOGGER.error("Failed to invoke method", e);
      return null;
    }
  }

  public void onInterpreterOutputAppend(
      String noteId, String paragraphId, int outputIndex, String output) {
    try {
      callRemoteFunction(client -> {
        client.appendOutput(
                new OutputAppendEvent(noteId, paragraphId, outputIndex, output, null));
        return null;
      });
    } catch (Exception e) {
      LOGGER.warn("Fail to appendOutput", e);
    }
  }

  public void onInterpreterOutputUpdate(
      String noteId, String paragraphId, int outputIndex,
      InterpreterResult.Type type, String output) {
    try {
      callRemoteFunction(client -> {
        client.updateOutput(
                new OutputUpdateEvent(noteId, paragraphId, outputIndex, type.name(), output, null));
        return null;
      });

    } catch (Exception e) {
      LOGGER.warn("Fail to updateOutput", e);
    }
  }

  public void onInterpreterOutputUpdateAll(
      String noteId, String paragraphId, List<InterpreterResultMessage> messages) {
    try {
      callRemoteFunction(client -> {
        client.updateAllOutput(
                new OutputUpdateAllEvent(noteId, paragraphId, convertToThrift(messages)));
        return null;
      });

    } catch (Exception e) {
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

  public void runParagraphs(String noteId,
                                         List<String> paragraphIds,
                                         List<Integer> paragraphIndices,
                                         String curParagraphId) {
    RunParagraphsEvent event =
        new RunParagraphsEvent(noteId, paragraphIds, paragraphIndices, curParagraphId);
    try {
      callRemoteFunction(client -> {
        client.runParagraphs(event);
        return null;
      });
    } catch (Exception e) {
      LOGGER.warn("Fail to runParagraphs: {}", event, e);
    }
  }

  public void checkpointOutput(String noteId, String paragraphId) {
    try {
      callRemoteFunction(client -> {
        client.checkpointOutput(noteId, paragraphId);
        return null;
      });
    } catch (Exception e) {
      LOGGER.warn("Fail to checkpointOutput of paragraph: {} of note: {}",
              paragraphId, noteId, e);
    }
  }

  public void onAppOutputAppend(
      String noteId, String paragraphId, int index, String appId, String output) {
    AppOutputAppendEvent event =
        new AppOutputAppendEvent(noteId, paragraphId, appId, index, output);
    try {
      callRemoteFunction(client -> {
        client.appendAppOutput(event);
        return null;
      });
    } catch (Exception e) {
      LOGGER.warn("Fail to appendAppOutput: {}", event, e);
    }
  }


  public void onAppOutputUpdate(
      String noteId, String paragraphId, int index, String appId,
      InterpreterResult.Type type, String output) {
    AppOutputUpdateEvent event =
        new AppOutputUpdateEvent(noteId, paragraphId, appId, index, type.name(), output);
    try {
      callRemoteFunction(client -> {
        client.updateAppOutput(event);
        return null;
      });
    } catch (Exception e) {
      LOGGER.warn("Fail to updateAppOutput: {}", event, e);
    }
  }

  public void onAppStatusUpdate(String noteId, String paragraphId, String appId,
                                             String status) {
    AppStatusUpdateEvent event = new AppStatusUpdateEvent(noteId, paragraphId, appId, status);
    try {
      callRemoteFunction(client -> {
        client.updateAppStatus(event);
        return null;
      });
    } catch (Exception e) {
      LOGGER.warn("Fail to updateAppStatus: {}", event, e);
    }
  }

  public void onParaInfosReceived(Map<String, String> infos) {
    try {
      callRemoteFunction(client -> {
        client.sendParagraphInfo(intpGroupId, GSON.toJson(infos));
        return null;
      });
    } catch (Exception e) {
      LOGGER.warn("Fail to onParaInfosReceived: {}", infos, e);
    }
  }

  @Override
  public synchronized void onAddAngularObject(String interpreterGroupId, AngularObject angularObject) {
    try {
      callRemoteFunction(client -> {
        client.addAngularObject(intpGroupId, angularObject.toJson());
        return null;
      });
    } catch (Exception e) {
      LOGGER.warn("Fail to add AngularObject: {}", angularObject, e);
    }
  }

  @Override
  public void onUpdateAngularObject(String interpreterGroupId, AngularObject angularObject) {
    try {
      callRemoteFunction(client -> {
        client.updateAngularObject(intpGroupId, angularObject.toJson());
        return null;
      });
    } catch (Exception e) {
      LOGGER.warn("Fail to update AngularObject: {}", angularObject, e);
    }
  }

  @Override
  public void onRemoveAngularObject(String interpreterGroupId, AngularObject angularObject) {
    try {
      callRemoteFunction(client -> {
        client.removeAngularObject(intpGroupId,
                angularObject.getNoteId(),
                angularObject.getParagraphId(),
                angularObject.getName());
        return null;
      });
    } catch (Exception e) {
      LOGGER.warn("Fail to remove AngularObject", e);
    }
  }

  public void updateParagraphConfig(String noteId, String paragraphId, Map<String, String> config) {
    try {
      callRemoteFunction(client -> {
        client.updateParagraphConfig(noteId, paragraphId, config);
        return null;
      });
    } catch (Exception e) {
      LOGGER.warn("Fail to updateParagraphConfig", e);
    }
  }

  @Override
  public void close() {
    remoteClient.close();
  }
}
