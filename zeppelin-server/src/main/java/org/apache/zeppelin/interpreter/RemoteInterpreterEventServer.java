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

package org.apache.zeppelin.interpreter;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.thrift.TException;
import org.apache.thrift.server.ServerContext;
import org.apache.thrift.server.TServerEventHandler;
import org.apache.thrift.server.TThreadPoolServer;
import org.apache.thrift.transport.TSaslServerTransport;
import org.apache.thrift.transport.TServerSocket;
import org.apache.thrift.transport.TTransport;
import org.apache.thrift.transport.TTransportException;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.display.AngularObject;
import org.apache.zeppelin.helium.ApplicationEventListener;
import org.apache.zeppelin.interpreter.remote.AppendOutputRunner;
import org.apache.zeppelin.interpreter.remote.InvokeResourceMethodEventMessage;
import org.apache.zeppelin.interpreter.remote.RemoteAngularObject;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterEventClient;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterProcess;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterProcessListener;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterUtils;
import org.apache.zeppelin.interpreter.thrift.AppOutputAppendEvent;
import org.apache.zeppelin.interpreter.thrift.AppOutputUpdateEvent;
import org.apache.zeppelin.interpreter.thrift.AppStatusUpdateEvent;
import org.apache.zeppelin.interpreter.thrift.InterpreterRPCException;
import org.apache.zeppelin.interpreter.thrift.LibraryMetadata;
import org.apache.zeppelin.interpreter.thrift.ParagraphInfo;
import org.apache.zeppelin.interpreter.thrift.RegisterInfo;
import org.apache.zeppelin.interpreter.thrift.OutputAppendEvent;
import org.apache.zeppelin.interpreter.thrift.OutputUpdateAllEvent;
import org.apache.zeppelin.interpreter.thrift.OutputUpdateEvent;
import org.apache.zeppelin.interpreter.thrift.RemoteInterpreterEventService;
import org.apache.zeppelin.interpreter.thrift.RemoteInterpreterResultMessage;
import org.apache.zeppelin.interpreter.thrift.RunParagraphsEvent;
import org.apache.zeppelin.interpreter.thrift.WebUrlInfo;
import org.apache.zeppelin.resource.RemoteResource;
import org.apache.zeppelin.resource.Resource;
import org.apache.zeppelin.resource.ResourceId;
import org.apache.zeppelin.resource.ResourcePool;
import org.apache.zeppelin.resource.ResourceSet;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.sasl.AuthorizeCallback;
import javax.security.sasl.RealmCallback;
import javax.security.sasl.RealmChoiceCallback;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class RemoteInterpreterEventServer implements RemoteInterpreterEventService.Iface {

  private static final Logger LOGGER = LoggerFactory.getLogger(RemoteInterpreterEventServer.class);
  private static final Gson GSON = new Gson();
  private static final int CALLBACK_TOKEN_BYTES = 32;
  private static final SecureRandom SECURE_RANDOM = new SecureRandom();
  private static final ThreadLocal<String> AUTHENTICATED_INTERPRETER_GROUP = new ThreadLocal<>();
  private static final ThreadLocal<CallbackCredential> AUTHENTICATED_CREDENTIAL =
      new ThreadLocal<>();

  private int port;
  private String host;
  private ZeppelinConfiguration zConf;
  private TThreadPoolServer thriftServer;
  private InterpreterSettingManager interpreterSettingManager;
  private final Map<String, CallbackCredential> callbackCredentials = new ConcurrentHashMap<>();
  private final Map<String, CallbackCredential> callbackCredentialsByAuthenticationId =
      new ConcurrentHashMap<>();

  private final ScheduledExecutorService appendService =
      Executors.newSingleThreadScheduledExecutor();
  private ScheduledFuture<?> appendFuture;
  private AppendOutputRunner runner;
  private final RemoteInterpreterProcessListener listener;
  private final ApplicationEventListener appListener;


  public RemoteInterpreterEventServer(ZeppelinConfiguration zConf,
                                      InterpreterSettingManager interpreterSettingManager) {
    this.zConf = zConf;
    this.interpreterSettingManager = interpreterSettingManager;
    this.listener = interpreterSettingManager.getRemoteInterpreterProcessListener();
    this.appListener = interpreterSettingManager.getAppEventListener();
  }

  public void start() throws IOException {
    Thread startingThread = new Thread() {
      @Override
      public void run() {
        try (TServerSocket tSocket = new TServerSocket(zConf.getZeppelinServerRpcPort().orElse(
            RemoteInterpreterUtils.findAvailablePort(zConf.getZeppelinServerRPCPortRange())))
        ) {
          port = tSocket.getServerSocket().getLocalPort();
          host = RemoteInterpreterUtils.findAvailableHostAddress();
          LOGGER.info("InterpreterEventServer is starting at {}:{}", host, port);
          RemoteInterpreterEventService.Processor<RemoteInterpreterEventServer> processor =
              new RemoteInterpreterEventService.Processor<>(RemoteInterpreterEventServer.this);
          TSaslServerTransport.Factory transportFactory = new TSaslServerTransport.Factory(
              RemoteInterpreterEventClient.SASL_MECHANISM,
              RemoteInterpreterEventClient.SASL_PROTOCOL,
              RemoteInterpreterEventClient.SASL_SERVER_NAME,
              RemoteInterpreterEventClient.SASL_PROPERTIES,
              createSaslCallbackHandler());
          thriftServer = new TThreadPoolServer(
              new TThreadPoolServer.Args(tSocket)
                  .processor(processor)
                  .transportFactory(transportFactory));
          thriftServer.setServerEventHandler(new AuthenticationContextCleaner());
          thriftServer.serve();
        } catch (IOException | TTransportException e ) {
          throw new RuntimeException("Fail to create TServerSocket", e);
        }
        LOGGER.info("ThriftServer-Thread finished");
      }
    };
    startingThread.start();
    long start = System.currentTimeMillis();
    while ((System.currentTimeMillis() - start) < 30 * 1000) {
      if (thriftServer != null && thriftServer.isServing()) {
        break;
      }
      try {
        Thread.sleep(500);
      } catch (InterruptedException e) {
        throw new IOException(e);
      }
    }

    if (thriftServer != null && !thriftServer.isServing()) {
      throw new IOException("Fail to start InterpreterEventServer in 30 seconds.");
    }
    LOGGER.info("RemoteInterpreterEventServer is started");

    runner = new AppendOutputRunner(listener);
    appendFuture = appendService.scheduleWithFixedDelay(
        runner, 0, AppendOutputRunner.BUFFER_TIME_MS, TimeUnit.MILLISECONDS);
  }

  public void stop() {
    if (thriftServer != null) {
      thriftServer.stop();
    }
    if (appendFuture != null) {
      appendFuture.cancel(true);
    }
    appendService.shutdownNow();
    LOGGER.info("RemoteInterpreterEventServer is stopped");
  }


  public int getPort() {
    return port;
  }

  public String getHost() {
    return host;
  }

  public String issueCallbackToken(String interpreterGroupId) {
    if (StringUtils.isBlank(interpreterGroupId)) {
      throw new IllegalArgumentException("Interpreter group id is required");
    }
    byte[] tokenBytes = new byte[CALLBACK_TOKEN_BYTES];
    SECURE_RANDOM.nextBytes(tokenBytes);
    String token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
    installCallbackCredential(interpreterGroupId, token);
    return token;
  }

  public void registerCallbackToken(String interpreterGroupId, String callbackToken) {
    if (StringUtils.isAnyBlank(interpreterGroupId, callbackToken)) {
      throw new IllegalArgumentException("Interpreter group id and callback token are required");
    }
    installCallbackCredential(interpreterGroupId, callbackToken);
  }

  public void registerCallbackToken(String interpreterGroupId,
                                    String callbackToken,
                                    String registeredHost,
                                    int registeredPort) {
    if (StringUtils.isAnyBlank(interpreterGroupId, callbackToken, registeredHost)
        || registeredPort < 1 || registeredPort > 65535) {
      throw new IllegalArgumentException(
          "Interpreter group, callback token, and registered endpoint are required");
    }
    installCallbackCredential(
        interpreterGroupId, callbackToken, registeredHost, registeredPort);
  }

  public String getCallbackToken(String interpreterGroupId) {
    CallbackCredential credential = callbackCredentials.get(interpreterGroupId);
    return credential == null ? null : credential.token;
  }

  public void revokeCallbackToken(String interpreterGroupId, String callbackToken) {
    CallbackCredential credential = callbackCredentials.get(interpreterGroupId);
    if (credential != null && credential.token.equals(callbackToken)) {
      removeCallbackCredential(interpreterGroupId, credential);
    }
  }

  private synchronized CallbackCredential installCallbackCredential(String interpreterGroupId,
                                                                     String callbackToken) {
    return installCallbackCredential(interpreterGroupId, callbackToken, null, -1);
  }

  private synchronized CallbackCredential installCallbackCredential(String interpreterGroupId,
                                                                     String callbackToken,
                                                                     String registeredHost,
                                                                     int registeredPort) {
    CallbackCredential replacement = new CallbackCredential(
        interpreterGroupId, callbackToken, registeredHost, registeredPort);
    CallbackCredential previous = callbackCredentials.put(interpreterGroupId, replacement);
    callbackCredentialsByAuthenticationId.put(replacement.authenticationId, replacement);
    if (previous != null) {
      callbackCredentialsByAuthenticationId.remove(previous.authenticationId, previous);
    }
    return replacement;
  }

  private synchronized void removeCallbackCredential(String interpreterGroupId,
                                                     CallbackCredential credential) {
    if (callbackCredentials.remove(interpreterGroupId, credential)) {
      callbackCredentialsByAuthenticationId.remove(credential.authenticationId, credential);
    }
  }

  private CallbackHandler createSaslCallbackHandler() {
    return callbacks -> {
      String authenticationId = null;
      for (Callback callback : callbacks) {
        if (callback instanceof NameCallback) {
          authenticationId = ((NameCallback) callback).getDefaultName();
          break;
        }
      }

      for (Callback callback : callbacks) {
        if (callback instanceof NameCallback) {
          // The DIGEST-MD5 provider supplies the requested authentication id as the default name.
        } else if (callback instanceof PasswordCallback) {
          CallbackCredential credential =
              callbackCredentialsByAuthenticationId.get(authenticationId);
          if (credential != null) {
            ((PasswordCallback) callback).setPassword(credential.token.toCharArray());
          }
        } else if (callback instanceof AuthorizeCallback) {
          AuthorizeCallback authorizeCallback = (AuthorizeCallback) callback;
          CallbackCredential credential = callbackCredentialsByAuthenticationId.get(
              authorizeCallback.getAuthenticationID());
          boolean authorized = authorizeCallback.getAuthenticationID().equals(
              authorizeCallback.getAuthorizationID())
              && credential != null
              && callbackCredentials.get(credential.interpreterGroupId) == credential;
          authorizeCallback.setAuthorized(authorized);
          if (authorized) {
            authorizeCallback.setAuthorizedID(authorizeCallback.getAuthorizationID());
          }
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

  private void requireAuthenticatedGroup(String interpreterGroupId)
      throws InterpreterRPCException {
    String authenticatedGroup = AUTHENTICATED_INTERPRETER_GROUP.get();
    if (!StringUtils.equals(authenticatedGroup, interpreterGroupId)) {
      throw new InterpreterRPCException(
          "Authenticated interpreter group does not match the requested interpreter group");
    }
  }

  private final class AuthenticationContextCleaner implements TServerEventHandler {
    @Override
    public void preServe() {
    }

    @Override
    public ServerContext createContext(org.apache.thrift.protocol.TProtocol input,
                                       org.apache.thrift.protocol.TProtocol output) {
      TTransport transport = input.getTransport();
      if (!(transport instanceof TSaslServerTransport)
          || ((TSaslServerTransport) transport).getSaslServer() == null
          || !((TSaslServerTransport) transport).getSaslServer().isComplete()) {
        throw new SecurityException(
            "Interpreter callback connection is not authenticated");
      }
      String authenticationId = ((TSaslServerTransport) transport)
          .getSaslServer().getAuthorizationID();
      CallbackCredential credential =
          callbackCredentialsByAuthenticationId.get(authenticationId);
      if (credential == null
          || callbackCredentials.get(credential.interpreterGroupId) != credential) {
        throw new SecurityException("Interpreter callback credential is no longer valid");
      }
      return new AuthenticatedConnectionContext(credential.interpreterGroupId, credential);
    }

    @Override
    public void deleteContext(ServerContext serverContext,
                              org.apache.thrift.protocol.TProtocol input,
                              org.apache.thrift.protocol.TProtocol output) {
      AUTHENTICATED_INTERPRETER_GROUP.remove();
      AUTHENTICATED_CREDENTIAL.remove();
    }

    @Override
    public void processContext(ServerContext serverContext, TTransport input, TTransport output) {
      if (!(serverContext instanceof AuthenticatedConnectionContext)) {
        throw new SecurityException(
            "Interpreter callback connection has no authentication context");
      }
      AuthenticatedConnectionContext context = (AuthenticatedConnectionContext) serverContext;
      if (callbackCredentials.get(context.interpreterGroupId) != context.credential) {
        throw new SecurityException("Interpreter callback credential is no longer valid");
      }
      AUTHENTICATED_INTERPRETER_GROUP.set(context.interpreterGroupId);
      AUTHENTICATED_CREDENTIAL.set(context.credential);
    }
  }

  private static final class CallbackCredential {
    private final String interpreterGroupId;
    private final String token;
    private final String authenticationId;
    private String registeredHost;
    private int registeredPort = -1;

    private CallbackCredential(String interpreterGroupId,
                               String token,
                               String registeredHost,
                               int registeredPort) {
      this.interpreterGroupId = interpreterGroupId;
      this.token = token;
      this.authenticationId = RemoteInterpreterEventClient.callbackAuthenticationId(
          interpreterGroupId, token);
      this.registeredHost = registeredHost;
      this.registeredPort = registeredPort;
    }
  }

  private static final class AuthenticatedConnectionContext implements ServerContext {
    private final String interpreterGroupId;
    private final CallbackCredential credential;

    private AuthenticatedConnectionContext(String interpreterGroupId,
                                           CallbackCredential credential) {
      this.interpreterGroupId = interpreterGroupId;
      this.credential = credential;
    }
  }

  @Override
  public void registerInterpreterProcess(RegisterInfo registerInfo) throws InterpreterRPCException, TException {
    requireAuthenticatedGroup(registerInfo.getInterpreterGroupId());
    if (StringUtils.isBlank(registerInfo.getHost())
        || registerInfo.getPort() < 1 || registerInfo.getPort() > 65535) {
      throw new InterpreterRPCException("Interpreter process endpoint is invalid");
    }
    CallbackCredential authenticatedCredential = AUTHENTICATED_CREDENTIAL.get();
    if (authenticatedCredential == null) {
      throw new InterpreterRPCException("Interpreter callback credential is unavailable");
    }
    InterpreterGroup interpreterGroup =
        interpreterSettingManager.getInterpreterGroupById(registerInfo.getInterpreterGroupId());
    if (interpreterGroup == null) {
      LOGGER.warn("Unable to register interpreter process, because no such interpreterGroup: {}",
              registerInfo.getInterpreterGroupId());
      return;
    }
    RemoteInterpreterProcess interpreterProcess =
        ((ManagedInterpreterGroup) interpreterGroup).getInterpreterProcess();
    if (interpreterProcess == null) {
      LOGGER.warn("Unable to register interpreter process, because no interpreter process associated with " +
              "interpreterGroup: {}", registerInfo.getInterpreterGroupId());
      return;
    }
    synchronized (authenticatedCredential) {
      if (authenticatedCredential.registeredHost != null) {
        if (authenticatedCredential.registeredPort == registerInfo.getPort()
            && authenticatedCredential.registeredHost.equals(registerInfo.getHost())) {
          LOGGER.debug("Interpreter process is already registered at {}:{} for group {}",
              registerInfo.getHost(), registerInfo.getPort(), registerInfo.getInterpreterGroupId());
          return;
        }
        throw new InterpreterRPCException(
            "Interpreter process endpoint is already registered for this launch credential");
      }
      LOGGER.info("Register interpreter process: {}:{}, interpreterGroup: {}",
          registerInfo.getHost(), registerInfo.getPort(), registerInfo.getInterpreterGroupId());
      interpreterProcess.processStarted(registerInfo.port, registerInfo.host);
      authenticatedCredential.registeredHost = registerInfo.getHost();
      authenticatedCredential.registeredPort = registerInfo.getPort();
    }
  }

  @Override
  public void unRegisterInterpreterProcess(String intpGroupId) throws InterpreterRPCException, TException {
    requireAuthenticatedGroup(intpGroupId);
    CallbackCredential authenticatedCredential = AUTHENTICATED_CREDENTIAL.get();
    try {
      LOGGER.info("Unregister interpreter process: {}", intpGroupId);
      InterpreterGroup interpreterGroup =
              interpreterSettingManager.getInterpreterGroupById(intpGroupId);
      if (interpreterGroup == null) {
        LOGGER.warn("Unable to unregister interpreter process because no such interpreterGroup: {}",
                intpGroupId);
        return;
      }
      // Close RemoteInterpreter when RemoteInterpreterServer already timeout.
      // Otherwise the ProgressBar will be missing when rerun after the
      // RemoteInterpreterServer timeout
      // and old RemoteInterpreterGroups will always alive after GC.
      interpreterGroup.close();
      interpreterSettingManager.removeInterpreterGroup(intpGroupId);
    } finally {
      removeCallbackCredential(intpGroupId, authenticatedCredential);
    }
  }

  @Override
  public void sendWebUrl(WebUrlInfo weburlInfo) throws InterpreterRPCException, TException {
    requireAuthenticatedGroup(weburlInfo.getInterpreterGroupId());
    InterpreterGroup interpreterGroup =
            interpreterSettingManager.getInterpreterGroupById(weburlInfo.getInterpreterGroupId());
    if (interpreterGroup == null) {
      LOGGER.warn("Unable to sendWebUrl, because no such interpreterGroup: {}",
              weburlInfo.getInterpreterGroupId());
      return;
    }
    interpreterGroup.setWebUrl(weburlInfo.getWeburl());
  }

  @Override
  public void appendOutput(OutputAppendEvent event) throws InterpreterRPCException, TException {
    if (event.getAppId() == null) {
      runner.appendBuffer(
          event.getNoteId(), event.getParagraphId(), event.getIndex(), event.getData());
    } else {
      appListener.onOutputAppend(event.getNoteId(), event.getParagraphId(), event.getIndex(),
          event.getAppId(), event.getData());
    }
  }

  @Override
  public void updateOutput(OutputUpdateEvent event) throws InterpreterRPCException, TException {
    if (event.getAppId() == null) {
      listener.onOutputUpdated(event.getNoteId(), event.getParagraphId(), event.getIndex(),
          InterpreterResult.Type.valueOf(event.getType()), event.getData());
    } else {
      appListener.onOutputUpdated(event.getNoteId(), event.getParagraphId(), event.getIndex(),
          event.getAppId(), InterpreterResult.Type.valueOf(event.getType()), event.getData());
    }
  }

  @Override
  public void updateAllOutput(OutputUpdateAllEvent event) throws InterpreterRPCException, TException {
    listener.onOutputClear(event.getNoteId(), event.getParagraphId());
    for (int i = 0; i < event.getMsg().size(); i++) {
      RemoteInterpreterResultMessage msg = event.getMsg().get(i);
      listener.onOutputUpdated(event.getNoteId(), event.getParagraphId(), i,
          InterpreterResult.Type.valueOf(msg.getType()), msg.getData());
    }
  }

  @Override
  public void appendAppOutput(AppOutputAppendEvent event) throws InterpreterRPCException, TException {
    appListener.onOutputAppend(event.noteId, event.paragraphId, event.index, event.appId,
        event.data);
  }

  @Override
  public void updateAppOutput(AppOutputUpdateEvent event) throws InterpreterRPCException, TException {
    appListener.onOutputUpdated(event.noteId, event.paragraphId, event.index, event.appId,
        InterpreterResult.Type.valueOf(event.type), event.data);
  }

  @Override
  public void updateAppStatus(AppStatusUpdateEvent event) throws InterpreterRPCException, TException {
    appListener.onStatusChange(event.noteId, event.paragraphId, event.appId, event.status);
  }

  @Override
  public void checkpointOutput(String noteId, String paragraphId) throws InterpreterRPCException, TException {
    listener.checkpointOutput(noteId, paragraphId);
  }

  @Override
  public void runParagraphs(RunParagraphsEvent event) throws InterpreterRPCException, TException {
    try {
      listener.runParagraphs(event.getNoteId(), event.getParagraphIndices(),
          event.getParagraphIds(), event.getCurParagraphId());
      if (InterpreterContext.get() != null) {
        LOGGER.info("complete runParagraphs.{} {}", InterpreterContext.get().getParagraphId(), event);
      } else {
        LOGGER.info("complete runParagraphs.{}", event);
      }
    } catch (IOException e) {
      throw new InterpreterRPCException(e.toString());
    }
  }

  @Override
  public void addAngularObject(String intpGroupId, String json) throws InterpreterRPCException, TException {
    requireAuthenticatedGroup(intpGroupId);
    LOGGER.debug("Add AngularObject, interpreterGroupId: {}, json: {}", intpGroupId, json);
    AngularObject<?> angularObject = AngularObject.fromJson(json);
    InterpreterGroup interpreterGroup =
        interpreterSettingManager.getInterpreterGroupById(intpGroupId);
    if (interpreterGroup == null) {
      LOGGER.warn("Invalid InterpreterGroupId: {}", intpGroupId);
      return;
    }
    interpreterGroup.getAngularObjectRegistry().add(angularObject.getName(),
        angularObject.get(), angularObject.getNoteId(), angularObject.getParagraphId());

    if (angularObject.getNoteId() != null) {
      try {
        interpreterSettingManager.getNotebook().processNote(angularObject.getNoteId(),
          note -> {
            if (note != null) {
              note.addOrUpdateAngularObject(intpGroupId, angularObject);
              interpreterSettingManager.getNotebook().saveNote(note, AuthenticationInfo.ANONYMOUS);
            }
            return null;
          });
      } catch (IOException e) {
        LOGGER.error("Fail to get note: {}", angularObject.getNoteId());
      }
    }
  }

  @Override
  public void updateAngularObject(String intpGroupId, String json) throws InterpreterRPCException, TException {
    requireAuthenticatedGroup(intpGroupId);
    AngularObject<?> angularObject = AngularObject.fromJson(json);
    InterpreterGroup interpreterGroup =
        interpreterSettingManager.getInterpreterGroupById(intpGroupId);
    if (interpreterGroup == null) {
      throw new InterpreterRPCException("Invalid InterpreterGroupId: " + intpGroupId);
    }
    AngularObject localAngularObject = interpreterGroup.getAngularObjectRegistry().get(
        angularObject.getName(), angularObject.getNoteId(), angularObject.getParagraphId());
    if (localAngularObject instanceof RemoteAngularObject) {
      // to avoid ping-pong loop
      ((RemoteAngularObject) localAngularObject).set(
          angularObject.get(), true, false);
    } else {
      localAngularObject.set(angularObject.get());
    }

    if (angularObject.getNoteId() != null) {
      try {
        interpreterSettingManager.getNotebook().processNote(angularObject.getNoteId(),
            note -> {
              if (note != null) {
                note.addOrUpdateAngularObject(intpGroupId, angularObject);
                interpreterSettingManager.getNotebook().saveNote(note, AuthenticationInfo.ANONYMOUS);
              }
              return null;
            });
      } catch (IOException e) {
        LOGGER.error("Fail to get note: {}", angularObject.getNoteId());
      }
    }
  }

  @Override
  public void removeAngularObject(String intpGroupId,
                                  String noteId,
                                  String paragraphId,
                                  String name) throws InterpreterRPCException, TException {
    requireAuthenticatedGroup(intpGroupId);
    InterpreterGroup interpreterGroup =
        interpreterSettingManager.getInterpreterGroupById(intpGroupId);
    if (interpreterGroup == null) {
      throw new InterpreterRPCException("Invalid InterpreterGroupId: " + intpGroupId);
    }
    interpreterGroup.getAngularObjectRegistry().remove(name, noteId, paragraphId);

    if (noteId != null) {
      try {
        interpreterSettingManager.getNotebook().processNote(noteId,
          note -> {
            if (note == null) {
              throw new IOException("Fail to get note: " + noteId);
            }
            note.deleteAngularObject(intpGroupId, noteId, paragraphId, name);
            return null;
          });
      } catch (IOException e) {
        LOGGER.warn("Fail to removeAngularObject of note: {}", noteId, e);
      }
    }
  }

  @Override
  public void sendParagraphInfo(String intpGroupId, String json) throws InterpreterRPCException, TException {
    requireAuthenticatedGroup(intpGroupId);
    InterpreterGroup interpreterGroup =
        interpreterSettingManager.getInterpreterGroupById(intpGroupId);
    if (interpreterGroup == null) {
      throw new InterpreterRPCException("Invalid InterpreterGroupId: " + intpGroupId);
    }

    Map<String, String> paraInfos = GSON.fromJson(json,
        new TypeToken<Map<String, String>>() {
        }.getType());
    String noteId = paraInfos.get("noteId");
    String paraId = paraInfos.get("paraId");
    String settingId = ((ManagedInterpreterGroup) interpreterGroup).getInterpreterSetting().getId();
    if (noteId != null && paraId != null && settingId != null) {
      listener.onParaInfosReceived(noteId, paraId, settingId, paraInfos);
    }
  }

  @Override
  public List<String> getAllResources(String intpGroupId) throws InterpreterRPCException, TException {
    requireAuthenticatedGroup(intpGroupId);
    ResourceSet resourceSet = getAllResourcePoolExcept(intpGroupId);
    List<String> resourceList = new LinkedList<>();
    for (Resource r : resourceSet) {
      resourceList.add(r.toJson());
    }
    return resourceList;
  }

  @Override
  public ByteBuffer getResource(String resourceIdJson) throws InterpreterRPCException, TException {
    ResourceId resourceId = ResourceId.fromJson(resourceIdJson);
    Object o = getResource(resourceId);
    ByteBuffer obj;
    if (o == null) {
      obj = ByteBuffer.allocate(0);
    } else {
      try {
        obj = Resource.serializeObject(o);
      } catch (IOException e) {
        throw new InterpreterRPCException(e.toString());
      }
    }
    return obj;
  }

  /**
   *
   * @param intpGroupId caller interpreter group id
   * @param invokeMethodJson invoke information
   * @return
   * @throws TException
   */
  @Override
  public ByteBuffer invokeMethod(String intpGroupId, String invokeMethodJson)
          throws InterpreterRPCException, TException {
    requireAuthenticatedGroup(intpGroupId);
    InvokeResourceMethodEventMessage invokeMethodMessage =
        InvokeResourceMethodEventMessage.fromJson(invokeMethodJson);
    Object ret = invokeResourceMethod(intpGroupId, invokeMethodMessage);
    ByteBuffer obj = null;
    if (ret == null) {
      obj = ByteBuffer.allocate(0);
    } else {
      try {
        obj = Resource.serializeObject(ret);
      } catch (IOException e) {
        LOGGER.error("invokeMethod failed", e);
      }
    }
    return obj;
  }

  @Override
  public List<ParagraphInfo> getParagraphList(String user, String noteId)
          throws InterpreterRPCException, TException {
    LOGGER.info("get paragraph list from remote interpreter noteId: {}, user = {}",noteId, user);

    if (user != null && noteId != null) {
      List<ParagraphInfo> paragraphInfos = null;
      try {
        paragraphInfos = listener.getParagraphList(user, noteId);
      } catch (IOException e) {
       throw new InterpreterRPCException(e.toString());
      }
      return paragraphInfos;
    } else {
      LOGGER.error("user or noteId is null!");
      return Collections.emptyList();
    }
  }

  private Object invokeResourceMethod(String intpGroupId,
                                      final InvokeResourceMethodEventMessage message) {
    final ResourceId resourceId = message.resourceId;
    ManagedInterpreterGroup intpGroup =
        interpreterSettingManager.getInterpreterGroupById(resourceId.getResourcePoolId());
    if (intpGroup == null) {
      return null;
    }

    RemoteInterpreterProcess remoteInterpreterProcess = intpGroup.getRemoteInterpreterProcess();
    if (remoteInterpreterProcess == null) {
      ResourcePool localPool = intpGroup.getResourcePool();
      if (localPool != null) {
        Resource res = localPool.get(resourceId.getName());
        if (res != null) {
          try {
            return res.invokeMethod(
                message.methodName,
                message.getParamTypes(),
                message.params,
                message.returnResourceName);
          } catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
            return null;
          }
        } else {
          // object is null. can't invoke any method
          LOGGER.error("Can't invoke method {} on null object", message.methodName);
          return null;
        }
      } else {
        LOGGER.error("no resource pool");
        return null;
      }
    } else if (remoteInterpreterProcess.isRunning()) {
      ByteBuffer res = remoteInterpreterProcess.callRemoteFunction(client ->
              client.resourceInvokeMethod(
                  resourceId.getNoteId(),
                  resourceId.getParagraphId(),
                  resourceId.getName(),
                  message.toJson()));

      try {
        return Resource.deserializeObject(res);
      } catch (Exception e) {
        LOGGER.error(e.getMessage(), e);
      }
      return null;
    }
    return null;
  }

  private Object getResource(final ResourceId resourceId) {
    ManagedInterpreterGroup intpGroup = interpreterSettingManager
        .getInterpreterGroupById(resourceId.getResourcePoolId());
    if (intpGroup == null) {
      return null;
    }
    RemoteInterpreterProcess remoteInterpreterProcess = intpGroup.getRemoteInterpreterProcess();
    ByteBuffer buffer = remoteInterpreterProcess.callRemoteFunction(client ->
            client.resourceGet(
                resourceId.getNoteId(),
                resourceId.getParagraphId(),
                resourceId.getName()));

    try {
      return Resource.deserializeObject(buffer);
    } catch (Exception e) {
      LOGGER.error(e.getMessage(), e);
    }
    return null;
  }

  private ResourceSet getAllResourcePoolExcept(String interpreterGroupId) {
    ResourceSet resourceSet = new ResourceSet();
    for (ManagedInterpreterGroup intpGroup : interpreterSettingManager.getAllInterpreterGroup()) {
      if (intpGroup.getId().equals(interpreterGroupId)) {
        continue;
      }

      RemoteInterpreterProcess remoteInterpreterProcess = intpGroup.getRemoteInterpreterProcess();
      if (remoteInterpreterProcess == null) {
        ResourcePool localPool = intpGroup.getResourcePool();
        if (localPool != null) {
          resourceSet.addAll(localPool.getAll());
        }
      } else if (remoteInterpreterProcess.isRunning()) {
        List<String> resourceList = remoteInterpreterProcess.callRemoteFunction(
                client -> client.resourcePoolGetAll());
        for (String res : resourceList) {
          resourceSet.add(RemoteResource.fromJson(res));
        }
      }
    }
    return resourceSet;
  }

  @Override
  public void updateParagraphConfig(String noteId,
                                    String paragraphId,
                                    Map<String, String> config)
          throws InterpreterRPCException, TException {
    try {
      LOGGER.info("Update paragraph config");
      interpreterSettingManager.getNotebook().processNote(noteId,
          note -> {
            note.getParagraph(paragraphId).updateConfig(config);
            interpreterSettingManager.getNotebook().saveNote(note, AuthenticationInfo.ANONYMOUS);
            return null;
          });
    } catch (Exception e) {
      LOGGER.error("Fail to updateParagraphConfig", e);
    }

  }

  @Override
  public List<LibraryMetadata> getAllLibraryMetadatas(String interpreter) throws TException {
    if (StringUtils.isBlank(interpreter)) {
      LOGGER.warn("Interpreter is blank");
      return Collections.emptyList();
    }
    File interpreterLocalRepo = new File(
        zConf.getAbsoluteDir(ZeppelinConfiguration.ConfVars.ZEPPELIN_DEP_LOCALREPO)
            + File.separator
            + interpreter);
    if (!interpreterLocalRepo.exists()) {
      LOGGER.warn("Local interpreter repository {} for interpreter {} doesn't exists", interpreterLocalRepo,
          interpreter);
      return Collections.emptyList();
    }
    if (!interpreterLocalRepo.isDirectory()) {
      LOGGER.warn("Local interpreter repository {} is no folder", interpreterLocalRepo);
      return Collections.emptyList();
    }
    Collection<File> files = FileUtils.listFiles(interpreterLocalRepo, new String[] { "jar" }, false);
    List<LibraryMetadata> metaDatas = new ArrayList<>(files.size());
    for (File file : files) {
      try {
        metaDatas.add(new LibraryMetadata(file.getName(), FileUtils.checksumCRC32(file)));
      } catch (IOException e) {
        LOGGER.warn(e.getMessage(), e);
      }
    }
    return metaDatas;
  }


  @Override
  public ByteBuffer getLibrary(String interpreter, String libraryName) throws TException {
    if (StringUtils.isAnyBlank(interpreter, libraryName)) {
      LOGGER.warn("Interpreter \"{}\" or libraryName \"{}\" is blank", interpreter, libraryName);
      return null;
    }
    File library = new File(zConf.getAbsoluteDir(ZeppelinConfiguration.ConfVars.ZEPPELIN_DEP_LOCALREPO)
        + File.separator + interpreter + File.separator + libraryName);
    if (!library.exists()) {
      LOGGER.warn("Library {} doesn't exists", library);
      return null;
    }

    try {
      return ByteBuffer.wrap(FileUtils.readFileToByteArray(library));
    } catch (IOException e) {
      LOGGER.error("Unable to read library {}", library, e);
    }
    return null;
  }

}
