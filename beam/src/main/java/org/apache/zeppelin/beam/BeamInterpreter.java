package org.apache.zeppelin.beam;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterPropertyBuilder;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.thrift.InterpreterCompletion;

import com.google.gson.Gson;

/**
*
*/
public class BeamInterpreter extends Interpreter {

  public BeamInterpreter(Properties property) {
    super(property);
  }

  @Override
  public void open() {

  }

  @Override
  public void close() {
    File dir = new File(".");
    for (int i = 0; i < dir.list().length; i++) {
      File f = dir.listFiles()[i];
      if (f.getAbsolutePath().contains(".class"))
        f.delete();
    }
  }

  @Override
  public InterpreterResult interpret(String code, InterpreterContext context) {

    String className = "C" + UUID.randomUUID().toString().replace("-", "");

    try {
      String msg = StaticRepl.execute(className, code);
      return new InterpreterResult(InterpreterResult.Code.SUCCESS, msg);
    } catch (Exception e) {
      // e.printStackTrace();
      return new InterpreterResult(InterpreterResult.Code.ERROR, e.getMessage());

    }

  }

  @Override
  public void cancel(InterpreterContext context) {

  }

  @Override
  public FormType getFormType() {
    return FormType.SIMPLE;
  }

  @Override
  public int getProgress(InterpreterContext context) {
    return 0;
  }

  @Override
  public List<InterpreterCompletion> completion(String buf, int cursor) {
    return null;
  }

}
