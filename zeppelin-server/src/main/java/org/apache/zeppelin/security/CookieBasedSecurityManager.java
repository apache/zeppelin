package org.apache.zeppelin.security;

import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.subject.SubjectContext;
import org.apache.shiro.web.mgt.DefaultWebSecurityManager;

/**
 * A Security Manager implementing a slightly more relaxed behavior around "remember me" cookies
 */
public class CookieBasedSecurityManager extends DefaultWebSecurityManager {

  @Override
  public Subject createSubject(SubjectContext subjectContext) {
    Subject subject = super.createSubject(subjectContext);
    if (subject.isRemembered()) {
      return createSubject(
        subjectContext.getAuthenticationToken(),
        subjectContext.getAuthenticationInfo(),
        subject);
    }
    return subject;
  }
}
