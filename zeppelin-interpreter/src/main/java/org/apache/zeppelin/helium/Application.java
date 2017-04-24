/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.zeppelin.helium;

import org.apache.zeppelin.annotation.Experimental;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.resource.ResourceSet;

import java.io.IOException;

/**
 * Base class for pluggable application (e.g. visualization)
 * Application can access resources from ResourcePool and interact with front-end using
 * AngularDisplay system
 */
@Experimental
public abstract class Application {

  private final ApplicationContext context;

  public Application(ApplicationContext context) {
    this.context = context;
  }

  public ApplicationContext context() {
    return context;
  }

  /**
   * This method can be invoked multiple times before unload(),
   * Either just after application selected or when paragraph re-run after application load
   */
  @Experimental
  public abstract void run(ResourceSet args)
      throws ApplicationException, IOException;


  /**
   * this method is invoked just before application is removed
   */
  @Experimental
  public abstract void unload() throws ApplicationException;

  /**
   * Print string on the notebook
   * @param string
   * @throws IOException
   */
  @Experimental
  public void print(String string) throws IOException {
    context.out.write(string);
  }

  /**
   * Print string on the notebook with newline
   * @param string
   * @throws IOException
   */
  @Experimental
  public void println(String string) throws IOException {
    print(string + "\n");
  }

  /**
   * Print resource on the notebook
   * @param resourceName
   * @throws IOException
   */
  @Experimental
  public void printResource(String resourceName) throws IOException {
    context.out.writeResource(resourceName);
  }

  /**
   * Print resource as a javascript
   *
   * Using this method does not require print javascript inside of <script></script> tag.
   * Javascript printed using this method will be run in the un-named function.
   * i.e. each method call will creates different variable scope for the javascript code.
   *
   * This method inject '$z' into the variable scope for convenience.
   *
   * $z.scope : angularjs scope object for this application
   * $z.id : unique id for this application instance
   *
   * @param resourceName
   * @throws IOException
   */
  @Experimental
  public void printResourceAsJavascript(String resourceName) throws IOException {
    beginJavascript();
    context.out.writeResource(resourceName);
    endJavascript();
  }

  /**
   * Print string as a javascript
   *
   * Using this method does not require print javascript inside of <script></script> tag.
   * Javascript printed using this method will be run in the un-named function.
   * i.e. each method call will creates different variable scope for the javascript code.
   *
   * This method inject '$z' into the variable scope for convenience.
   *
   * $z.scope : angularjs scope object for this application
   * $z.id : unique id for this application instance
   *
   * @param js
   * @throws IOException
   */
  @Experimental
  public void printStringAsJavascript(String js) throws IOException {
    beginJavascript();
    context.out.write(js);
    endJavascript();
  }

  private void beginJavascript() throws IOException {
    StringBuffer js = new StringBuffer();
    js.append("\n<script id=\"app_js_" + js.hashCode() + "\">\n");
    js.append("(function() {\n");
    js.append("let $z = {\n");
    js.append("id : \"" + context.getApplicationInstanceId() + "\",\n");
    js.append("scope : angular.element(\"#app_js_" + js.hashCode() + "\").scope()\n");
    js.append("};\n");
    js.append("$z.result = ($z.scope._devmodeResult) ? " +
        "$z.scope._devmodeResult : $z.scope.$parent.paragraph.result;\n");
    context.out.write(js.toString());
  }

  private void endJavascript() throws IOException {
    StringBuffer js = new StringBuffer();
    js.append("\n})();\n");
    js.append("</script>\n");
    context.out.write(js.toString());
  }
}
