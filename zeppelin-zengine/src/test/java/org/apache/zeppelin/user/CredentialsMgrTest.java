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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;

import org.apache.commons.io.IOUtils;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.storage.ConfigStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

class CredentialsMgrTest {

  private Gson gson;

  @BeforeEach
  void startUp() throws IOException {
    GsonBuilder builder = new GsonBuilder();
    builder.setPrettyPrinting();
    gson = builder.create();
  }

  @Test
  void testDefaultProperty() throws IOException {
    ZeppelinConfiguration zconf = mock(ZeppelinConfiguration.class);
    ConfigStorage storage = mock(ConfigStorage.class);
    when(zconf.credentialsPersist()).thenReturn(false);
    CredentialsMgr credentials = new CredentialsMgr(zconf, storage);
    Credential up1 = new Credential("user2", "password", null, new HashSet<String>(Arrays.asList("user1")));
    credentials.putCredentialsEntity("hive(vertica)", up1);
    credentials.putCredentialsEntity("user1", up1);
    UsernamePasswords uc2 = credentials.getAllUsernamePasswords(new HashSet<String>(Arrays.asList("user1")));
    UsernamePassword up2 = uc2.getUsernamePassword("hive(vertica)");
    assertEquals(up1.getUsername(), up2.getUsername());
    assertEquals(up1.getPassword(), up2.getPassword());
  }

  @Test
  void testCredentialLoad() throws IOException {
    Credentials credentials = new Credentials();
    HashSet<String> reader = new HashSet<>();
    reader.add("reader1");
    HashSet<String> owner = new HashSet<>();
    owner.add("user1");
    owner.add("user2");
    credentials.put("enitity1", new Credential("username", "password", reader, owner));
    credentials.put("enitity2", new Credential("username2", "password2", reader, owner));

    String output = gson.toJson(new CredentialsInfoSaving(credentials));
    assertNotNull(output);
    Credentials creds = CredentialsMgr.parseToCredentials(output, gson);
    assertEquals("username", creds.get("enitity1").getUsername());
    assertEquals("password", creds.get("enitity1").getPassword());
    assertEquals("username2", creds.get("enitity2").getUsername());
    assertEquals("password2", creds.get("enitity2").getPassword());
    // All readers and owners are part of the data structure.
    assertEquals(2, creds.get("enitity1").getOwners().size());
    assertTrue(creds.get("enitity1").getOwners().containsAll(owner));
    assertEquals(1, creds.get("enitity1").getReaders().size());
    assertTrue(creds.get("enitity1").getReaders().containsAll(reader));

    // Generate old output
    output = gson.toJson(new CredentialsInfoSavingOld(CredentialsInfoSavingOldGenerator.generateOldStructure(credentials)));
    assertNotNull(output);
    creds = CredentialsMgr.parseToCredentials(output, gson);
    assertEquals("username", creds.get("enitity1").getUsername());
    assertEquals("password", creds.get("enitity1").getPassword());
    assertEquals("username2", creds.get("enitity2").getUsername());
    assertEquals("password2", creds.get("enitity2").getPassword());
    // readers and owners cannot be transferred to the old data structure.
    assertEquals(1, creds.get("enitity1").getOwners().size());
    assertEquals(1, creds.get("enitity1").getReaders().size());
  }

  @Test
  void testLoading() throws IOException {
    String credentials = IOUtils.toString(CredentialsMgrTest.class.getResourceAsStream("/credentials/credentials.json"), StandardCharsets.UTF_8);
    assertNotNull(credentials);
    Credentials creds = CredentialsMgr.parseToCredentials(credentials, gson);
    assertEquals(2, creds.get("enitity1").getOwners().size());
    assertEquals(1, creds.get("enitity1").getReaders().size());
    assertEquals("user1", creds.get("enitity1").getOwners().iterator().next());
    assertEquals("username", creds.get("enitity1").getUsername());
    assertEquals("password", creds.get("enitity1").getPassword());

    assertEquals(1, creds.get("enitity2").getOwners().size());
    assertEquals(2, creds.get("enitity2").getReaders().size());
    assertEquals("user1", creds.get("enitity2").getOwners().iterator().next());
    assertEquals("username2", creds.get("enitity2").getUsername());
    assertEquals("password2", creds.get("enitity2").getPassword());

  }

  @Test
  void testMigration() throws IOException {
    String credentials_old = IOUtils.toString(CredentialsMgrTest.class.getResourceAsStream("/credentials/credentials_old.json"), StandardCharsets.UTF_8);
    assertNotNull(credentials_old);
    Credentials creds = CredentialsMgr.parseToCredentials(credentials_old, gson);
    assertNotNull(creds);

    assertEquals(1, creds.get("test").getOwners().size());
    assertEquals("user1", creds.get("test").getOwners().iterator().next());
    assertEquals(1, creds.get("test").getReaders().size());
    assertEquals("user1", creds.get("test").getReaders().iterator().next());
    assertEquals("test", creds.get("test").getUsername());
    assertEquals("test", creds.get("test").getPassword());

    assertEquals(1, creds.get("test2").getOwners().size());
    assertEquals("user1", creds.get("test2").getOwners().iterator().next());
    assertEquals(1, creds.get("test2").getReaders().size());
    assertEquals("user1", creds.get("test2").getReaders().iterator().next());
    assertEquals("username", creds.get("test2").getUsername());
    assertEquals("password", creds.get("test2").getPassword());

    assertEquals(1, creds.get("FOO").getOwners().size());
    assertEquals("user2", creds.get("FOO").getOwners().iterator().next());
    assertEquals(1, creds.get("FOO").getReaders().size());
    assertEquals("user2", creds.get("FOO").getReaders().iterator().next());
    assertEquals("2", creds.get("FOO").getUsername());
    assertEquals("1", creds.get("FOO").getPassword());
  }
}
