package com.nflabs.zeppelin.interpreter.remote;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.thrift.TException;

import com.google.gson.Gson;
import com.nflabs.zeppelin.interpreter.Interpreter;
import com.nflabs.zeppelin.interpreter.InterpreterContext;
import com.nflabs.zeppelin.interpreter.InterpreterException;
import com.nflabs.zeppelin.interpreter.InterpreterGroup;
import com.nflabs.zeppelin.interpreter.InterpreterResult;
import com.nflabs.zeppelin.interpreter.thrift.RemoteInterpreterContext;
import com.nflabs.zeppelin.interpreter.thrift.RemoteInterpreterResult;
import com.nflabs.zeppelin.interpreter.thrift.RemoteInterpreterService.Client;

/**
 *
 */
public class RemoteInterpreter extends Interpreter {
  Gson gson = new Gson();
  private String interpreterRunner;
  private String interpreterPath;
  private String className;
  static Map<String, RemoteInterpreterProcess> interpreterGroupReference
    = new HashMap<String, RemoteInterpreterProcess>();

  public RemoteInterpreter(Properties property,
      String className,
      String interpreterRunner,
      String interpreterPath) {
    super(property);

    this.className = className;
    this.interpreterRunner = interpreterRunner;
    this.interpreterPath = interpreterPath;
  }

  @Override
  public String getClassName() {
    return className;
  }

  private RemoteInterpreterProcess getInterpreterProcess() {
    synchronized (interpreterGroupReference) {
      if (interpreterGroupReference.containsKey(getInterpreterGroupKey(getInterpreterGroup()))) {
        RemoteInterpreterProcess interpreterProcess = interpreterGroupReference
            .get(getInterpreterGroupKey(getInterpreterGroup()));
        try {
          return interpreterProcess;
        } catch (Exception e) {
          throw new InterpreterException(e);
        }
      } else {
        throw new InterpreterException("Unexpected error");
      }
    }
  }

  @Override
  public void open() {
    RemoteInterpreterProcess interpreterProcess = null;

    synchronized (interpreterGroupReference) {
      if (interpreterGroupReference.containsKey(getInterpreterGroupKey(getInterpreterGroup()))) {
        interpreterProcess = interpreterGroupReference
            .get(getInterpreterGroupKey(getInterpreterGroup()));
      } else {
        throw new InterpreterException("Unexpected error");
      }
    }

    interpreterProcess.reference();

    Client client = null;
    try {
      client = interpreterProcess.getClient();
    } catch (Exception e1) {
      throw new InterpreterException(e1);
    }

    try {
      client.createInterpreter(className, (Map) property);
      client.open(className);
    } catch (TException e) {
      e.printStackTrace();
      throw new InterpreterException(e.getCause());
    } finally {
      interpreterProcess.releaseClient(client);
    }
  }

  @Override
  public void close() {
    RemoteInterpreterProcess interpreterProcess = getInterpreterProcess();
    Client client = null;
    try {
      client = interpreterProcess.getClient();
    } catch (Exception e1) {
      throw new InterpreterException(e1);
    }

    try {
      client.close(className);
    } catch (TException e) {
      throw new InterpreterException(e.getCause());
    } finally {
      interpreterProcess.releaseClient(client);
    }

    interpreterProcess.dereference();
  }

  @Override
  public InterpreterResult interpret(String st, InterpreterContext context) {
    RemoteInterpreterProcess interpreterProcess = getInterpreterProcess();
    Client client = null;
    try {
      client = interpreterProcess.getClient();
    } catch (Exception e1) {
      throw new InterpreterException(e1);
    }

    try {
      return convert(client.interpret(className, st, convert(context)));
    } catch (TException e) {
      throw new InterpreterException(e.getCause());
    } finally {
      interpreterProcess.releaseClient(client);
    }
  }

  @Override
  public void cancel(InterpreterContext context) {
    RemoteInterpreterProcess interpreterProcess = getInterpreterProcess();
    Client client = null;
    try {
      client = interpreterProcess.getClient();
    } catch (Exception e1) {
      throw new InterpreterException(e1);
    }

    try {
      client.cancel(className, convert(context));
    } catch (TException e) {
      throw new InterpreterException(e.getCause());
    } finally {
      interpreterProcess.releaseClient(client);
    }
  }


  @Override
  public FormType getFormType() {
    return null;
  }

  @Override
  public int getProgress(InterpreterContext context) {
    return 0;
    /*
    RemoteInterpreterProcess interpreterProcess = getInterpreterProcess();
    Client client = null;
    try {
      client = interpreterProcess.getClient();
    } catch (Exception e1) {
      throw new InterpreterException(e1);
    }

    try {
      return client.getProgress(intpId, convert(context));
    } catch (TException e) {
      throw new InterpreterException(e.getCause());
    } finally {
      interpreterProcess.releaseClient(client);
    }
    */
  }


  @Override
  public List<String> completion(String buf, int cursor) {
    return null;
  }

  @Override
  public void setInterpreterGroup(InterpreterGroup interpreterGroup) {
    super.setInterpreterGroup(interpreterGroup);

    synchronized (interpreterGroupReference) {
      if (!interpreterGroupReference
          .containsKey(getInterpreterGroupKey(interpreterGroup))) {
        interpreterGroupReference.put(getInterpreterGroupKey(interpreterGroup),
            new RemoteInterpreterProcess(interpreterRunner,
                interpreterPath));

        System.out.println("create SetInterpreterGroup = "
            + getInterpreterGroupKey(interpreterGroup) + " class=" + className);
      }
    }
  }

  private String getInterpreterGroupKey(InterpreterGroup interpreterGroup) {
    return interpreterGroup.getId();
  }

  private RemoteInterpreterContext convert(InterpreterContext ic) {
    return new RemoteInterpreterContext(
        ic.getParagraphId(),
        ic.getParagraphTitle(),
        ic.getParagraphText(),
        gson.toJson(ic.getConfig()),
        gson.toJson(ic.getGui()));
  }

  private InterpreterResult convert(RemoteInterpreterResult result) {
    return new InterpreterResult(
        InterpreterResult.Code.valueOf(result.getCode()),
        result.getMsg());
  }
}
