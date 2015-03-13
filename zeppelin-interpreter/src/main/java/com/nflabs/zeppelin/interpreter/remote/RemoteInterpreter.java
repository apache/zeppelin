package com.nflabs.zeppelin.interpreter.remote;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.thrift.TException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.nflabs.zeppelin.display.GUI;
import com.nflabs.zeppelin.interpreter.Interpreter;
import com.nflabs.zeppelin.interpreter.InterpreterContext;
import com.nflabs.zeppelin.interpreter.InterpreterException;
import com.nflabs.zeppelin.interpreter.InterpreterGroup;
import com.nflabs.zeppelin.interpreter.InterpreterResult;
import com.nflabs.zeppelin.interpreter.InterpreterResult.Type;
import com.nflabs.zeppelin.interpreter.thrift.RemoteInterpreterContext;
import com.nflabs.zeppelin.interpreter.thrift.RemoteInterpreterResult;
import com.nflabs.zeppelin.interpreter.thrift.RemoteInterpreterService.Client;
import com.nflabs.zeppelin.scheduler.Scheduler;
import com.nflabs.zeppelin.scheduler.SchedulerFactory;

/**
 *
 */
public class RemoteInterpreter extends Interpreter {
  Logger logger = LoggerFactory.getLogger(RemoteInterpreter.class);
  Gson gson = new Gson();
  private String interpreterRunner;
  private String interpreterPath;
  private String className;
  FormType formType;
  private Map<String, String> env;
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
    env = new HashMap<String, String>();
  }

  public RemoteInterpreter(Properties property,
      String className,
      String interpreterRunner,
      String interpreterPath,
      Map<String, String> env) {
    super(property);

    this.className = className;
    this.interpreterRunner = interpreterRunner;
    this.interpreterPath = interpreterPath;
    this.env = env;
  }

  @Override
  public String getClassName() {
    return className;
  }

  public RemoteInterpreterProcess getInterpreterProcess() {
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

  private void init() {
    RemoteInterpreterProcess interpreterProcess = null;

    synchronized (interpreterGroupReference) {
      if (interpreterGroupReference.containsKey(getInterpreterGroupKey(getInterpreterGroup()))) {
        interpreterProcess = interpreterGroupReference
            .get(getInterpreterGroupKey(getInterpreterGroup()));
      } else {
        throw new InterpreterException("Unexpected error");
      }
    }

    int rc = interpreterProcess.reference();

    synchronized (interpreterProcess) {
      // when first process created
      if (rc == 1) {
        // create all interpreter class in this interpreter group
        Client client = null;
        try {
          client = interpreterProcess.getClient();
        } catch (Exception e1) {
          throw new InterpreterException(e1);
        }

        try {
          for (Interpreter intp : this.getInterpreterGroup()) {
            logger.info("Create remote interpreter {}", intp.getClassName());
            client.createInterpreter(intp.getClassName(), (Map) property);

          }
        } catch (TException e) {
          throw new InterpreterException(e);
        } finally {
          interpreterProcess.releaseClient(client);
        }
      }
    }
  }



  @Override
  public void open() {
    init();

    RemoteInterpreterProcess interpreterProcess = getInterpreterProcess();

    Client client = null;
    try {
      client = interpreterProcess.getClient();
    } catch (Exception e1) {
      throw new InterpreterException(e1);
    }

    try {
      logger.info("open remote interpreter {}", className);
      client.open(className);
    } catch (TException e) {
      throw new InterpreterException(e);
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
      throw new InterpreterException(e);
    } finally {
      interpreterProcess.releaseClient(client);
    }

    interpreterProcess.dereference();
  }

  @Override
  public InterpreterResult interpret(String st, InterpreterContext context) {
    FormType form = getFormType();
    RemoteInterpreterProcess interpreterProcess = getInterpreterProcess();
    Client client = null;
    try {
      client = interpreterProcess.getClient();
    } catch (Exception e1) {
      throw new InterpreterException(e1);
    }

    try {
      GUI settings = context.getGui();
      RemoteInterpreterResult remoteResult = client.interpret(className, st, convert(context));

      Map<String, Object> remoteConfig = (Map<String, Object>) gson.fromJson(
          remoteResult.getConfig(), new TypeToken<Map<String, Object>>() {
          }.getType());
      context.getConfig().clear();
      context.getConfig().putAll(remoteConfig);

      if (form == FormType.NATIVE) {
        GUI remoteGui = gson.fromJson(remoteResult.getGui(), GUI.class);
        context.getGui().clear();
        context.getGui().setParams(remoteGui.getParams());
        context.getGui().setForms(remoteGui.getForms());
      }

      return convert(remoteResult);
    } catch (TException e) {
      throw new InterpreterException(e);
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
      throw new InterpreterException(e);
    } finally {
      interpreterProcess.releaseClient(client);
    }
  }


  @Override
  public FormType getFormType() {
    if (formType != null) {
      return formType;
    }

    RemoteInterpreterProcess interpreterProcess = getInterpreterProcess();
    Client client = null;
    try {
      client = interpreterProcess.getClient();
    } catch (Exception e1) {
      throw new InterpreterException(e1);
    }

    try {
      formType = FormType.valueOf(client.getFormType(className));
      return formType;
    } catch (TException e) {
      throw new InterpreterException(e);
    } finally {
      interpreterProcess.releaseClient(client);
    }
  }

  @Override
  public int getProgress(InterpreterContext context) {
    RemoteInterpreterProcess interpreterProcess = getInterpreterProcess();
    Client client = null;
    try {
      client = interpreterProcess.getClient();
    } catch (Exception e1) {
      throw new InterpreterException(e1);
    }

    try {
      return client.getProgress(className, convert(context));
    } catch (TException e) {
      throw new InterpreterException(e);
    } finally {
      interpreterProcess.releaseClient(client);
    }
  }


  @Override
  public List<String> completion(String buf, int cursor) {
    RemoteInterpreterProcess interpreterProcess = getInterpreterProcess();
    Client client = null;
    try {
      client = interpreterProcess.getClient();
    } catch (Exception e1) {
      throw new InterpreterException(e1);
    }

    try {
      return client.completion(className, buf, cursor);
    } catch (TException e) {
      throw new InterpreterException(e);
    } finally {
      interpreterProcess.releaseClient(client);
    }
  }

  @Override
  public Scheduler getScheduler() {
    int maxConcurrency = 10;

    return SchedulerFactory.singleton().createOrGetRemoteScheduler(
        "remoteinterpreter_" + this.hashCode(), getInterpreterProcess(), maxConcurrency);
  }

  @Override
  public void setInterpreterGroup(InterpreterGroup interpreterGroup) {
    super.setInterpreterGroup(interpreterGroup);

    synchronized (interpreterGroupReference) {
      if (!interpreterGroupReference
          .containsKey(getInterpreterGroupKey(interpreterGroup))) {
        interpreterGroupReference.put(getInterpreterGroupKey(interpreterGroup),
            new RemoteInterpreterProcess(interpreterRunner,
                interpreterPath, env));

        logger.info("setInterpreterGroup = "
            + getInterpreterGroupKey(interpreterGroup) + " class=" + className
            + ", path=" + interpreterPath);
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
        Type.valueOf(result.getType()),
        result.getMsg());
  }
}
