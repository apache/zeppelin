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

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;

import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.realm.Realm;
import org.apache.shiro.subject.Subject;
import org.apache.zeppelin.annotation.ZeppelinApi;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.notebook.AuthorizationService;
import org.apache.zeppelin.realm.ExternalLoginRealm;
import org.apache.zeppelin.server.JsonResponse;
import org.apache.zeppelin.service.AuthenticationService;
import org.apache.zeppelin.ticket.TicketContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created for org.apache.zeppelin.rest.message.
 */
@Path("/login")
@Produces("application/json")
@Singleton
public class LoginRestApi extends AbstractRestApi {
  private static final Logger LOGGER = LoggerFactory.getLogger(LoginRestApi.class);
  private final ZeppelinConfiguration zConf;

  private final AuthorizationService authorizationService;

  @Inject
  public LoginRestApi(ZeppelinConfiguration zConf,
                      AuthenticationService authenticationService,
                      AuthorizationService authorizationService) {
    super(authenticationService);
    this.zConf = zConf;
    this.authorizationService = authorizationService;
  }

  @GET
  @ZeppelinApi
  public Response getLogin(@Context HttpHeaders headers) {
    JsonResponse<Map<String, String>> response = null;
    ExternalLoginRealm externalLoginRealm = getExternalLoginRealm();
    if (externalLoginRealm != null) {
      try {
        AuthenticationToken token =
            externalLoginRealm.getLoginAuthenticationToken(headers.getCookies());
        if (token != null) {
          String name = externalLoginRealm.getLoginPrincipal(token);
          Subject currentUser = SecurityUtils.getSubject();
          if (!currentUser.isAuthenticated() || !currentUser.getPrincipal().equals(name)) {
            response = proceedToLogin(currentUser, token);
          }
        }
      } catch (AuthenticationException e) {
        LOGGER.error("Error while processing an external login token", e);
      }

      if (response == null && externalLoginRealm.shouldRedirectOnMissingToken()) {
        Map<String, String> data = new HashMap<>();
        data.put("redirectURL",
            constructUrl(externalLoginRealm.getProviderUrl(),
                externalLoginRealm.getRedirectParam(), externalLoginRealm.getLogin()));
        response = new JsonResponse<>(Status.OK, "", data);
      }
      if (response == null) {
        LOGGER.warn("No external authentication token received");
        response = new JsonResponse<>(Status.UNAUTHORIZED, "", null);
      }
      return response.build();
    }
    return new JsonResponse<>(Status.METHOD_NOT_ALLOWED).build();
  }

  private ExternalLoginRealm getExternalLoginRealm() {
    ExternalLoginRealm selectedRealm = null;
    Collection<Realm> realmsList = authenticationService.getRealmsList();
    if (realmsList != null) {
      for (Realm realm : realmsList) {
        LOGGER.debug("RealmClass.getName: {}", realm.getClass().getName());
        if (realm instanceof ExternalLoginRealm
            && (selectedRealm == null
                || ((ExternalLoginRealm) realm).getLoginPriority()
                    > selectedRealm.getLoginPriority())) {
          selectedRealm = (ExternalLoginRealm) realm;
        }
      }
    }
    return selectedRealm;
  }

  private JsonResponse<Map<String, String>> proceedToLogin(
      Subject currentUser, AuthenticationToken token) {
    JsonResponse<Map<String, String>> response = null;
    try {
      logoutCurrentUser();
      currentUser.getSession(true);
      currentUser.login(token);

      Set<String> roles = authenticationService.getAssociatedRoles();
      String principal = authenticationService.getPrincipal();
      TicketContainer.Entry ticketEntry = "anonymous".equals(principal) ?
              TicketContainer.ANONYMOUS_ENTRY : TicketContainer.instance.getTicketEntry(principal, roles);

      Map<String, String> data = new HashMap<>();
      data.put("principal", ticketEntry.getPrincipal());
      data.put("roles", GSON.toJson(ticketEntry.getRoles()));
      data.put("ticket", ticketEntry.getTicket());

      response = new JsonResponse<>(Status.OK, "", data);
      // if no exception, that's it, we're done!

      // set roles for user in NotebookAuthorization module
      authorizationService.setRoles(principal, roles);
    } catch (AuthenticationException uae) {
      // username wasn't in the system, show them an error message?
      // password didn't match, try again?
      // account for that username is locked - can't login.  Show them a message?
      // unexpected condition - error?
      LOGGER.error("Exception in login: ", uae);
    }
    return response;
  }

  /**
   * Post Login
   * Returns userName & password
   * for anonymous access, username is always anonymous.
   * After getting this ticket, access through websockets become safe
   *
   * @return 200 response
   */
  @POST
  @ZeppelinApi
  public Response postLogin(@FormParam("userName") String userName,
      @FormParam("password") String password) {
    LOGGER.debug("userName: {}", userName);
    // ticket set to anonymous for anonymous user. Simplify testing.
    Subject currentUser = SecurityUtils.getSubject();
    if (currentUser.isAuthenticated()) {
      currentUser.logout();
    }
    LOGGER.debug("currentUser: {}", currentUser);
    JsonResponse<Map<String, String>> response = null;
    if (!currentUser.isAuthenticated()) {

      UsernamePasswordToken token = new UsernamePasswordToken(userName, password);

      response = proceedToLogin(currentUser, token);
    }

    if (response == null) {
      response = new JsonResponse<>(Response.Status.FORBIDDEN, "", null);
    }

    LOGGER.info(response.toString());
    return response.build();
  }

  @POST
  @Path("logout")
  @ZeppelinApi
  public Response logout() {
    logoutCurrentUser();
    Status status;
    Map<String, String> data = new HashMap<>();
    if (zConf.isAuthorizationHeaderClear()) {
      status = Status.UNAUTHORIZED;
      data.put("clearAuthorizationHeader", "true");
    } else {
      status = Status.FORBIDDEN;
      data.put("clearAuthorizationHeader", "false");
    }
    ExternalLoginRealm externalLoginRealm = getExternalLoginRealm();
    if (externalLoginRealm != null) {
      data.put("redirectURL",
          constructUrl(externalLoginRealm.getProviderUrl(), externalLoginRealm.getRedirectParam(),
              externalLoginRealm.getLogout()));
      data.put("isLogoutAPI", externalLoginRealm.getLogoutAPI().toString());
    }
    JsonResponse<Map<String, String>> response = new JsonResponse<>(status, "", data);
    LOGGER.info(response.toString());
    return response.build();
  }

  private String constructUrl(String providerURL, String redirectParam,
      String path) {
    StringBuilder redirectURL = new StringBuilder(providerURL);
    redirectURL.append(path);
    if (redirectParam != null) {
      redirectURL.append("?").append(redirectParam).append("=");
    }
    return redirectURL.toString();
  }

  private void logoutCurrentUser() {
    Subject currentUser = SecurityUtils.getSubject();
    TicketContainer.instance.removeTicket(authenticationService.getPrincipal());
    currentUser.getSession().stop();
    currentUser.logout();
  }
}
