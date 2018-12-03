package org.apache.zeppelin.service;

import com.google.common.collect.Sets;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;

public class NoSecurityService implements SecurityService {
  private final String ANONYMOUS = "anonymous";

  @Override
  public String getPrincipal() {
    return ANONYMOUS;
  }

  @Override
  public Set<String> getAssociatedRoles() {
    return Sets.newHashSet();
  }

  @Override
  public Collection getRealmsList() {
    return Collections.emptyList();
  }

  @Override
  public boolean isAuthenticated() {
    return false;
  }
}
