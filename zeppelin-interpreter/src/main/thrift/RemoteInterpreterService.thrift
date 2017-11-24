/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

namespace java org.apache.zeppelin.interpreter.thrift

struct RemoteInterpreterContext {
  1: string noteId,
  2: string paragraphId,
  3: string replName,
  4: string paragraphTitle,
  5: string paragraphText,
  6: string authenticationInfo,
  7: string config,   // json serialized config
  8: string gui,      // json serialized gui
  9: string noteGui,      // json serialized note gui
  10: string runners   // json serialized runner
}

struct RemoteInterpreterResultMessage {
  1: string type,
  2: string data
}
struct RemoteInterpreterResult {
  1: string code,
  2: list<RemoteInterpreterResultMessage> msg,
  3: string config,   // json serialized config
  4: string gui       // json serialized gui
  5: string noteGui       // json serialized note gui
}

enum RemoteInterpreterEventType {
  NO_OP = 1,
  ANGULAR_OBJECT_ADD = 2,
  ANGULAR_OBJECT_UPDATE = 3,
  ANGULAR_OBJECT_REMOVE = 4,
  RUN_INTERPRETER_CONTEXT_RUNNER = 5,
  RESOURCE_POOL_GET_ALL = 6,
  RESOURCE_GET = 7
  OUTPUT_APPEND = 8,
  OUTPUT_UPDATE = 9,
  OUTPUT_UPDATE_ALL = 10,
  ANGULAR_REGISTRY_PUSH = 11,
  APP_STATUS_UPDATE = 12,
  META_INFOS = 13,
  REMOTE_ZEPPELIN_SERVER_RESOURCE = 14,
  RESOURCE_INVOKE_METHOD = 15,
  PARA_INFOS = 16
}


struct RemoteInterpreterEvent {
  1: RemoteInterpreterEventType type,
  2: string data      // json serialized data
}

struct RemoteApplicationResult {
  1: bool success,
  2: string msg
}

struct ZeppelinServerResourceParagraphRunner {
  1: string noteId,
  2: string paragraphId
}

/*
 * The below variables(name, value) will be connected to getCompletions in paragraph.controller.js
 *
 * name: which is shown in the suggestion list
 * value: actual return value what you selected
 */
struct InterpreterCompletion {
  1: string name,
  2: string value,
  3: string meta
}

struct CallbackInfo {
  1: string host,
  2: i32 port
}

service RemoteInterpreterService {

  void createInterpreter(1: string intpGroupId, 2: string sessionKey, 3: string className, 4: map<string, string> properties, 5: string userName);
  void open(1: string sessionKey, 2: string className);
  void close(1: string sessionKey, 2: string className);
  RemoteInterpreterResult interpret(1: string sessionKey, 2: string className, 3: string st, 4: RemoteInterpreterContext interpreterContext);
  void cancel(1: string sessionKey, 2: string className, 3: RemoteInterpreterContext interpreterContext);
  i32 getProgress(1: string sessionKey, 2: string className, 3: RemoteInterpreterContext interpreterContext);
  string getFormType(1: string sessionKey, 2: string className);
  list<InterpreterCompletion> completion(1: string sessionKey, 2: string className, 3: string buf, 4: i32 cursor, 5: RemoteInterpreterContext interpreterContext);
  void shutdown();

  string getStatus(1: string sessionKey, 2:string jobId);

  RemoteInterpreterEvent getEvent();

  // as a response, ZeppelinServer send list of resources to Interpreter process
  void resourcePoolResponseGetAll(1: list<string> resources);
  // as a response, ZeppelinServer send serialized value of resource
  void resourceResponseGet(1: string resourceId, 2: binary object);
  // as a response, ZeppelinServer send return object
  void resourceResponseInvokeMethod(1: string invokeMessage, 2: binary object);
  // get all resources in the interpreter process
  list<string> resourcePoolGetAll();
  // get value of resource
  binary resourceGet(1: string sessionKey, 2: string paragraphId, 3: string resourceName);
  // remove resource
  bool resourceRemove(1: string sessionKey, 2: string paragraphId, 3:string resourceName);
  // invoke method on resource
  binary resourceInvokeMethod(1: string sessionKey, 2: string paragraphId, 3:string resourceName, 4:string invokeMessage);

  void angularObjectUpdate(1: string name, 2: string sessionKey, 3: string paragraphId, 4: string
  object);
  void angularObjectAdd(1: string name, 2: string sessionKey, 3: string paragraphId, 4: string object);
  void angularObjectRemove(1: string name, 2: string sessionKey, 3: string paragraphId);
  void angularRegistryPush(1: string registry);

  RemoteApplicationResult loadApplication(1: string applicationInstanceId, 2: string packageInfo, 3: string sessionKey, 4: string paragraphId);
  RemoteApplicationResult unloadApplication(1: string applicationInstanceId);
  RemoteApplicationResult runApplication(1: string applicationInstanceId);

  void onReceivedZeppelinResource(1: string object);
}

service RemoteInterpreterCallbackService {
  void callback(1: CallbackInfo callbackInfo);
}
