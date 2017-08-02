package org.apache.zeppelin.utils;

import org.apache.shiro.subject.Subject;
import org.apache.shiro.web.filter.authz.RolesAuthorizationFilter;

import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import java.io.IOException;

/**
 * Allows access if current user has at least one role of the specified list.
 * <p>
 * Basically, it's the same as {@link RolesAuthorizationFilter} but using {@literal OR} instead
 * of {@literal AND} on the specified roles.
 */
public class AnyOfRolesAuthorizationFilter extends RolesAuthorizationFilter {

  @Override
  public boolean isAccessAllowed(ServletRequest request, ServletResponse response,
                                 Object mappedValue) throws IOException {

    final Subject subject = getSubject(request, response);
    final String[] rolesArray = (String[]) mappedValue;

    if (rolesArray == null || rolesArray.length == 0) {
      //no roles specified, so nothing to check - allow access.
      return true;
    }

    for (String roleName : rolesArray) {
      if (subject.hasRole(roleName)) {
        return true;
      }
    }
    return false;
  }
}
