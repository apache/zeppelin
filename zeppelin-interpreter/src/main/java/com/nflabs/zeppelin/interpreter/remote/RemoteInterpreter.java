package com.nflabs.zeppelin.interpreter.remote;

import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.thrift.TException;

import com.nflabs.zeppelin.interpreter.Interpreter;
import com.nflabs.zeppelin.interpreter.InterpreterContext;
import com.nflabs.zeppelin.interpreter.InterpreterException;
import com.nflabs.zeppelin.interpreter.InterpreterResult;
import com.nflabs.zeppelin.interpreter.thrift.RemoteInterpreterService;
import com.nflabs.zeppelin.interpreter.thrift.RemoteInterpreterService.Client;

/**
 *
 */
public class RemoteInterpreter extends Interpreter {
  private Client client;
  private int intpId;

  public RemoteInterpreter(Properties property,
      String className,
      RemoteInterpreterService.Client client) {
    super(property);
    this.client = client;
    try {
      intpId = client.createInterpreter(className, (Map) property);
    } catch (TException e) {
      throw new InterpreterException(e.getCause());
    }
  }

  @Override
  public void open() {
    try {
      client.open(intpId);
    } catch (TException e) {
      throw new InterpreterException(e.getCause());
    }
  }

  @Override
  public void close() {
    try {
      client.close(intpId);
    } catch (TException e) {
      throw new InterpreterException(e.getCause());
    }
  }

  @Override
  public InterpreterResult interpret(String st, InterpreterContext context) {
    return null;
  }

  @Override
  public void cancel(InterpreterContext context) {
  }


  @Override
  public FormType getFormType() {
    return null;
  }

  @Override
  public int getProgress(InterpreterContext context) {
    return 0;
  }

  @Override
  public List<String> completion(String buf, int cursor) {
    return null;
  }
}
