package org.apache.zeppelin.service;

import java.util.Collection;
import java.util.Set;

public interface SecurityService {

  String getPrincipal();

  Set<String> getAssociatedRoles();

  Collection getRealmsList();

  boolean isAuthenticated();
}
