package org.apache.zeppelin.python2;

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
  public static final io.grpc.MethodDescriptor<PythonInterpreterOuterClass.CodeRequest,
      PythonInterpreterOuterClass.InterpetedResult> METHOD_INTERPRETE =
      io.grpc.MethodDescriptor.create(
          io.grpc.MethodDescriptor.MethodType.UNARY,
          generateFullMethodName(
              "PythonInterpreter", "Interprete"),
          io.grpc.protobuf.ProtoUtils.marshaller(PythonInterpreterOuterClass.CodeRequest.getDefaultInstance()),
          io.grpc.protobuf.ProtoUtils.marshaller(PythonInterpreterOuterClass.InterpetedResult.getDefaultInstance()));

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
     */
    @java.lang.Override
    public void interprete(PythonInterpreterOuterClass.CodeRequest request,
        io.grpc.stub.StreamObserver<PythonInterpreterOuterClass.InterpetedResult> responseObserver) {
      asyncUnimplementedUnaryCall(METHOD_INTERPRETE, responseObserver);
    }

    @java.lang.Override public io.grpc.ServerServiceDefinition bindService() {
      return io.grpc.ServerServiceDefinition.builder(getServiceDescriptor())
          .addMethod(
            METHOD_INTERPRETE,
            asyncUnaryCall(
              new MethodHandlers<
                PythonInterpreterOuterClass.CodeRequest,
                PythonInterpreterOuterClass.InterpetedResult>(
                  this, METHODID_INTERPRETE)))
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
     */
    @java.lang.Override
    public void interprete(PythonInterpreterOuterClass.CodeRequest request,
        io.grpc.stub.StreamObserver<PythonInterpreterOuterClass.InterpetedResult> responseObserver) {
      asyncUnaryCall(
          getChannel().newCall(METHOD_INTERPRETE, getCallOptions()), request, responseObserver);
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
     */
    @java.lang.Override
    public PythonInterpreterOuterClass.InterpetedResult interprete(PythonInterpreterOuterClass.CodeRequest request) {
      return blockingUnaryCall(
          getChannel(), METHOD_INTERPRETE, getCallOptions(), request);
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
     */
    @java.lang.Override
    public com.google.common.util.concurrent.ListenableFuture<PythonInterpreterOuterClass.InterpetedResult> interprete(
        PythonInterpreterOuterClass.CodeRequest request) {
      return futureUnaryCall(
          getChannel().newCall(METHOD_INTERPRETE, getCallOptions()), request);
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

    public void interprete(PythonInterpreterOuterClass.CodeRequest request,
        io.grpc.stub.StreamObserver<PythonInterpreterOuterClass.InterpetedResult> responseObserver);
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

    public PythonInterpreterOuterClass.InterpetedResult interprete(PythonInterpreterOuterClass.CodeRequest request);
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

    public com.google.common.util.concurrent.ListenableFuture<PythonInterpreterOuterClass.InterpetedResult> interprete(
        PythonInterpreterOuterClass.CodeRequest request);
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
              PythonInterpreterOuterClass.CodeRequest,
              PythonInterpreterOuterClass.InterpetedResult>(
                serviceImpl, METHODID_INTERPRETE)))
        .build();
  }

  private static final int METHODID_INTERPRETE = 0;

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
          serviceImpl.interprete((PythonInterpreterOuterClass.CodeRequest) request,
              (io.grpc.stub.StreamObserver<PythonInterpreterOuterClass.InterpetedResult>) responseObserver);
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
        METHOD_INTERPRETE);
  }

}
