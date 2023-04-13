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
package org.apache.zeppelin.service.shiro;

import org.apache.shiro.SecurityUtils;
import org.apache.shiro.UnavailableSecurityManagerException;
import org.apache.shiro.mgt.SecurityManager;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.subject.support.SubjectThreadState;
import org.apache.shiro.util.LifecycleUtils;
import org.apache.shiro.util.ThreadState;
import org.junit.AfterClass;

/**
 * Abstract test case enabling Shiro in test environments.
 * Copy&Paste from https://shiro.apache.org/testing.html
 */
public abstract class AbstractShiroTest {

  private static ThreadState subjectThreadState;

  public AbstractShiroTest() {
  }

  /**
   * Allows subclasses to set the currently executing {@link Subject} instance.
   *
   * @param subject the Subject instance
   */
  protected void setSubject(Subject subject) {
    clearSubject();
    subjectThreadState = createThreadState(subject);
    subjectThreadState.bind();
  }

  protected Subject getSubject() {
    return SecurityUtils.getSubject();
  }

  protected ThreadState createThreadState(Subject subject) {
    return new SubjectThreadState(subject);
  }

  /**
   * Clears Shiro's thread state, ensuring the thread remains clean for future test execution.
   */
  protected void clearSubject() {
    doClearSubject();
  }

  private static void doClearSubject() {
    if (subjectThreadState != null) {
      subjectThreadState.clear();
      subjectThreadState = null;
    }
  }

  protected static void setSecurityManager(SecurityManager securityManager) {
    SecurityUtils.setSecurityManager(securityManager);
  }

  protected static SecurityManager getSecurityManager() {
    return SecurityUtils.getSecurityManager();
  }

  @AfterClass
  public static void tearDownShiro() {
    doClearSubject();
    try {
      SecurityManager securityManager = getSecurityManager();
      LifecycleUtils.destroy(securityManager);
    } catch (UnavailableSecurityManagerException e) {
      // we don't care about this when cleaning up the test environment
      // (for example, maybe the subclass is a unit test and it didn't
      // need a SecurityManager instance because it was using only
      // mock Subject instances)
    }
    setSecurityManager(null);
  }
}
