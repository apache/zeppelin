/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.zeppelin.submarine.commons;

import org.apache.commons.lang.StringUtils;
import org.apache.zeppelin.display.AngularObject;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SubmarineUtils {
  private static Logger LOGGER = LoggerFactory.getLogger(SubmarineUI.class);

  public static String unifyKey(String key) {
    key = key.replace(".", "_").toUpperCase();
    return key;
  }

  // 1. yarn application match the pattern [a-z][a-z0-9-]*
  // 2. yarn limit appName can not be greater than 30 characters
  public static String getJobName(String userName, String noteId)
      throws RuntimeException {
    userName = userName.toLowerCase();
    userName = userName.replace("_", "-");
    userName = userName.replace(".", "-");

    noteId = noteId.toLowerCase();
    noteId = noteId.replace("_", "-");
    noteId = noteId.replace(".", "-");

    String jobName = userName + "-" + noteId;

    String yarnAppPatternString = "[a-z][a-z0-9-]*";
    Pattern pattern = Pattern.compile(yarnAppPatternString);
    Matcher matcher = pattern.matcher(jobName);
    boolean matches = matcher.matches();
    if (false == matches) {
      throw new RuntimeException("Job Name(`noteName`-`noteId`) " +
          "does not matcher the `[a-z][a-z0-9-]*` Pattern!");
    }

    if (jobName.length() > 30) {
      throw new RuntimeException("Job Name can not be greater than 30 characters");
    }

    return jobName;
  }

  // 1. Yarn application match the pattern [a-z][a-z0-9-]*
  // 2. Yarn registry dns Hostname can not be greater than 64 characters,
  //    The name needs to be short.
  public static String getTensorboardName(String user) {
    return user.toLowerCase() + "-tb";
  }

  public static String getAgulObjValue(InterpreterContext context, String name) {
    String value = "";
    AngularObject angularObject = context.getAngularObjectRegistry()
        .get(name, context.getNoteId(), context.getParagraphId());
    if (null != angularObject && null != angularObject.get()) {
      value = angularObject.get().toString();
    }
    return value;
  }

  public static void setAgulObjValue(InterpreterContext context, String name, Object value) {
    AngularObject angularObject = context.getAngularObjectRegistry()
        .add(name, value, context.getNoteId(), context.getParagraphId(), true);
  }

  public static void removeAgulObjValue(InterpreterContext context, String name) {
    context.getAngularObjectRegistry().remove(name, context.getNoteId(),
        context.getParagraphId(), true);
  }

  private static String getProperty(Properties properties, String key,
                                    boolean outputLog, StringBuffer sbMessage) {
    String value = properties.getProperty(key, "");
    if (StringUtils.isEmpty(value) && outputLog) {
      sbMessage.append("EXECUTE_SUBMARINE_ERROR: " +
          "Please set the submarine interpreter properties : ");
      sbMessage.append(key).append("\n");
    }

    return value;
  }
}
