package org.apache.zeppelin.realm;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attributes;
import javax.naming.directory.BasicAttributes;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.LdapContext;

import org.junit.Test;

public class ActiveDirectoryGroupRealmTest {
	
	@Test
	public void testSearchForUserName() throws NamingException {
		final ActiveDirectoryGroupRealm activeDirectoryGroupRealm = new ActiveDirectoryGroupRealm();
		final LdapContext ldapContext = mock(LdapContext.class);
		final NamingEnumeration namingEnumeration = mock(NamingEnumeration.class);
		
		final Attributes attributes_1 = new BasicAttributes(true);
		attributes_1.put("userprincipalname", "bob@testcompany.com");
		attributes_1.put("cn", "Bob | Test Company");
		
		final Attributes attributes_2 = new BasicAttributes(true);
		attributes_2.put("userprincipalname", "peter@testcompany.com");
		attributes_2.put("cn", "Peter | Test Company");
		
		final SearchResult searchResult_1 = new SearchResult("bob@testcompany.com", null, attributes_1);
		final SearchResult searchResult_2 = new SearchResult("peter@testcompany.com", null, attributes_2);
		
		when(ldapContext.search(anyString(), anyString(), any(Object[].class), any(SearchControls.class))).thenReturn(namingEnumeration);
		when(namingEnumeration.hasMoreElements()).thenReturn(Boolean.TRUE).thenReturn(Boolean.TRUE).thenReturn(Boolean.FALSE);
		when(namingEnumeration.next()).thenReturn(searchResult_1).thenReturn(searchResult_2);
		
		final List<String> result = activeDirectoryGroupRealm.searchForUserName("bob", ldapContext, 3);
		
		assertEquals(2, result.size());
		assertEquals("bob@testcompany.com", result.get(0));
		assertEquals("peter@testcompany.com", result.get(1));
	}
}