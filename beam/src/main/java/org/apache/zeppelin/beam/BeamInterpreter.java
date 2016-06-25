package org.apache.zeppelin.beam;


import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import org.apache.beam.examples.MinimalWordCount;
import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterPropertyBuilder;
import org.apache.zeppelin.interpreter.InterpreterResult;

import com.google.gson.Gson;

/**
*
*/
public class BeamInterpreter extends Interpreter {

  private String host = "http://localhost:8001";
  private InterpreterContext context;

  public BeamInterpreter(Properties property) {
    super(property);
  }

  static {
    Interpreter.register("beam", "beam", BeamInterpreter.class.getName(),
        new InterpreterPropertyBuilder().build());
  }

  public static void main(String[] args) {

  }

  @Override
  public void open() {

  }

  @Override
  public void close() {

  }

  @Override
  public InterpreterResult interpret(String st, InterpreterContext context) {

    String uuid = "C" + UUID.randomUUID().toString().replace("-", "");

    try {
      String msg = CompileSourceInMemory.execute(uuid, st);
      return new InterpreterResult(InterpreterResult.Code.SUCCESS, msg);
    } catch (Exception e) {
      e.printStackTrace();
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
  public List<String> completion(String buf, int cursor) {
    return null;
  }

}
