package com.nflabs.zeppelin.interpreter.remote;


import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.util.Map;
import java.util.Properties;

import org.apache.thrift.TException;
import org.apache.thrift.server.TServer;
import org.apache.thrift.server.TThreadPoolServer;
import org.apache.thrift.transport.TServerSocket;
import org.apache.thrift.transport.TServerTransport;
import org.apache.thrift.transport.TTransportException;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.nflabs.zeppelin.display.GUI;
import com.nflabs.zeppelin.interpreter.Interpreter;
import com.nflabs.zeppelin.interpreter.InterpreterContext;
import com.nflabs.zeppelin.interpreter.InterpreterException;
import com.nflabs.zeppelin.interpreter.InterpreterGroup;
import com.nflabs.zeppelin.interpreter.InterpreterResult;
import com.nflabs.zeppelin.interpreter.thrift.RemoteInterpreterContext;
import com.nflabs.zeppelin.interpreter.thrift.RemoteInterpreterResult;
import com.nflabs.zeppelin.interpreter.thrift.RemoteInterpreterService;


/**
 *
 */
public class RemoteInterpreterServer implements RemoteInterpreterService.Iface {

  public static RemoteInterpreterService.Processor<RemoteInterpreterServer> processor;
  public static RemoteInterpreterServer handler;

  public static void main(String [] args) throws TTransportException {
    handler = new RemoteInterpreterServer();
    processor = new RemoteInterpreterService.Processor<RemoteInterpreterServer>(handler);

    int port = Integer.parseInt(args[0]);
    TServerTransport serverTransport = new TServerSocket(port);
    TServer server = new TThreadPoolServer(new TThreadPoolServer.Args(
        serverTransport).processor(processor));

    server.serve();
  }

  InterpreterGroup interpreterGroup = new InterpreterGroup();
  Gson gson = new Gson();

  @Override
  public int createInterpreter(String className, Map<String, String> properties)
      throws TException {
    try {
      Class<Interpreter> replClass = (Class<Interpreter>) Object.class.forName(className);
      Properties p = new Properties();
      p.putAll(properties);

      Constructor<Interpreter> constructor =
          replClass.getConstructor(new Class[] {Properties.class});
      Interpreter repl = constructor.newInstance(p);
      repl.setClassloaderUrls(new URL[]{});
      repl.setInterpreterGroup(interpreterGroup);

      synchronized (interpreterGroup) {
        interpreterGroup.add(repl);
      }

      return getId(repl);
    } catch (ClassNotFoundException | NoSuchMethodException | SecurityException
        | InstantiationException | IllegalAccessException
        | IllegalArgumentException | InvocationTargetException e) {
      e.printStackTrace();
      throw new TException(e);
    }
  }

  private int getId(Interpreter intp) {
    return intp.hashCode();
  }

  private Interpreter getInterpreter(int intpId) throws TException {
    synchronized (interpreterGroup) {
      for (Interpreter inp : interpreterGroup) {
        if (intpId == getId(inp)) {
          return inp;
        }
      }
    }
    throw new TException(new InterpreterException("Interpreter instance "
        + intpId + " not found"));
  }

  @Override
  public void open(int intpId) throws TException {
    Interpreter intp = getInterpreter(intpId);
    intp.open();
  }

  @Override
  public void close(int intpId) throws TException {
    Interpreter intp = getInterpreter(intpId);
    intp.close();
  }

  @Override
  public RemoteInterpreterResult interpret(int intpId, String st,
      RemoteInterpreterContext interpreterContext) throws TException {
    Interpreter intp = getInterpreter(intpId);
    return convert(intp.interpret(st, convert(interpreterContext)));
  }

  @Override
  public void cancel(int intpId, RemoteInterpreterContext interpreterContext)
      throws TException {
    Interpreter intp = getInterpreter(intpId);
    intp.cancel(convert(interpreterContext));
  }

  @Override
  public int getProgress(int intpId, RemoteInterpreterContext interpreterContext)
      throws TException {
    Interpreter intp = getInterpreter(intpId);
    return intp.getProgress(convert(interpreterContext));
  }

  private InterpreterContext convert(RemoteInterpreterContext ric) {
    return new InterpreterContext(
        ric.getParagraphId(),
        ric.getParagraphTitle(),
        ric.getParagraphText(),
        (Map<String, Object>) gson.fromJson(ric.getConfig(),
            new TypeToken<Map<String, Object>>() {}.getType()),
        gson.fromJson(ric.getGui(), GUI.class));
  }

  private RemoteInterpreterResult convert(InterpreterResult result) {
    return new RemoteInterpreterResult(
        result.code().name(),
        result.message());
  }



}
