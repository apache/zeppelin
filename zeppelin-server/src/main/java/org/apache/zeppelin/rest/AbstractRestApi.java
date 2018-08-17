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

package org.apache.zeppelin.rest;

import com.google.common.collect.Sets;
import org.apache.zeppelin.service.ServiceContext;
import org.apache.zeppelin.service.SimpleServiceCallback;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.apache.zeppelin.utils.SecurityUtils;

import javax.ws.rs.WebApplicationException;
import java.io.IOException;
import java.util.Set;

public class AbstractRestApi {

  protected ServiceContext getServiceContext() {
    AuthenticationInfo authInfo = new AuthenticationInfo(SecurityUtils.getPrincipal());
    Set<String> userAndRoles = Sets.newHashSet();
    userAndRoles.add(SecurityUtils.getPrincipal());
    userAndRoles.addAll(SecurityUtils.getAssociatedRoles());
    return new ServiceContext(authInfo, userAndRoles);
  }

  public static class RestServiceCallback<T> extends SimpleServiceCallback<T> {

    @Override
    public void onFailure(Exception ex, ServiceContext context) throws IOException {
      super.onFailure(ex, context);
      if (ex instanceof WebApplicationException) {
        throw (WebApplicationException) ex;
      } else {
        throw new IOException(ex);
      }
    }
  }
}
