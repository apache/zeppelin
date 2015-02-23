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
  private int intpId;
  Gson gson = new Gson();
  private String interpreterRunner;
  private String interpreterDir;
  private String className;
  static Map<String, RemoteInterpreterProcess> interpreterGroupReference
    = new HashMap<String, RemoteInterpreterProcess>();

  public RemoteInterpreter(Properties property,
      String className,
      String interpreterRunner,
      String interpreterDir) {
    super(property);

    this.className = className;
    this.interpreterRunner = interpreterRunner;
    this.interpreterDir = interpreterDir;

  }

  @Override
  public String getClassName() {
    return className;
  }

  private Client getClient() {
    synchronized (interpreterGroupReference) {
      if (interpreterGroupReference.containsKey(getInterpreterGroupKey(getInterpreterGroup()))) {
        RemoteInterpreterProcess interpreterProcess = interpreterGroupReference
            .get(getInterpreterGroupKey(getInterpreterGroup()));
        return interpreterProcess.getClient();
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

    try {
      intpId = interpreterProcess.getClient().createInterpreter(className, (Map) property);
    } catch (TException e) {
      e.printStackTrace();
      throw new InterpreterException(e.getCause());
    }

    try {
      interpreterProcess.getClient().open(intpId);
    } catch (TException e) {
      interpreterProcess.dereference();
      throw new InterpreterException(e.getCause());
    }
  }

  @Override
  public void close() {
    try {
      getClient().close(intpId);
    } catch (TException e) {
      throw new InterpreterException(e.getCause());
    }

    RemoteInterpreterProcess interpreterProcess = null;
    synchronized (interpreterGroupReference) {
      if (interpreterGroupReference
          .containsKey(getInterpreterGroupKey(getInterpreterGroup()))) {
        interpreterProcess = interpreterGroupReference
            .get(getInterpreterGroupKey(getInterpreterGroup()));
      } else {
        throw new InterpreterException("Unexpected error");
      }
    }

    interpreterProcess.dereference();
  }

  @Override
  public InterpreterResult interpret(String st, InterpreterContext context) {
    try {
      return convert(getClient().interpret(intpId, st, convert(context)));
    } catch (TException e) {
      throw new InterpreterException(e.getCause());
    }
  }

  @Override
  public void cancel(InterpreterContext context) {
    try {
      getClient().cancel(intpId, convert(context));
    } catch (TException e) {
      throw new InterpreterException(e.getCause());
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
    if (!isInterpreterCreated()) {
      return 0;
    }



    try {
      return getClient().getProgress(intpId, convert(context));
    } catch (TException e) {
      e.printStackTrace();
      throw new InterpreterException(e.getCause());
    }
    */
  }

  private boolean isInterpreterCreated() {
    return intpId != 0;
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
                interpreterDir));

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
