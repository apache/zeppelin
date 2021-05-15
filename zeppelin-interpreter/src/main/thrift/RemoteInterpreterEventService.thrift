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

include "RemoteInterpreterService.thrift"

namespace java org.apache.zeppelin.interpreter.thrift

struct RegisterInfo {
  1: string host,
  2: i32 port
  3: string interpreterGroupId
}

struct WebUrlInfo {
  1: string interpreterGroupId,
  2: string weburl
}

struct OutputAppendEvent {
  1: string noteId,
  2: string paragraphId,
  3: i32 index,
  4: string data,
  5: string appId
}

struct OutputUpdateEvent {
  1: string noteId,
  2: string paragraphId,
  3: i32 index,
  4: string type,
  5: string data,
  6: string appId
}

struct OutputUpdateAllEvent {
  1: string noteId,
  2: string paragraphId,
  3: list<RemoteInterpreterService.RemoteInterpreterResultMessage> msg,
}

struct RunParagraphsEvent {
  1: string noteId,
  2: list<string> paragraphIds,
  3: list<i32> paragraphIndices,
  4: string curParagraphId
}

struct AngularObjectId {
  1: string noteId,
  2: string paragraphId,
  3: string name
}

struct AppOutputAppendEvent {
  1: string noteId,
  2: string paragraphId,
  3: string appId,
  4: i32 index,
  5: string data
}

struct AppOutputUpdateEvent {
  1: string noteId,
  2: string paragraphId,
  3: string appId,
  4: i32 index,
  5: string type,
  6: string data
}

struct AppStatusUpdateEvent {
  1: string noteId,
  2: string paragraphId,
  3: string appId,
  4: string status
}

struct ParagraphInfo {
  1: string noteId,
  2: string paragraphId,
  3: string paragraphTitle,
  4: string paragraphText
}

// The metadata of a file
struct LibraryMetadata {
   1: required string name;
   2: required i64 checksum;
}

exception ServiceException{
  1: required string message;
}

service RemoteInterpreterEventService {
  void registerInterpreterProcess(1: RegisterInfo registerInfo) throws (1: RemoteInterpreterService.InterpreterRPCException ex);
  void unRegisterInterpreterProcess(1: string intpGroupId) throws (1: RemoteInterpreterService.InterpreterRPCException ex);

  void appendOutput(1: OutputAppendEvent event) throws (1: RemoteInterpreterService.InterpreterRPCException ex);
  void updateOutput(1: OutputUpdateEvent event) throws (1: RemoteInterpreterService.InterpreterRPCException ex);
  void updateAllOutput(1: OutputUpdateAllEvent event) throws (1: RemoteInterpreterService.InterpreterRPCException ex);

  void appendAppOutput(1: AppOutputAppendEvent event) throws (1: RemoteInterpreterService.InterpreterRPCException ex);
  void updateAppOutput(1: AppOutputUpdateEvent event) throws (1: RemoteInterpreterService.InterpreterRPCException ex);
  void updateAppStatus(1: AppStatusUpdateEvent event) throws (1: RemoteInterpreterService.InterpreterRPCException ex);

  void checkpointOutput(1: string noteId, 2: string paragraphId) throws (1: RemoteInterpreterService.InterpreterRPCException ex);

  void runParagraphs(1: RunParagraphsEvent event) throws (1: RemoteInterpreterService.InterpreterRPCException ex);

  void addAngularObject(1: string intpGroupId, 2: string json) throws (1: RemoteInterpreterService.InterpreterRPCException ex);
  void updateAngularObject(1: string intpGroupId, 2: string json) throws (1: RemoteInterpreterService.InterpreterRPCException ex);
  void removeAngularObject(1: string intpGroupId, 2: string noteId, 3: string paragraphId, 4: string name) throws (1: RemoteInterpreterService.InterpreterRPCException ex);

  void sendWebUrl(1: WebUrlInfo weburlInfo) throws (1: RemoteInterpreterService.InterpreterRPCException ex);
  void sendParagraphInfo(1: string intpGroupId, 2: string json) throws (1: RemoteInterpreterService.InterpreterRPCException ex);
  void updateParagraphConfig(1: string noteId, 2: string paragraphId, 3: map<string, string> config) throws (1: RemoteInterpreterService.InterpreterRPCException ex);

  list<string> getAllResources(1: string intpGroupId) throws (1: RemoteInterpreterService.InterpreterRPCException ex);
  binary getResource(1: string resourceIdJson) throws (1: RemoteInterpreterService.InterpreterRPCException ex);
  binary invokeMethod(1: string intpGroupId, 2: string invokeMethodJson) throws (1: RemoteInterpreterService.InterpreterRPCException ex);

  list<ParagraphInfo> getParagraphList(1: string user, 2: string noteId) throws (1: RemoteInterpreterService.InterpreterRPCException ex);

  list<LibraryMetadata> getAllLibraryMetadatas(1: string intpSettingName);
  binary getLibrary(1: string intpSettingName, 2: string libraryName);
}
