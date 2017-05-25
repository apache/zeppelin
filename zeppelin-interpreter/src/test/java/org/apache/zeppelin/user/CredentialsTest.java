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

package org.apache.zeppelin.user;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CredentialsTest {

  @Test
  public void testDefaultProperty() throws IOException {
    Credentials credentials = new Credentials(false, null);
    UserCredentials userCredentials = new UserCredentials();
    UsernamePassword up1 = new UsernamePassword("user2", "password");
    userCredentials.putUsernamePassword("hive(vertica)", up1);
    credentials.putUserCredentials("user1", userCredentials);
    UserCredentials uc2 = credentials.getUserCredentials("user1");
    UsernamePassword up2 = uc2.getUsernamePassword("hive(vertica)");
    assertEquals(up1.getUsername(), up2.getUsername());
    assertEquals(up1.getPassword(), up2.getPassword());
  }

  @Test
  public void testPasswordEncoding() throws IOException {
    String password = "some_password";
    String entityName = "entity";
    String user = "user1";
    String encodedPassword = "BhwIF24FEhYBRhoBAQ==";

    Path credentialsPath = Files.createTempFile(null, null);
    Credentials credentials = new Credentials(true, credentialsPath.toAbsolutePath().toString());
    UserCredentials userCredentials = new UserCredentials();
    UsernamePassword usernamePassword = new UsernamePassword(user, password);
    userCredentials.putUsernamePassword(entityName, usernamePassword);
    credentials.putUserCredentials(user, userCredentials);
    credentials.saveCredentials();

    String jsonString = new String(Files.readAllBytes(credentialsPath));
    Credentials loadedCredentials = new Credentials(true, credentialsPath.toAbsolutePath().toString());
    credentialsPath.toFile().delete();

    assertFalse(jsonString.contains(password));
    assertTrue(jsonString.contains(encodedPassword));
    assertEquals(password, loadedCredentials.getUserCredentials(user).getUsernamePassword(entityName).getPassword());
  }

}
