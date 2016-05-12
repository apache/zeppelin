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

import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.resource.ResourceSet;

import java.io.IOException;

/**
 * Zeppelin Application base
 */
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
  public abstract void run(ResourceSet args)
      throws ApplicationException, IOException;


  /**
   * this method is invoked just before application is removed
   */
  public abstract void unload() throws ApplicationException;

  public void print(String string) throws IOException {
    context.out.write(string);
  }

  public void println(String string) throws IOException {
    print(string + "\n");
  }

  public void printResource(String resourceName) throws IOException {
    context.out.writeResource(resourceName);
  }

  public void printResourceAsJavascript(String resourceName) throws IOException {
    beginJavascript();
    context.out.writeResource(resourceName);
    endJavascript();
  }

  public void printStringAsJavascript(String js) throws IOException {
    beginJavascript();
    context.out.write(js);
    endJavascript();
  }

  private void beginJavascript() throws IOException {
    StringBuffer js = new StringBuffer();
    js.append("\n<script id=\"app_js_" + js.hashCode() + "\">\n");
    js.append("(function() {\n");
    js.append("var $z = {\n");
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
