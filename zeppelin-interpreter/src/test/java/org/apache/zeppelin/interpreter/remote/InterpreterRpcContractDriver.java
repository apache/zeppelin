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

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Transport-neutral test contract for Server-to-Interpreter control RPC implementations.
 *
 * <p>Transport adapters normalize their generated messages and failures into the value types in
 * this interface. The shared scenarios can therefore be reused without depending on a wire
 * protocol, client topology, or generated RPC classes.
 */
public interface InterpreterRpcContractDriver extends AutoCloseable {

  InterpreterSpec interpreterSpec();

  /** Starts the transport and performs its process-level initialization. */
  void start() throws Exception;

  void createInterpreter(InterpreterSpec spec) throws ContractFailure;

  FormType getFormType(InterpreterRef interpreter) throws ContractFailure;

  int getProgress(InterpreterRef interpreter, ContractContext context) throws ContractFailure;

  List<ContractCompletion> completion(
      InterpreterRef interpreter, String buffer, int cursor, ContractContext context)
      throws ContractFailure;

  ContractResult interpret(
      InterpreterRef interpreter, String statement, ContractContext context)
      throws ContractFailure;

  JobStatus getStatus(InterpreterRef interpreter, String jobId) throws ContractFailure;

  void cancel(InterpreterRef interpreter, ContractContext context) throws ContractFailure;

  void closeInterpreter(InterpreterRef interpreter) throws ContractFailure;

  ContractProbe probe();

  ContractFaults faults();

  @Override
  void close() throws Exception;

  enum FormType {
    NATIVE,
    SIMPLE,
    NONE
  }

  enum JobStatus {
    UNKNOWN,
    READY,
    PENDING,
    RUNNING,
    FINISHED,
    ERROR,
    ABORT
  }

  enum ResultCode {
    SUCCESS,
    INCOMPLETE,
    ERROR,
    KEEP_PREVIOUS_RESULT
  }

  enum ResultType {
    TEXT,
    HTML,
    ANGULAR,
    TABLE,
    IMG,
    SVG,
    NULL,
    NETWORK
  }

  enum FailureCategory {
    OPERATION_FAILED,
    INTERPRETER_NOT_FOUND,
    TRANSPORT_UNAVAILABLE
  }

  final class InterpreterRef {
    private final String interpreterGroupId;
    private final String sessionId;
    private final String className;

    public InterpreterRef(String interpreterGroupId, String sessionId, String className) {
      this.interpreterGroupId = interpreterGroupId;
      this.sessionId = sessionId;
      this.className = className;
    }

    public String getInterpreterGroupId() {
      return interpreterGroupId;
    }

    public String getSessionId() {
      return sessionId;
    }

    public String getClassName() {
      return className;
    }

    public InterpreterRef withSessionId(String replacementSessionId) {
      return new InterpreterRef(interpreterGroupId, replacementSessionId, className);
    }
  }

  final class InterpreterSpec {
    private final InterpreterRef interpreter;
    private final String userName;
    private final Map<String, String> properties;

    public InterpreterSpec(
        InterpreterRef interpreter, String userName, Map<String, String> properties) {
      this.interpreter = interpreter;
      this.userName = userName;
      this.properties = immutableCopy(properties);
    }

    public InterpreterRef getInterpreter() {
      return interpreter;
    }

    public String getUserName() {
      return userName;
    }

    public Map<String, String> getProperties() {
      return properties;
    }
  }

  final class ContractContext {
    private final String noteId;
    private final String noteName;
    private final String paragraphId;
    private final String replName;
    private final String paragraphTitle;
    private final String paragraphText;
    private final String userName;
    private final Set<String> userRoles;
    private final String userTicket;
    private final Map<String, String> localProperties;
    private final Map<String, Object> config;
    private final Map<String, Object> guiParameters;
    private final Map<String, Object> noteGuiParameters;

    public ContractContext(
        String noteId,
        String noteName,
        String paragraphId,
        String replName,
        String paragraphTitle,
        String paragraphText,
        String userName,
        Set<String> userRoles,
        String userTicket,
        Map<String, String> localProperties,
        Map<String, Object> config,
        Map<String, Object> guiParameters,
        Map<String, Object> noteGuiParameters) {
      this.noteId = noteId;
      this.noteName = noteName;
      this.paragraphId = paragraphId;
      this.replName = replName;
      this.paragraphTitle = paragraphTitle;
      this.paragraphText = paragraphText;
      this.userName = userName;
      this.userRoles = Collections.unmodifiableSet(new LinkedHashSet<>(userRoles));
      this.userTicket = userTicket;
      this.localProperties = immutableCopy(localProperties);
      this.config = immutableCopy(config);
      this.guiParameters = immutableCopy(guiParameters);
      this.noteGuiParameters = immutableCopy(noteGuiParameters);
    }

    public String getNoteId() {
      return noteId;
    }

    public String getNoteName() {
      return noteName;
    }

    public String getParagraphId() {
      return paragraphId;
    }

    public String getReplName() {
      return replName;
    }

    public String getParagraphTitle() {
      return paragraphTitle;
    }

    public String getParagraphText() {
      return paragraphText;
    }

    public String getUserName() {
      return userName;
    }

    public Set<String> getUserRoles() {
      return userRoles;
    }

    public String getUserTicket() {
      return userTicket;
    }

    public Map<String, String> getLocalProperties() {
      return localProperties;
    }

    public Map<String, Object> getConfig() {
      return config;
    }

    public Map<String, Object> getGuiParameters() {
      return guiParameters;
    }

    public Map<String, Object> getNoteGuiParameters() {
      return noteGuiParameters;
    }

    @Override
    public boolean equals(Object other) {
      if (this == other) {
        return true;
      }
      if (!(other instanceof ContractContext)) {
        return false;
      }
      ContractContext that = (ContractContext) other;
      return Objects.equals(noteId, that.noteId)
          && Objects.equals(noteName, that.noteName)
          && Objects.equals(paragraphId, that.paragraphId)
          && Objects.equals(replName, that.replName)
          && Objects.equals(paragraphTitle, that.paragraphTitle)
          && Objects.equals(paragraphText, that.paragraphText)
          && Objects.equals(userName, that.userName)
          && Objects.equals(userRoles, that.userRoles)
          && Objects.equals(userTicket, that.userTicket)
          && Objects.equals(localProperties, that.localProperties)
          && Objects.equals(config, that.config)
          && Objects.equals(guiParameters, that.guiParameters)
          && Objects.equals(noteGuiParameters, that.noteGuiParameters);
    }

    @Override
    public int hashCode() {
      return Objects.hash(
          noteId,
          noteName,
          paragraphId,
          replName,
          paragraphTitle,
          paragraphText,
          userName,
          userRoles,
          userTicket,
          localProperties,
          config,
          guiParameters,
          noteGuiParameters);
    }
  }

  final class ContractCompletion {
    private final String name;
    private final String value;
    private final String meta;

    public ContractCompletion(String name, String value, String meta) {
      this.name = name;
      this.value = value;
      this.meta = meta;
    }

    public String getName() {
      return name;
    }

    public String getValue() {
      return value;
    }

    public String getMeta() {
      return meta;
    }
  }

  final class ContractResultMessage {
    private final ResultType type;
    private final String data;

    public ContractResultMessage(ResultType type, String data) {
      this.type = type;
      this.data = data;
    }

    public ResultType getType() {
      return type;
    }

    public String getData() {
      return data;
    }
  }

  final class ContractResult {
    private final ResultCode code;
    private final List<ContractResultMessage> messages;
    private final Map<String, Object> config;
    private final Map<String, Object> guiParameters;
    private final Map<String, Object> noteGuiParameters;

    public ContractResult(
        ResultCode code,
        List<ContractResultMessage> messages,
        Map<String, Object> config,
        Map<String, Object> guiParameters,
        Map<String, Object> noteGuiParameters) {
      this.code = code;
      this.messages = Collections.unmodifiableList(new ArrayList<>(messages));
      this.config = immutableCopy(config);
      this.guiParameters = immutableCopy(guiParameters);
      this.noteGuiParameters = immutableCopy(noteGuiParameters);
    }

    public ResultCode getCode() {
      return code;
    }

    public List<ContractResultMessage> getMessages() {
      return messages;
    }

    public Map<String, Object> getConfig() {
      return config;
    }

    public Map<String, Object> getGuiParameters() {
      return guiParameters;
    }

    public Map<String, Object> getNoteGuiParameters() {
      return noteGuiParameters;
    }
  }

  final class ProbeSnapshot {
    private final int constructorCalls;
    private final int openCalls;
    private final int closeCalls;
    private final String completionBuffer;
    private final int completionCursor;
    private final String completionParagraphId;
    private final String cancelNoteId;
    private final String cancelParagraphId;
    private final ContractContext lastInterpretContext;

    public ProbeSnapshot(
        int constructorCalls,
        int openCalls,
        int closeCalls,
        String completionBuffer,
        int completionCursor,
        String completionParagraphId,
        String cancelNoteId,
        String cancelParagraphId,
        ContractContext lastInterpretContext) {
      this.constructorCalls = constructorCalls;
      this.openCalls = openCalls;
      this.closeCalls = closeCalls;
      this.completionBuffer = completionBuffer;
      this.completionCursor = completionCursor;
      this.completionParagraphId = completionParagraphId;
      this.cancelNoteId = cancelNoteId;
      this.cancelParagraphId = cancelParagraphId;
      this.lastInterpretContext = lastInterpretContext;
    }

    public int getConstructorCalls() {
      return constructorCalls;
    }

    public int getOpenCalls() {
      return openCalls;
    }

    public int getCloseCalls() {
      return closeCalls;
    }

    public String getCompletionBuffer() {
      return completionBuffer;
    }

    public int getCompletionCursor() {
      return completionCursor;
    }

    public String getCompletionParagraphId() {
      return completionParagraphId;
    }

    public String getCancelNoteId() {
      return cancelNoteId;
    }

    public String getCancelParagraphId() {
      return cancelParagraphId;
    }

    public ContractContext getLastInterpretContext() {
      return lastInterpretContext;
    }
  }

  interface ContractProbe {
    ProbeSnapshot snapshot();

    boolean awaitInterpretStarted(Duration timeout) throws InterruptedException;

    boolean awaitCancelObserved(Duration timeout) throws InterruptedException;

    void releaseInterpretation();
  }

  interface ContractFaults {
    void makeTransportUnavailable();

    void abortPendingCalls();
  }

  final class ContractFailure extends Exception {
    private final FailureCategory category;

    public ContractFailure(FailureCategory category, String message, Throwable cause) {
      super(message, cause);
      this.category = category;
    }

    public FailureCategory getCategory() {
      return category;
    }
  }

  private static <K, V> Map<K, V> immutableCopy(Map<K, V> source) {
    return Collections.unmodifiableMap(new LinkedHashMap<>(source));
  }
}
