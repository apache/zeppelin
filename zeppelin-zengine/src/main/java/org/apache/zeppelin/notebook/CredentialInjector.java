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
package org.apache.zeppelin.notebook;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResultMessage;
import org.apache.zeppelin.user.UserCredentials;
import org.apache.zeppelin.user.UsernamePassword;

/**
 * Class for replacing {user.&gt;credentialkey&lt;} and
 * {password.&gt;credentialkey&lt;} tags with the matching credentials from
 * zeppelin
 */
class CredentialInjector {

  private Set<String> passwords = new HashSet<>();
  private final UserCredentials creds;
  private static final Pattern userpattern = Pattern.compile("\\{user\\.([^\\}]+)\\}");
  private static final Pattern passwordpattern = Pattern.compile("\\{password\\.([^\\}]+)\\}");


  public CredentialInjector(UserCredentials creds) {
    this.creds = creds;
  }

  public String replaceCredentials(String code) {
    if (code == null) {
      return null;
    }
    String replaced = code;
    Matcher matcher = userpattern.matcher(replaced);
    while (matcher.find()) {
      String key = matcher.group(1);
      UsernamePassword usernamePassword = creds.getUsernamePassword(key);
      if (usernamePassword != null) {
        String value = usernamePassword.getUsername();
        replaced = matcher.replaceFirst(value);
        matcher = userpattern.matcher(replaced);
      }
    }
    matcher = passwordpattern.matcher(replaced);
    while (matcher.find()) {
      String key = matcher.group(1);
      UsernamePassword usernamePassword = creds.getUsernamePassword(key);
      if (usernamePassword != null) {
        passwords.add(usernamePassword.getPassword());
        String value = usernamePassword.getPassword();
        replaced = matcher.replaceFirst(value);
        matcher = passwordpattern.matcher(replaced);
      }
    }
    return replaced;
  }

  public InterpreterResult hidePasswords(InterpreterResult ret) {
    if (ret == null) {
      return null;
    }
    return new InterpreterResult(ret.code(), replacePasswords(ret.message()));
  }

  private List<InterpreterResultMessage> replacePasswords(List<InterpreterResultMessage> original) {
    List<InterpreterResultMessage> replaced = new ArrayList<>();
    for (InterpreterResultMessage msg : original) {
      switch(msg.getType()) {
        case HTML:
        case TEXT:
        case TABLE: {
          String replacedMessages = replacePasswords(msg.getData());
          replaced.add(new InterpreterResultMessage(msg.getType(), replacedMessages));
          break;
        }
        default:
          replaced.add(msg);
      }
    }
    return replaced;
  }

  private String replacePasswords(String str) {
    String result = str;
    for (String password : passwords) {
      result = result.replace(password, "###");
    }
    return result;
  }

}
