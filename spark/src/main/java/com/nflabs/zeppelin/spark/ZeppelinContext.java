package com.nflabs.zeppelin.spark;

import static scala.collection.JavaConversions.asJavaCollection;
import static scala.collection.JavaConversions.asJavaIterable;

import java.io.PrintStream;
import java.util.Collection;
import java.util.Iterator;

import org.apache.spark.SparkContext;
import org.apache.spark.sql.SQLContext;
import org.apache.spark.sql.SchemaRDD;

import scala.Tuple2;

import com.nflabs.zeppelin.interpreter.Interpreter;
import com.nflabs.zeppelin.interpreter.InterpreterContext;
import com.nflabs.zeppelin.interpreter.InterpreterResult;
import com.nflabs.zeppelin.notebook.Paragraph;
import com.nflabs.zeppelin.notebook.form.Input.ParamOption;
import com.nflabs.zeppelin.notebook.form.Setting;
import com.nflabs.zeppelin.spark.dep.DependencyResolver;

/**
 * Spark context for zeppelin.
 *
 * @author Leemoonsoo
 *
 */
public class ZeppelinContext {
  private DependencyResolver dep;
  private PrintStream out;
  private InterpreterContext interpreterContext;

  public ZeppelinContext(SparkContext sc, SQLContext sql,
      InterpreterContext interpreterContext,
      DependencyResolver dep, PrintStream printStream) {
    this.sc = sc;
    this.sqlContext = sql;
    this.interpreterContext = interpreterContext;
    this.dep = dep;
    this.out = printStream;
  }

  public SparkContext sc;
  public SQLContext sqlContext;
  private Setting form;

  public SchemaRDD sql(String sql) {
    return sqlContext.sql(sql);
  }

  /**
   * Load dependency for interpreter and runtime (driver).
   *
   * @param artifact "group:artifact:version" or file path like "/somepath/your.jar"
   * @throws Exception
   */
  public void load(String artifact) throws Exception {
    dep.load(artifact, false, false);
  }

  /**
   * Load dependency for interpreter and runtime (driver).
   *
   * @param artifact "groupId:artifactId:version" or file path like "/somepath/your.jar"
   * @param recursive load all transitive dependency when it is true
   * @throws Exception
   */
  public void load(String artifact, boolean recursive) throws Exception {
    dep.load(artifact, recursive, false);
  }

  /**
   * Load dependency and it's transitive dependencies for interpreter and runtime (driver).
   *
   * @param artifact "groupId:artifactId:version" or file path like "/somepath/your.jar"
   * @param excludes exclusion list of transitive dependency. list of "groupId:artifactId" string.
   * @throws Exception
   */
  public void load(String artifact, scala.collection.Iterable<String> excludes)
      throws Exception {
    dep.load(artifact, asJavaCollection(excludes), true, false);
  }

  /**
   * Load dependency and it's transitive dependencies for interpreter and runtime (driver).
   *
   * @param artifact "groupId:artifactId:version" or file path like "/somepath/your.jar"
   * @param excludes exclusion list of transitive dependency. list of "groupId:artifactId" string.
   * @throws Exception
   */
  public void load(String artifact, Collection<String> excludes) throws Exception {
    dep.load(artifact, excludes, true, false);
  }

  /**
   * Load dependency for interpreter and runtime, and then add to sparkContext.
   *
   * @param artifact "groupId:artifactId:version" or file path like "/somepath/your.jar"
   * @throws Exception
   */
  public void loadAndDist(String artifact) throws Exception {
    dep.load(artifact, false, true);
  }

  /**
   * Load dependency for interpreter and runtime, and then add to sparkContext
   * @param artifact "groupId:artifactId:version" or file path like "/somepath/your.jar"
   * @param recursive load all transitive dependency when it is true
   * @throws Exception
   */
  public void loadAndDist(String artifact, boolean recursive) throws Exception {
    dep.load(artifact, true, true);
  }

  /**
   * Load dependency and it's transitive dependencies and then add to sparkContext.
   *
   * @param artifact "groupId:artifactId:version" or file path like "/somepath/your.jar"
   * @param excludes exclusion list of transitive dependency. list of "groupId:artifactId" string.
   * @throws Exception
   */
  public void loadAndDist(String artifact,
      scala.collection.Iterable<String> excludes) throws Exception {
    dep.load(artifact, asJavaCollection(excludes), true, true);
  }

  /**
   * Load dependency and it's transitive dependencies and then add to sparkContext.
   *
   * @param artifact "groupId:artifactId:version" or file path like "/somepath/your.jar"
   * @param excludes exclusion list of transitive dependency. list of "groupId:artifactId" string.
   * @throws Exception
   */
  public void loadAndDist(String artifact, Collection<String> excludes)
      throws Exception {
    dep.load(artifact, excludes, true, true);
  }


  /**
   * Add maven repository
   *
   * @param id id of repository ex) oss, local, snapshot
   * @param url url of repository. supported protocol : file, http, https
   */
  public void addMavenRepo(String id, String url) {
    addMavenRepo(id, url, false);
  }

  /**
   * Add maven repository
   *
   * @param id id of repository
   * @param url url of repository. supported protocol : file, http, https
   * @param snapshot true if it is snapshot repository
   */
  public void addMavenRepo(String id, String url, boolean snapshot) {
    dep.addRepo(id, url, snapshot);
  }

  /**
   * Remove maven repository by id
   * @param id id of repository
   */
  public void removeMavenRepo(String id){
    dep.delRepo(id);
  }

  /**
   * Load dependency only interpreter.
   *
   * @param name
   * @return
   */

  public Object input(String name) {
    return input(name, "");
  }

  public Object input(String name, Object defaultValue) {
    return form.input(name, defaultValue);
  }

  public Object select(String name, scala.collection.Iterable<Tuple2<Object, String>> options) {
    return select(name, "", options);
  }

  public Object select(String name, Object defaultValue,
      scala.collection.Iterable<Tuple2<Object, String>> options) {
    int n = options.size();
    ParamOption[] paramOptions = new ParamOption[n];
    Iterator<Tuple2<Object, String>> it = asJavaIterable(options).iterator();

    int i = 0;
    while (it.hasNext()) {
      Tuple2<Object, String> valueAndDisplayValue = it.next();
      paramOptions[i++] = new ParamOption(valueAndDisplayValue._1(), valueAndDisplayValue._2());
    }

    return form.select(name, "", paramOptions);
  }

  public void setFormSetting(Setting o) {
    this.form = o;
  }

  public void run(String lines) {
    String intpName = Paragraph.getRequiredReplName(lines);
    String scriptBody = Paragraph.getScriptBody(lines);
    Interpreter intp = interpreterContext.getParagraph().getRepl(intpName);
    InterpreterResult ret = intp.interpret(scriptBody, interpreterContext);
    if (ret.code() == InterpreterResult.Code.SUCCESS) {
      out.println("%" + ret.type().toString().toLowerCase() + " " + ret.message());
    } else if (ret.code() == InterpreterResult.Code.ERROR) {
      out.println("Error: " + ret.message());
    } else if (ret.code() == InterpreterResult.Code.INCOMPLETE) {
      out.println("Incomplete");
    } else {
      out.println("Unknown error");
    }
  }

  public InterpreterContext getInterpreterContext() {
    return interpreterContext;
  }

  public void setInterpreterContext(InterpreterContext interpreterContext) {
    this.interpreterContext = interpreterContext;
  }

}
