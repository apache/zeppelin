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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Response;
import org.apache.commons.lang3.StringUtils;
import org.apache.zeppelin.annotation.ZeppelinApi;
import org.apache.zeppelin.server.JsonResponse;
import org.apache.zeppelin.service.AuthenticationService;
import org.apache.zeppelin.ticket.TicketContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Zeppelin security rest api endpoint.
 */
@Path("/security")
@Produces("application/json")
@Singleton
public class SecurityRestApi extends AbstractRestApi {
  private static final Logger LOGGER = LoggerFactory.getLogger(SecurityRestApi.class);

  @Inject
  public SecurityRestApi(AuthenticationService authenticationService) {
    super(authenticationService);
  }

  /**
   * Return legacy UI identity metadata.
   * For anonymous access, username is always anonymous. The returned ticket is retained for
   * response compatibility and is not a REST or WebSocket authentication credential.
   *
   * @return 200 response
   */
  @GET
  @Path("ticket")
  @ZeppelinApi
  public Response ticket() {
    String principal = authenticationService.getPrincipal();
    Set<String> roles = authenticationService.getAssociatedRoles();
    // ticket set to anonymous for anonymous user. Simplify testing.
    TicketContainer.Entry ticketEntry;
    if ("anonymous".equals(principal)) {
      ticketEntry = TicketContainer.ANONYMOUS_ENTRY;
    } else {
      ticketEntry = TicketContainer.instance.getTicketEntry(principal, roles);
    }

    Map<String, String> data = new HashMap<>();
    data.put("principal", ticketEntry.getPrincipal());
    data.put("roles", GSON.toJson(ticketEntry.getRoles()));
    data.put("ticket", ticketEntry.getTicket());

    JsonResponse<Map<String, String>> response = new JsonResponse<>(Response.Status.OK, "", data);
    LOGGER.warn("{}", response);
    return response.build();
  }

  /**
   * Get userlist.
   *
   * Returns list of all user from available realms
   *
   * @return 200 response
   */
  @GET
  @Path("userlist/{searchText}")
  public Response getUserList(@PathParam("searchText") final String searchText) {

    final int numUsersToFetch = 5;
    List<String> usersList = authenticationService.getMatchedUsers(searchText, numUsersToFetch);
    List<String> rolesList = authenticationService.getMatchedRoles();

    List<String> autoSuggestUserList = new ArrayList<>();
    List<String> autoSuggestRoleList = new ArrayList<>();
    Collections.sort(usersList);
    Collections.sort(rolesList);
    // List the users whose name starts with the search text first, keeping the alphabetical order
    // within each group. The search text comes from the client, so it must not be compiled as a
    // regular expression here.
    usersList.sort(Comparator.comparing((String user) -> !user.startsWith(searchText)));
    int maxLength = 0;
    for (String user : usersList) {
      if (StringUtils.containsIgnoreCase(user, searchText)) {
        autoSuggestUserList.add(user);
        maxLength++;
      }
      if (maxLength == numUsersToFetch) {
        break;
      }
    }

    for (String role : rolesList) {
      if (StringUtils.containsIgnoreCase(role, searchText)) {
        autoSuggestRoleList.add(role);
      }
    }

    Map<String, List<String>> returnListMap = new HashMap<>();
    returnListMap.put("users", autoSuggestUserList);
    returnListMap.put("roles", autoSuggestRoleList);

    return new JsonResponse<>(Response.Status.OK, "", returnListMap).build();
  }
}
