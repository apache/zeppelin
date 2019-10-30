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
package org.apache.zeppelin.configuration;

import org.apache.shiro.config.Ini;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.*;

public class ValidateShiroIniTest {
	protected static final Logger LOG = LoggerFactory.getLogger(ValidateShiroIniTest.class);
	Ini.Section usersSection;
	String activeDirectoryRealm;
	String ldapRealm;

	private static String zeppelinValidRealms =
		"[users]\n" +
			"admin = password1, admin\n" +
			"[main]\n" +
			"sessionManager = org.apache.shiro.web.session.mgt.DefaultWebSessionManager\n" +
			"securityManager.sessionManager = $sessionManager\n" +
			"securityManager.sessionManager.globalSessionTimeout = 86400000\n" +
			"shiro.loginUrl = /api/login\n";

	private static String zeppelinInvalidRealms =
		"[users]\n" +
			"admin = password1, admin\n" +
			"[main]\n" +
			"ldapRealm = org.apache.zeppelin.realm.LdapGroupRealm\n" +
			"sessionManager = org.apache.shiro.web.session.mgt.DefaultWebSessionManager\n" +
			"securityManager.sessionManager = $sessionManager\n" +
			"securityManager.sessionManager.globalSessionTimeout = 86400000\n" +
			"shiro.loginUrl = /api/login\n";

	@Test
	public void testTrueRealms() throws Exception {
		validateShiroIni(zeppelinValidRealms);
		assertEquals(usersSection.size(),1);
		assertNull(activeDirectoryRealm);
		assertNull(ldapRealm);
	}

	@Test (expected = Exception.class)
	public void testFalseRealms() throws Exception {
		validateShiroIni(zeppelinInvalidRealms);
	}

	private void validateShiroIni(String shiroIni) throws Exception {
		Ini ini = new Ini();
		ini.load(shiroIni);

		usersSection = ini.get("users");
		activeDirectoryRealm = ini.getSectionProperty("main", "activeDirectoryRealm");
		ldapRealm = ini.getSectionProperty("main", "ldapRealm");

		if (usersSection != null && (activeDirectoryRealm != null || ldapRealm != null )) {
			throw new Exception(
				"IniRealm/password based auth mechanisms should be exclusive. "
					+ "Consider removing [users] block from shiro.ini");
		}
	}
}

