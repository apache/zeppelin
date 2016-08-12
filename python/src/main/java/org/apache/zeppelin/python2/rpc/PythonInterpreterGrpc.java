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
package org.apache.zeppelin.python2.rpc;

import static io.grpc.stub.ClientCalls.asyncUnaryCall;
import static io.grpc.stub.ClientCalls.asyncServerStreamingCall;
import static io.grpc.stub.ClientCalls.asyncClientStreamingCall;
import static io.grpc.stub.ClientCalls.asyncBidiStreamingCall;
import static io.grpc.stub.ClientCalls.blockingUnaryCall;
import static io.grpc.stub.ClientCalls.blockingServerStreamingCall;
import static io.grpc.stub.ClientCalls.futureUnaryCall;
import static io.grpc.MethodDescriptor.generateFullMethodName;
import static io.grpc.stub.ServerCalls.asyncUnaryCall;
import static io.grpc.stub.ServerCalls.asyncServerStreamingCall;
import static io.grpc.stub.ServerCalls.asyncClientStreamingCall;
import static io.grpc.stub.ServerCalls.asyncBidiStreamingCall;
import static io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall;
import static io.grpc.stub.ServerCalls.asyncUnimplementedStreamingCall;

/**
 */
@javax.annotation.Generated(
    value = "by gRPC proto compiler (version 1.1.0-SNAPSHOT)",
    comments = "Source: python_interpreter.proto")
public class PythonInterpreterGrpc {

  private PythonInterpreterGrpc() {}

  public static final String SERVICE_NAME = "PythonInterpreter";

  // Static method descriptors that strictly reflect the proto.
  @io.grpc.ExperimentalApi("https://github.com/grpc/grpc-java/issues/1901")
  public static final io.grpc.MethodDescriptor<org.apache.zeppelin.python2.rpc.PythonInterpreterOuterClass.CodeInterpreteRequest,
      org.apache.zeppelin.python2.rpc.PythonInterpreterOuterClass.InterpetedResult> METHOD_INTERPRETE =
      io.grpc.MethodDescriptor.create(
          io.grpc.MethodDescriptor.MethodType.UNARY,
          generateFullMethodName(
              "PythonInterpreter", "Interprete"),
          io.grpc.protobuf.ProtoUtils.marshaller(org.apache.zeppelin.python2.rpc.PythonInterpreterOuterClass.CodeInterpreteRequest.getDefaultInstance()),
          io.grpc.protobuf.ProtoUtils.marshaller(org.apache.zeppelin.python2.rpc.PythonInterpreterOuterClass.InterpetedResult.getDefaultInstance()));
  @io.grpc.ExperimentalApi("https://github.com/grpc/grpc-java/issues/1901")
  public static final io.grpc.MethodDescriptor<org.apache.zeppelin.python2.rpc.PythonInterpreterOuterClass.CodeCompletionRequest,
      org.apache.zeppelin.python2.rpc.PythonInterpreterOuterClass.CodeSuggestions> METHOD_AUTO_COMPLETE =
      io.grpc.MethodDescriptor.create(
          io.grpc.MethodDescriptor.MethodType.UNARY,
          generateFullMethodName(
              "PythonInterpreter", "AutoComplete"),
          io.grpc.protobuf.ProtoUtils.marshaller(org.apache.zeppelin.python2.rpc.PythonInterpreterOuterClass.CodeCompletionRequest.getDefaultInstance()),
          io.grpc.protobuf.ProtoUtils.marshaller(org.apache.zeppelin.python2.rpc.PythonInterpreterOuterClass.CodeSuggestions.getDefaultInstance()));
  @io.grpc.ExperimentalApi("https://github.com/grpc/grpc-java/issues/1901")
  public static final io.grpc.MethodDescriptor<org.apache.zeppelin.python2.rpc.PythonInterpreterOuterClass.Void,
      org.apache.zeppelin.python2.rpc.PythonInterpreterOuterClass.ProgressIndicator> METHOD_PROGRESS =
      io.grpc.MethodDescriptor.create(
          io.grpc.MethodDescriptor.MethodType.UNARY,
          generateFullMethodName(
              "PythonInterpreter", "Progress"),
          io.grpc.protobuf.ProtoUtils.marshaller(org.apache.zeppelin.python2.rpc.PythonInterpreterOuterClass.Void.getDefaultInstance()),
          io.grpc.protobuf.ProtoUtils.marshaller(org.apache.zeppelin.python2.rpc.PythonInterpreterOuterClass.ProgressIndicator.getDefaultInstance()));
  @io.grpc.ExperimentalApi("https://github.com/grpc/grpc-java/issues/1901")
  public static final io.grpc.MethodDescriptor<org.apache.zeppelin.python2.rpc.PythonInterpreterOuterClass.Void,
      org.apache.zeppelin.python2.rpc.PythonInterpreterOuterClass.Void> METHOD_SHUTDOWN =
      io.grpc.MethodDescriptor.create(
          io.grpc.MethodDescriptor.MethodType.UNARY,
          generateFullMethodName(
              "PythonInterpreter", "Shutdown"),
          io.grpc.protobuf.ProtoUtils.marshaller(org.apache.zeppelin.python2.rpc.PythonInterpreterOuterClass.Void.getDefaultInstance()),
          io.grpc.protobuf.ProtoUtils.marshaller(org.apache.zeppelin.python2.rpc.PythonInterpreterOuterClass.Void.getDefaultInstance()));

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static PythonInterpreterStub newStub(io.grpc.Channel channel) {
    return new PythonInterpreterStub(channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static PythonInterpreterBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    return new PythonInterpreterBlockingStub(channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary and streaming output calls on the service
   */
  public static PythonInterpreterFutureStub newFutureStub(
      io.grpc.Channel channel) {
    return new PythonInterpreterFutureStub(channel);
  }

  /**
   */
  public static abstract class PythonInterpreterImplBase implements io.grpc.BindableService, PythonInterpreter {

    /**
     * <pre>
     * Blocking RPC to interpreter pice of code
     * </pre>
     */
    @java.lang.Override
    public void interprete(org.apache.zeppelin.python2.rpc.PythonInterpreterOuterClass.CodeInterpreteRequest request,
        io.grpc.stub.StreamObserver<org.apache.zeppelin.python2.rpc.PythonInterpreterOuterClass.InterpetedResult> responseObserver) {
      asyncUnimplementedUnaryCall(METHOD_INTERPRETE, responseObserver);
    }

    /**
     */
    @java.lang.Override
    public void autoComplete(org.apache.zeppelin.python2.rpc.PythonInterpreterOuterClass.CodeCompletionRequest request,
        io.grpc.stub.StreamObserver<org.apache.zeppelin.python2.rpc.PythonInterpreterOuterClass.CodeSuggestions> responseObserver) {
      asyncUnimplementedUnaryCall(METHOD_AUTO_COMPLETE, responseObserver);
    }

    /**
     */
    @java.lang.Override
    public void progress(org.apache.zeppelin.python2.rpc.PythonInterpreterOuterClass.Void request,
        io.grpc.stub.StreamObserver<org.apache.zeppelin.python2.rpc.PythonInterpreterOuterClass.ProgressIndicator> responseObserver) {
      asyncUnimplementedUnaryCall(METHOD_PROGRESS, responseObserver);
    }

    /**
     * <pre>
     * Terminates this RPC Server python process
     * </pre>
     */
    @java.lang.Override
    public void shutdown(org.apache.zeppelin.python2.rpc.PythonInterpreterOuterClass.Void request,
        io.grpc.stub.StreamObserver<org.apache.zeppelin.python2.rpc.PythonInterpreterOuterClass.Void> responseObserver) {
      asyncUnimplementedUnaryCall(METHOD_SHUTDOWN, responseObserver);
    }

    @java.lang.Override public io.grpc.ServerServiceDefinition bindService() {
      return io.grpc.ServerServiceDefinition.builder(getServiceDescriptor())
          .addMethod(
            METHOD_INTERPRETE,
            asyncUnaryCall(
              new MethodHandlers<
                org.apache.zeppelin.python2.rpc.PythonInterpreterOuterClass.CodeInterpreteRequest,
                org.apache.zeppelin.python2.rpc.PythonInterpreterOuterClass.InterpetedResult>(
                  this, METHODID_INTERPRETE)))
          .addMethod(
            METHOD_AUTO_COMPLETE,
            asyncUnaryCall(
              new MethodHandlers<
                org.apache.zeppelin.python2.rpc.PythonInterpreterOuterClass.CodeCompletionRequest,
                org.apache.zeppelin.python2.rpc.PythonInterpreterOuterClass.CodeSuggestions>(
                  this, METHODID_AUTO_COMPLETE)))
          .addMethod(
            METHOD_PROGRESS,
            asyncUnaryCall(
              new MethodHandlers<
                org.apache.zeppelin.python2.rpc.PythonInterpreterOuterClass.Void,
                org.apache.zeppelin.python2.rpc.PythonInterpreterOuterClass.ProgressIndicator>(
                  this, METHODID_PROGRESS)))
          .addMethod(
            METHOD_SHUTDOWN,
            asyncUnaryCall(
              new MethodHandlers<
                org.apache.zeppelin.python2.rpc.PythonInterpreterOuterClass.Void,
                org.apache.zeppelin.python2.rpc.PythonInterpreterOuterClass.Void>(
                  this, METHODID_SHUTDOWN)))
          .build();
    }
  }

  /**
   */
  public static class PythonInterpreterStub extends io.grpc.stub.AbstractStub<PythonInterpreterStub>
      implements PythonInterpreter {
    private PythonInterpreterStub(io.grpc.Channel channel) {
      super(channel);
    }

    private PythonInterpreterStub(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected PythonInterpreterStub build(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      return new PythonInterpreterStub(channel, callOptions);
    }

    /**
     * <pre>
     * Blocking RPC to interpreter pice of code
     * </pre>
     */
    @java.lang.Override
    public void interprete(org.apache.zeppelin.python2.rpc.PythonInterpreterOuterClass.CodeInterpreteRequest request,
        io.grpc.stub.StreamObserver<org.apache.zeppelin.python2.rpc.PythonInterpreterOuterClass.InterpetedResult> responseObserver) {
      asyncUnaryCall(
          getChannel().newCall(METHOD_INTERPRETE, getCallOptions()), request, responseObserver);
    }

    /**
     */
    @java.lang.Override
    public void autoComplete(org.apache.zeppelin.python2.rpc.PythonInterpreterOuterClass.CodeCompletionRequest request,
        io.grpc.stub.StreamObserver<org.apache.zeppelin.python2.rpc.PythonInterpreterOuterClass.CodeSuggestions> responseObserver) {
      asyncUnaryCall(
          getChannel().newCall(METHOD_AUTO_COMPLETE, getCallOptions()), request, responseObserver);
    }

    /**
     */
    @java.lang.Override
    public void progress(org.apache.zeppelin.python2.rpc.PythonInterpreterOuterClass.Void request,
        io.grpc.stub.StreamObserver<org.apache.zeppelin.python2.rpc.PythonInterpreterOuterClass.ProgressIndicator> responseObserver) {
      asyncUnaryCall(
          getChannel().newCall(METHOD_PROGRESS, getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * Terminates this RPC Server python process
     * </pre>
     */
    @java.lang.Override
    public void shutdown(org.apache.zeppelin.python2.rpc.PythonInterpreterOuterClass.Void request,
        io.grpc.stub.StreamObserver<org.apache.zeppelin.python2.rpc.PythonInterpreterOuterClass.Void> responseObserver) {
      asyncUnaryCall(
          getChannel().newCall(METHOD_SHUTDOWN, getCallOptions()), request, responseObserver);
    }
  }

  /**
   */
  public static class PythonInterpreterBlockingStub extends io.grpc.stub.AbstractStub<PythonInterpreterBlockingStub>
      implements PythonInterpreterBlockingClient {
    private PythonInterpreterBlockingStub(io.grpc.Channel channel) {
      super(channel);
    }

    private PythonInterpreterBlockingStub(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected PythonInterpreterBlockingStub build(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      return new PythonInterpreterBlockingStub(channel, callOptions);
    }

    /**
     * <pre>
     * Blocking RPC to interpreter pice of code
     * </pre>
     */
    @java.lang.Override
    public org.apache.zeppelin.python2.rpc.PythonInterpreterOuterClass.InterpetedResult interprete(org.apache.zeppelin.python2.rpc.PythonInterpreterOuterClass.CodeInterpreteRequest request) {
      return blockingUnaryCall(
          getChannel(), METHOD_INTERPRETE, getCallOptions(), request);
    }

    /**
     */
    @java.lang.Override
    public org.apache.zeppelin.python2.rpc.PythonInterpreterOuterClass.CodeSuggestions autoComplete(org.apache.zeppelin.python2.rpc.PythonInterpreterOuterClass.CodeCompletionRequest request) {
      return blockingUnaryCall(
          getChannel(), METHOD_AUTO_COMPLETE, getCallOptions(), request);
    }

    /**
     */
    @java.lang.Override
    public org.apache.zeppelin.python2.rpc.PythonInterpreterOuterClass.ProgressIndicator progress(org.apache.zeppelin.python2.rpc.PythonInterpreterOuterClass.Void request) {
      return blockingUnaryCall(
          getChannel(), METHOD_PROGRESS, getCallOptions(), request);
    }

    /**
     * <pre>
     * Terminates this RPC Server python process
     * </pre>
     */
    @java.lang.Override
    public org.apache.zeppelin.python2.rpc.PythonInterpreterOuterClass.Void shutdown(org.apache.zeppelin.python2.rpc.PythonInterpreterOuterClass.Void request) {
      return blockingUnaryCall(
          getChannel(), METHOD_SHUTDOWN, getCallOptions(), request);
    }
  }

  /**
   */
  public static class PythonInterpreterFutureStub extends io.grpc.stub.AbstractStub<PythonInterpreterFutureStub>
      implements PythonInterpreterFutureClient {
    private PythonInterpreterFutureStub(io.grpc.Channel channel) {
      super(channel);
    }

    private PythonInterpreterFutureStub(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected PythonInterpreterFutureStub build(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      return new PythonInterpreterFutureStub(channel, callOptions);
    }

    /**
     * <pre>
     * Blocking RPC to interpreter pice of code
     * </pre>
     */
    @java.lang.Override
    public com.google.common.util.concurrent.ListenableFuture<org.apache.zeppelin.python2.rpc.PythonInterpreterOuterClass.InterpetedResult> interprete(
        org.apache.zeppelin.python2.rpc.PythonInterpreterOuterClass.CodeInterpreteRequest request) {
      return futureUnaryCall(
          getChannel().newCall(METHOD_INTERPRETE, getCallOptions()), request);
    }

    /**
     */
    @java.lang.Override
    public com.google.common.util.concurrent.ListenableFuture<org.apache.zeppelin.python2.rpc.PythonInterpreterOuterClass.CodeSuggestions> autoComplete(
        org.apache.zeppelin.python2.rpc.PythonInterpreterOuterClass.CodeCompletionRequest request) {
      return futureUnaryCall(
          getChannel().newCall(METHOD_AUTO_COMPLETE, getCallOptions()), request);
    }

    /**
     */
    @java.lang.Override
    public com.google.common.util.concurrent.ListenableFuture<org.apache.zeppelin.python2.rpc.PythonInterpreterOuterClass.ProgressIndicator> progress(
        org.apache.zeppelin.python2.rpc.PythonInterpreterOuterClass.Void request) {
      return futureUnaryCall(
          getChannel().newCall(METHOD_PROGRESS, getCallOptions()), request);
    }

    /**
     * <pre>
     * Terminates this RPC Server python process
     * </pre>
     */
    @java.lang.Override
    public com.google.common.util.concurrent.ListenableFuture<org.apache.zeppelin.python2.rpc.PythonInterpreterOuterClass.Void> shutdown(
        org.apache.zeppelin.python2.rpc.PythonInterpreterOuterClass.Void request) {
      return futureUnaryCall(
          getChannel().newCall(METHOD_SHUTDOWN, getCallOptions()), request);
    }
  }

  /**
   * This will be removed in the next release.
   * If your code has been using gRPC-java v0.15.0 or higher already,
   * the following changes to your code are suggested:
   * <ul>
   *   <li> replace {@code extends/implements PythonInterpreter} with {@code extends PythonInterpreterImplBase} for server side;</li>
   *   <li> replace {@code PythonInterpreter} with {@code PythonInterpreterStub} for client side;</li>
   *   <li> replace usage of {@code PythonInterpreter} with {@code PythonInterpreterImplBase};</li>
   *   <li> replace usage of {@code AbstractPythonInterpreter} with {@link PythonInterpreterImplBase};</li>
   *   <li> replace {@code serverBuilder.addService(PythonInterpreterGrpc.bindService(serviceImpl))}
   *        with {@code serverBuilder.addService(serviceImpl)};</li>
   *   <li> if you are mocking stubs using mockito, please do not mock them.
   *        See the documentation on testing with gRPC-java;</li>
   *   <li> replace {@code PythonInterpreterBlockingClient} with {@link PythonInterpreterBlockingStub};</li>
   *   <li> replace {@code PythonInterpreterFutureClient} with {@link PythonInterpreterFutureStub}.</li>
   * </ul>
   */
  @java.lang.Deprecated public static interface PythonInterpreter {

    public void interprete(org.apache.zeppelin.python2.rpc.PythonInterpreterOuterClass.CodeInterpreteRequest request,
        io.grpc.stub.StreamObserver<org.apache.zeppelin.python2.rpc.PythonInterpreterOuterClass.InterpetedResult> responseObserver);

    public void autoComplete(org.apache.zeppelin.python2.rpc.PythonInterpreterOuterClass.CodeCompletionRequest request,
        io.grpc.stub.StreamObserver<org.apache.zeppelin.python2.rpc.PythonInterpreterOuterClass.CodeSuggestions> responseObserver);

    public void progress(org.apache.zeppelin.python2.rpc.PythonInterpreterOuterClass.Void request,
        io.grpc.stub.StreamObserver<org.apache.zeppelin.python2.rpc.PythonInterpreterOuterClass.ProgressIndicator> responseObserver);

    public void shutdown(org.apache.zeppelin.python2.rpc.PythonInterpreterOuterClass.Void request,
        io.grpc.stub.StreamObserver<org.apache.zeppelin.python2.rpc.PythonInterpreterOuterClass.Void> responseObserver);
  }

  /**
   * This will be removed in the next release.
   * If your code has been using gRPC-java v0.15.0 or higher already,
   * the following changes to your code are suggested:
   * <ul>
   *   <li> replace {@code extends/implements PythonInterpreter} with {@code extends PythonInterpreterImplBase} for server side;</li>
   *   <li> replace {@code PythonInterpreter} with {@code PythonInterpreterStub} for client side;</li>
   *   <li> replace usage of {@code PythonInterpreter} with {@code PythonInterpreterImplBase};</li>
   *   <li> replace usage of {@code AbstractPythonInterpreter} with {@link PythonInterpreterImplBase};</li>
   *   <li> replace {@code serverBuilder.addService(PythonInterpreterGrpc.bindService(serviceImpl))}
   *        with {@code serverBuilder.addService(serviceImpl)};</li>
   *   <li> if you are mocking stubs using mockito, please do not mock them.
   *        See the documentation on testing with gRPC-java;</li>
   *   <li> replace {@code PythonInterpreterBlockingClient} with {@link PythonInterpreterBlockingStub};</li>
   *   <li> replace {@code PythonInterpreterFutureClient} with {@link PythonInterpreterFutureStub}.</li>
   * </ul>
   */
  @java.lang.Deprecated public static interface PythonInterpreterBlockingClient {

    public org.apache.zeppelin.python2.rpc.PythonInterpreterOuterClass.InterpetedResult interprete(org.apache.zeppelin.python2.rpc.PythonInterpreterOuterClass.CodeInterpreteRequest request);

    public org.apache.zeppelin.python2.rpc.PythonInterpreterOuterClass.CodeSuggestions autoComplete(org.apache.zeppelin.python2.rpc.PythonInterpreterOuterClass.CodeCompletionRequest request);

    public org.apache.zeppelin.python2.rpc.PythonInterpreterOuterClass.ProgressIndicator progress(org.apache.zeppelin.python2.rpc.PythonInterpreterOuterClass.Void request);

    public org.apache.zeppelin.python2.rpc.PythonInterpreterOuterClass.Void shutdown(org.apache.zeppelin.python2.rpc.PythonInterpreterOuterClass.Void request);
  }

  /**
   * This will be removed in the next release.
   * If your code has been using gRPC-java v0.15.0 or higher already,
   * the following changes to your code are suggested:
   * <ul>
   *   <li> replace {@code extends/implements PythonInterpreter} with {@code extends PythonInterpreterImplBase} for server side;</li>
   *   <li> replace {@code PythonInterpreter} with {@code PythonInterpreterStub} for client side;</li>
   *   <li> replace usage of {@code PythonInterpreter} with {@code PythonInterpreterImplBase};</li>
   *   <li> replace usage of {@code AbstractPythonInterpreter} with {@link PythonInterpreterImplBase};</li>
   *   <li> replace {@code serverBuilder.addService(PythonInterpreterGrpc.bindService(serviceImpl))}
   *        with {@code serverBuilder.addService(serviceImpl)};</li>
   *   <li> if you are mocking stubs using mockito, please do not mock them.
   *        See the documentation on testing with gRPC-java;</li>
   *   <li> replace {@code PythonInterpreterBlockingClient} with {@link PythonInterpreterBlockingStub};</li>
   *   <li> replace {@code PythonInterpreterFutureClient} with {@link PythonInterpreterFutureStub}.</li>
   * </ul>
   */
  @java.lang.Deprecated public static interface PythonInterpreterFutureClient {

    public com.google.common.util.concurrent.ListenableFuture<org.apache.zeppelin.python2.rpc.PythonInterpreterOuterClass.InterpetedResult> interprete(
        org.apache.zeppelin.python2.rpc.PythonInterpreterOuterClass.CodeInterpreteRequest request);

    public com.google.common.util.concurrent.ListenableFuture<org.apache.zeppelin.python2.rpc.PythonInterpreterOuterClass.CodeSuggestions> autoComplete(
        org.apache.zeppelin.python2.rpc.PythonInterpreterOuterClass.CodeCompletionRequest request);

    public com.google.common.util.concurrent.ListenableFuture<org.apache.zeppelin.python2.rpc.PythonInterpreterOuterClass.ProgressIndicator> progress(
        org.apache.zeppelin.python2.rpc.PythonInterpreterOuterClass.Void request);

    public com.google.common.util.concurrent.ListenableFuture<org.apache.zeppelin.python2.rpc.PythonInterpreterOuterClass.Void> shutdown(
        org.apache.zeppelin.python2.rpc.PythonInterpreterOuterClass.Void request);
  }

  /**
   * This will be removed in the next release.
   * If your code has been using gRPC-java v0.15.0 or higher already,
   * the following changes to your code are suggested:
   * <ul>
   *   <li> replace {@code extends/implements PythonInterpreter} with {@code extends PythonInterpreterImplBase} for server side;</li>
   *   <li> replace {@code PythonInterpreter} with {@code PythonInterpreterStub} for client side;</li>
   *   <li> replace usage of {@code PythonInterpreter} with {@code PythonInterpreterImplBase};</li>
   *   <li> replace usage of {@code AbstractPythonInterpreter} with {@link PythonInterpreterImplBase};</li>
   *   <li> replace {@code serverBuilder.addService(PythonInterpreterGrpc.bindService(serviceImpl))}
   *        with {@code serverBuilder.addService(serviceImpl)};</li>
   *   <li> if you are mocking stubs using mockito, please do not mock them.
   *        See the documentation on testing with gRPC-java;</li>
   *   <li> replace {@code PythonInterpreterBlockingClient} with {@link PythonInterpreterBlockingStub};</li>
   *   <li> replace {@code PythonInterpreterFutureClient} with {@link PythonInterpreterFutureStub}.</li>
   * </ul>
   */
  @java.lang.Deprecated public static abstract class AbstractPythonInterpreter extends PythonInterpreterImplBase {}

  /**
   * This will be removed in the next release.
   * If your code has been using gRPC-java v0.15.0 or higher already,
   * the following changes to your code are suggested:
   * <ul>
   *   <li> replace {@code extends/implements PythonInterpreter} with {@code extends PythonInterpreterImplBase} for server side;</li>
   *   <li> replace {@code PythonInterpreter} with {@code PythonInterpreterStub} for client side;</li>
   *   <li> replace usage of {@code PythonInterpreter} with {@code PythonInterpreterImplBase};</li>
   *   <li> replace usage of {@code AbstractPythonInterpreter} with {@link PythonInterpreterImplBase};</li>
   *   <li> replace {@code serverBuilder.addService(PythonInterpreterGrpc.bindService(serviceImpl))}
   *        with {@code serverBuilder.addService(serviceImpl)};</li>
   *   <li> if you are mocking stubs using mockito, please do not mock them.
   *        See the documentation on testing with gRPC-java;</li>
   *   <li> replace {@code PythonInterpreterBlockingClient} with {@link PythonInterpreterBlockingStub};</li>
   *   <li> replace {@code PythonInterpreterFutureClient} with {@link PythonInterpreterFutureStub}.</li>
   * </ul>
   */
  @java.lang.Deprecated public static io.grpc.ServerServiceDefinition bindService(final PythonInterpreter serviceImpl) {
    return io.grpc.ServerServiceDefinition.builder(getServiceDescriptor())
        .addMethod(
          METHOD_INTERPRETE,
          asyncUnaryCall(
            new MethodHandlers<
              org.apache.zeppelin.python2.rpc.PythonInterpreterOuterClass.CodeInterpreteRequest,
              org.apache.zeppelin.python2.rpc.PythonInterpreterOuterClass.InterpetedResult>(
                serviceImpl, METHODID_INTERPRETE)))
        .addMethod(
          METHOD_AUTO_COMPLETE,
          asyncUnaryCall(
            new MethodHandlers<
              org.apache.zeppelin.python2.rpc.PythonInterpreterOuterClass.CodeCompletionRequest,
              org.apache.zeppelin.python2.rpc.PythonInterpreterOuterClass.CodeSuggestions>(
                serviceImpl, METHODID_AUTO_COMPLETE)))
        .addMethod(
          METHOD_PROGRESS,
          asyncUnaryCall(
            new MethodHandlers<
              org.apache.zeppelin.python2.rpc.PythonInterpreterOuterClass.Void,
              org.apache.zeppelin.python2.rpc.PythonInterpreterOuterClass.ProgressIndicator>(
                serviceImpl, METHODID_PROGRESS)))
        .addMethod(
          METHOD_SHUTDOWN,
          asyncUnaryCall(
            new MethodHandlers<
              org.apache.zeppelin.python2.rpc.PythonInterpreterOuterClass.Void,
              org.apache.zeppelin.python2.rpc.PythonInterpreterOuterClass.Void>(
                serviceImpl, METHODID_SHUTDOWN)))
        .build();
  }

  private static final int METHODID_INTERPRETE = 0;
  private static final int METHODID_AUTO_COMPLETE = 1;
  private static final int METHODID_PROGRESS = 2;
  private static final int METHODID_SHUTDOWN = 3;

  private static class MethodHandlers<Req, Resp> implements
      io.grpc.stub.ServerCalls.UnaryMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ServerStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ClientStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.BidiStreamingMethod<Req, Resp> {
    private final PythonInterpreter serviceImpl;
    private final int methodId;

    public MethodHandlers(PythonInterpreter serviceImpl, int methodId) {
      this.serviceImpl = serviceImpl;
      this.methodId = methodId;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public void invoke(Req request, io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        case METHODID_INTERPRETE:
          serviceImpl.interprete((org.apache.zeppelin.python2.rpc.PythonInterpreterOuterClass.CodeInterpreteRequest) request,
              (io.grpc.stub.StreamObserver<org.apache.zeppelin.python2.rpc.PythonInterpreterOuterClass.InterpetedResult>) responseObserver);
          break;
        case METHODID_AUTO_COMPLETE:
          serviceImpl.autoComplete((org.apache.zeppelin.python2.rpc.PythonInterpreterOuterClass.CodeCompletionRequest) request,
              (io.grpc.stub.StreamObserver<org.apache.zeppelin.python2.rpc.PythonInterpreterOuterClass.CodeSuggestions>) responseObserver);
          break;
        case METHODID_PROGRESS:
          serviceImpl.progress((org.apache.zeppelin.python2.rpc.PythonInterpreterOuterClass.Void) request,
              (io.grpc.stub.StreamObserver<org.apache.zeppelin.python2.rpc.PythonInterpreterOuterClass.ProgressIndicator>) responseObserver);
          break;
        case METHODID_SHUTDOWN:
          serviceImpl.shutdown((org.apache.zeppelin.python2.rpc.PythonInterpreterOuterClass.Void) request,
              (io.grpc.stub.StreamObserver<org.apache.zeppelin.python2.rpc.PythonInterpreterOuterClass.Void>) responseObserver);
          break;
        default:
          throw new AssertionError();
      }
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public io.grpc.stub.StreamObserver<Req> invoke(
        io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        default:
          throw new AssertionError();
      }
    }
  }

  public static io.grpc.ServiceDescriptor getServiceDescriptor() {
    return new io.grpc.ServiceDescriptor(SERVICE_NAME,
        METHOD_INTERPRETE,
        METHOD_AUTO_COMPLETE,
        METHOD_PROGRESS,
        METHOD_SHUTDOWN);
  }

}
