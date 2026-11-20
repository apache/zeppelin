/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.apache.zeppelin.realm;

import org.apache.shiro.config.Ini;
import org.apache.shiro.config.ogdl.ReflectionBuilder;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RoleMappingLdapRealmTest {
  @Test
  public void testGetRolesByUsername() {
    RoleMappingLdapRealm realm = new RoleMappingLdapRealm();
    LinkedHashMap<String, String> rolesByUsername = new LinkedHashMap<>();
    rolesByUsername.put("admin", "admin");
    rolesByUsername.put("user1", "user");
    rolesByUsername.put("user2", "user");
    realm.setRolesByUsername(rolesByUsername);
    realm.setDefaultRole("guest");

    assertTrue(realm.getRoleNamesForUser("admin", null, null).contains("admin"));
    assertTrue(realm.getRoleNamesForUser("user1", null, null).contains("user"));
    assertTrue(realm.getRoleNamesForUser("user2", null, null).contains("user"));
    assertTrue(realm.getRoleNamesForUser("user3", null, null).contains("guest"));
  }

  @Test
  public void testGetRolesByUsernameWithMultipleRoles() {
    RoleMappingLdapRealm realm = new RoleMappingLdapRealm();
    LinkedHashMap<String, String> rolesByUsername = new LinkedHashMap<>();
    rolesByUsername.put("admin", "admin,role1");
    rolesByUsername.put("user1", "role1,role2");
    rolesByUsername.put("user2", "role2");
    realm.setRolesByUsername(rolesByUsername);
    realm.setDefaultRole("guest1,guest2");

    assertTrue(realm.getRoleNamesForUser("admin", null, null).contains("admin"));
    assertTrue(realm.getRoleNamesForUser("admin", null, null).contains("role1"));
    assertTrue(realm.getRoleNamesForUser("user1", null, null).contains("role1"));
    assertTrue(realm.getRoleNamesForUser("user1", null, null).contains("role2"));
    assertTrue(realm.getRoleNamesForUser("user2", null, null).contains("role2"));
    assertTrue(realm.getRoleNamesForUser("user3", null, null).contains("guest1"));
    assertTrue(realm.getRoleNamesForUser("user3", null, null).contains("guest2"));
  }

  @Test
  public void testDefaultRoleWithQuotes() {
    RoleMappingLdapRealm realm = new RoleMappingLdapRealm();
    realm.setDefaultRole("\"guest1,guest2\"");

    Set<String> roleNames = realm.getRoleNamesForUser("user3", null, null);

    assertEquals(Set.of("guest1", "guest2"), roleNames);
  }

  @Test
  public void testDefaultRoleWithIndividuallyQuotedRoles() {
    RoleMappingLdapRealm realm = new RoleMappingLdapRealm();
    realm.setDefaultRole("\"guest1\",\"guest2\"");

    Set<String> roleNames = realm.getRoleNamesForUser("user3", null, null);

    assertEquals(Set.of("guest1", "guest2"), roleNames);
  }

  @Test
  public void testRolesByUsernameWithQuotedRoles() {
    RoleMappingLdapRealm realm = new RoleMappingLdapRealm();
    Map<String, String> rolesByUsername = new LinkedHashMap<>();
    rolesByUsername.put("user1", "\"role1,role2\"");
    realm.setRolesByUsername(rolesByUsername);

    Set<String> roleNames = realm.getRoleNamesForUser("user1", null, null);

    assertEquals(Set.of("role1", "role2"), roleNames);
  }

  @Test
  public void testQuotedRolesParsedFromIni() {
    Ini ini = new Ini();
    ini.load("ldapRealm = org.apache.zeppelin.realm.RoleMappingLdapRealm\n"
        + "ldapRealm.defaultRole = \"role1,role2\"\n"
        + "ldapRealm.rolesByUsername = \"user1\":\"admin\",\"user2\":\"role1,role2\"\n");
    Map<String, ?> objects = new ReflectionBuilder().buildObjects(
        ini.getSection(Ini.DEFAULT_SECTION_NAME));
    RoleMappingLdapRealm realm = (RoleMappingLdapRealm) objects.get("ldapRealm");

    assertEquals(Set.of("admin"), realm.getRoleNamesForUser("user1", null, null));
    assertEquals(Set.of("role1", "role2"), realm.getRoleNamesForUser("user2", null, null));
    assertEquals(Set.of("role1", "role2"), realm.getRoleNamesForUser("user3", null, null));
  }
}
