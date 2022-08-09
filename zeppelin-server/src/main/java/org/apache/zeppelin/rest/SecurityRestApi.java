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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;
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
  private static final Logger LOG = LoggerFactory.getLogger(SecurityRestApi.class);

  @Inject
  public SecurityRestApi(AuthenticationService authenticationService) {
    super(authenticationService);
  }

  /**
   * Get ticket
   * Returns username & ticket
   * for anonymous access, username is always anonymous.
   * After getting this ticket, access through websockets become safe
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
    LOG.warn("{}", response);
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
    Collections.sort(
        usersList,
        (o1, o2) -> {
          if (o1.matches(searchText + "(.*)") && o2.matches(searchText + "(.*)")) {
            return 0;
          } else if (o1.matches(searchText + "(.*)")) {
            return -1;
          }
          return 0;
        });
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
