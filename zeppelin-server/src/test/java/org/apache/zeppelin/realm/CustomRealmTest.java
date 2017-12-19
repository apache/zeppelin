package org.apache.zeppelin.realm;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.SimpleAuthenticationInfo;
import org.apache.shiro.authz.AuthorizationInfo;
import org.apache.shiro.authz.SimpleAuthorizationInfo;
import org.apache.shiro.realm.AuthorizingRealm;
import org.apache.shiro.subject.PrincipalCollection;
import org.apache.shiro.subject.SimplePrincipalCollection;
import org.apache.shiro.util.CollectionUtils;
import org.apache.shiro.util.StringUtils;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CustomRealmTest {

	@Test
	public void testUsers() {

		TestRealm testRealm = new TestRealm();
		PrincipalCollection principals = testRealm.doGetAuthenticationInfo(null).getPrincipals();
		assertEquals("[role1, role2]", testRealm.doGetAuthorizationInfo(principals).getRoles().toString());
	}

	public class TestRealm extends AuthorizingRealm {

		private final Logger log = LoggerFactory.getLogger(TestRealm.class);

		@Override
		protected void onInit() {
			super.onInit();
		}

		@Override
		protected AuthenticationInfo doGetAuthenticationInfo(AuthenticationToken token) throws AuthenticationException {
			try {
				String userId = "user";
				Set<String> roles = new HashSet<String>();
				roles.add("role1");
				roles.add("role2");
				Map<String, Object> attributes = new HashMap<String, Object>();
				HashMap<String, String> rolesMap = new LinkedHashMap<String, String>();
				for (String role : roles) {
					rolesMap.put(role, "*");
				}
				attributes.put("roles", rolesMap);
				List<Object> principals = CollectionUtils.asList(userId, attributes);
				PrincipalCollection principalCollection = new SimplePrincipalCollection(principals, getName());
				return new SimpleAuthenticationInfo(principalCollection, null);
			} catch (AuthenticationException e) {
				log.error("Unable to fetch roles for the user ", e);
			}
			return new SimpleAuthenticationInfo();
		}

		@Override
		@SuppressWarnings("unchecked")
		protected AuthorizationInfo doGetAuthorizationInfo(PrincipalCollection principals) {
			SimplePrincipalCollection principalCollection = (SimplePrincipalCollection) principals;
			List<Object> listPrincipals = principalCollection.asList();
			Map<String, Object> attributes = (Map<String, Object>) listPrincipals.get(1);
			SimpleAuthorizationInfo simpleAuthorizationInfo = new SimpleAuthorizationInfo();
			Map<String, String> value = (Map<String, String>) attributes.get("roles");
			for (String key : value.keySet()) {
				addRoles(simpleAuthorizationInfo, split(key));
			}
			return simpleAuthorizationInfo;
		}

		private List<String> split(String s) {
			List<String> list = new ArrayList<String>();
			String[] elements = StringUtils.split(s, ',');
			if (elements != null && elements.length > 0) {
				for (String element : elements) {
					if (StringUtils.hasText(element)) {
						list.add(element.trim());
					}
				}
			}
			return list;
		}

		private void addRoles(SimpleAuthorizationInfo simpleAuthorizationInfo, List<String> roles) {
			for (String role : roles) {
				simpleAuthorizationInfo.addRole(role);
			}
		}

	}
}
