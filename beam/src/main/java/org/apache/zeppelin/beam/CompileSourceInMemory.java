package org.apache.zeppelin.beam;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaCompiler.CompilationTask;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;


import com.thoughtworks.qdox.JavaProjectBuilder;
import com.thoughtworks.qdox.model.JavaClass;
import com.thoughtworks.qdox.model.JavaSource;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.util.Arrays;
import java.util.List;

/**
 * @author Mahmoud
 *
 */
public class CompileSourceInMemory {
  public static String execute(String className, String code) throws Exception {

    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<JavaFileObject>();

    JavaProjectBuilder builder = new JavaProjectBuilder();
    JavaSource src = builder.addSource(new StringReader(code));

    List<JavaClass> classes = src.getClasses();
    String classMainName = null;
    for (int i = 0; i < classes.size(); i++) {
      boolean hasMain = false;
      for (int j = 0; j < classes.get(i).getMethods().size(); j++) {
        if (classes.get(i).getMethods().get(j).getName().equals("main")) {
          classMainName = classes.get(i).getName();
          hasMain = true;
          break;
        }
      }
      if (hasMain == true)
        break;

    }

    if (classMainName == null)
      throw new Exception("There isn't any class containing Main method.");

    code = code.replace(classMainName, className);

    StringWriter writer = new StringWriter();
    PrintWriter out = new PrintWriter(writer);

    out.println(code);
    out.close();
    


    JavaFileObject file = new JavaSourceFromString(className, writer.toString());
   
    Iterable<? extends JavaFileObject> compilationUnits = Arrays.asList(file);

    ByteArrayOutputStream baosOut = new ByteArrayOutputStream();
    ByteArrayOutputStream baosErr = new ByteArrayOutputStream();

    PrintStream newOut = new PrintStream(baosOut);
    PrintStream newErr = new PrintStream(baosErr);
    // IMPORTANT: Save the old System.out!
    PrintStream oldOut = System.out;
    PrintStream oldErr = System.err;
    // Tell Java to use your special stream
    System.setOut(newOut);
    System.setErr(newErr);

    CompilationTask task = compiler.getTask(null, null, diagnostics, null, null, compilationUnits);

    boolean success = task.call();
    if (!success) {
      for (Diagnostic diagnostic : diagnostics.getDiagnostics()) {
        System.out.println(diagnostic.getMessage(null));
      }
    }
    if (success) {
      try {

        Class.forName(className).getDeclaredMethod("main", new Class[] { String[].class })
        .invoke(null, new Object[] { null });

        System.out.flush();
        System.err.flush();

        System.setOut(oldOut);
        System.setErr(oldErr);


       
        return baosOut.toString();
      } catch (ClassNotFoundException e) {
        e.printStackTrace(newErr);
        System.err.println("Class not found: " + e);
        throw new Exception(baosErr.toString());
      } catch (NoSuchMethodException e) {
        e.printStackTrace(newErr);
        System.err.println("No such method: " + e);
        throw new Exception(baosErr.toString());
      } catch (IllegalAccessException e) {
        e.printStackTrace(newErr);
        System.err.println("Illegal access: " + e);
        throw new Exception(baosErr.toString());
      } catch (InvocationTargetException e) {
        e.printStackTrace(newErr);
        System.err.println("Invocation target: " + e);
        throw new Exception(baosErr.toString());
      }
    } else {
      throw new Exception(baosOut.toString());
    }
  }

}

class JavaSourceFromString extends SimpleJavaFileObject {
  final String code;

  JavaSourceFromString(String name, String code) {
    super(URI.create("string:///" + name.replace('.', '/') + Kind.SOURCE.extension), Kind.SOURCE);
    this.code = code;
  }

  @Override
  public CharSequence getCharContent(boolean ignoreEncodingErrors) {
    return code;
  }
}
