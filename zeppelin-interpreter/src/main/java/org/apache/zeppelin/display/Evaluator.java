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

package org.apache.zeppelin.display;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.jexl2.Expression;
import org.apache.commons.jexl2.JexlContext;
import org.apache.commons.jexl2.JexlEngine;
import org.apache.commons.jexl2.MapContext;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The evaluator helper class.
 *
 * This class is initialized using the fully qualified name of an
 * utility class having one or more static methods.
 *
 * Example:com.company.custom.udf.UDFUtility
 *
 * Utility class is resolved using ZEPPELIN_UTILITY_CLASS nv variable or
 * 'zeppelin.utility.class' JVM property.
 *
 * When an expression of the type: "eval:doSomething(...)" is passed from Zeppelin,
 * the Evaluator class tries to resolve the something(...) method in the utility class provided
 * at initialization time.
 *
 * Passing an empty utility class at initialization time also works, but then it is mandatory
 * to use the fully qualified name when writing the expression in Zeppelin.
 *
 * Example: eval:com.company.custom.udf.UDFUtility.doSomething(...)
 *
 * The command coming from Zeppelin notebook is evaluated using 'commons-jexl'.
 *
 */
public class Evaluator {

  public static final String EVAL_PREFIX = "eval:";
  private static final int EVAL_PREFIX_LENGTH = EVAL_PREFIX.length();
  private static Pattern REGEX = Pattern.compile("(?<clazz>.+\\..+)\\.(?<method>.+)");


  private static Logger LOG = LoggerFactory.getLogger(Evaluator.class);
  Class utilityClass;

  /**
   * Constructs an evaluator class given an user-defined Utility class fully qualified name.
   *
   * @param classImpl Utility class containing.
   * @throws ClassNotFoundException if utility class is not in the classpath.
   */
  public Evaluator(String classImpl) throws ClassNotFoundException {
    if (StringUtils.isEmpty(classImpl))
      LOG.debug("Only full qualified expressions will be executed... Be careful!");
    else
      this.utilityClass = Class.forName(classImpl);
  }
  
  /**
   * Evaluates the given command coming verbatim from Zeppelin notebook.
   *
   * The command coming from Zeppelin notebook is evaluated using 'commons-jexl'.
   *
   * @param command expression to eval
   * @return the result of
   */
  public Object eval(String command) throws UnsupportedOperationException {

    Object obj = null;
    
    // Check if expression has to be evaluated.
    if (!command.startsWith(EVAL_PREFIX)) {
      return command;
    }
    
    String expressionToEval = command.substring(EVAL_PREFIX_LENGTH);

    try {
      Map<String, String> mapFQN = getFQN(expressionToEval);
      
      // First, we try with fully qualified name
      JexlEngine jexl = new JexlEngine();
      String expression = "utils." + mapFQN.get("method");
      Expression expr = jexl.createExpression(expression);
      JexlContext jc = new MapContext();
    
      jc.set("utils", Class.forName(mapFQN.get("clazz")));
      obj = expr.evaluate(jc);   
      if (obj != null) 
        return obj;
    } catch (Exception e) {
      LOG.debug("Error trying to use the FQN class.");
    }

    // If it fails, we apply the default utility class
    LOG.debug("Trying the default utility class");
    try {
      JexlEngine jexl = new JexlEngine();
      String expression = "utils." + expressionToEval;
      Expression expr = jexl.createExpression(expression);
      JexlContext jc = new MapContext();
      jc.set("utils", utilityClass);

      obj = expr.evaluate(jc);
    } catch (Exception e) {
      LOG.debug("Error using configured utility class");
      throw new UnsupportedOperationException("Could not evaluate expression.");
    }

    if (obj == null)
      throw new UnsupportedOperationException("Could not evaluate expression.");
    
    return obj;
  }
  
  /**
   * This method extracts the class and the method with arguments from a fully qualified class.
   *
   * @param function FQN class to interpret
   * @return Map with both clazz and method to execute
   */
  private static HashMap<String, String> getFQN(String function) {
    HashMap<String, String> map = new HashMap<>();
    Matcher matcher = REGEX.matcher(function);
    
    matcher.find();
    
    String matcherClazz = matcher.group("clazz");
    String matcherMethod = matcher.group("method");
    
    map.put("clazz", matcherClazz);
    map.put("method", matcherMethod);
    
    return map;
  }

}
