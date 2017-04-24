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


import org.apache.commons.lang3.StringUtils;
import org.apache.shiro.realm.Realm;
import org.apache.shiro.realm.jdbc.JdbcRealm;
import org.apache.shiro.realm.ldap.JndiLdapRealm;
import org.apache.shiro.realm.text.IniRealm;
import org.apache.zeppelin.annotation.ZeppelinApi;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.realm.ActiveDirectoryGroupRealm;
import org.apache.zeppelin.realm.LdapRealm;
import org.apache.zeppelin.server.JsonResponse;
import org.apache.zeppelin.ticket.TicketContainer;
import org.apache.zeppelin.utils.SecurityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;
import java.util.*;

/**
 * Zeppelin security rest api endpoint.
 */
@Path("/security")
@Produces("application/json")
public class SecurityRestApi {
  private static final Logger LOG = LoggerFactory.getLogger(SecurityRestApi.class);

  /**
   * Required by Swagger.
   */
  public SecurityRestApi() {
    super();
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
    ZeppelinConfiguration conf = ZeppelinConfiguration.create();
    String principal = SecurityUtils.getPrincipal();
    HashSet<String> roles = SecurityUtils.getRoles();
    JsonResponse response;
    // ticket set to anonymous for anonymous user. Simplify testing.
    String ticket;
    if ("anonymous".equals(principal))
      ticket = "anonymous";
    else
      ticket = TicketContainer.instance.getTicket(principal);

    Map<String, String> data = new HashMap<>();
    data.put("principal", principal);
    data.put("roles", roles.toString());
    data.put("ticket", ticket);

    response = new JsonResponse(Response.Status.OK, "", data);
    LOG.warn(response.toString());
    return response.build();
  }

  /**
   * Get userlist
   * Returns list of all user from available realms
   *
   * @return 200 response
   */
  @GET
  @Path("userlist/{searchText}")
  public Response getUserList(@PathParam("searchText") final String searchText) {

    List<String> usersList = new ArrayList<>();
    List<String> rolesList = new ArrayList<>();
    try {
      GetUserList getUserListObj = new GetUserList();
      Collection realmsList = SecurityUtils.getRealmsList();
      if (realmsList != null) {
        for (Iterator<Realm> iterator = realmsList.iterator(); iterator.hasNext(); ) {
          Realm realm = iterator.next();
          String name = realm.getClass().getName();
          if (LOG.isDebugEnabled()) {
            LOG.debug("RealmClass.getName: " + name);
          }
          if (name.equals("org.apache.shiro.realm.text.IniRealm")) {
            usersList.addAll(getUserListObj.getUserList((IniRealm) realm));
            rolesList.addAll(getUserListObj.getRolesList((IniRealm) realm));
          } else if (name.equals("org.apache.zeppelin.realm.LdapGroupRealm")) {
            usersList.addAll(getUserListObj.getUserList((JndiLdapRealm) realm, searchText));
          } else if (name.equals("org.apache.zeppelin.realm.LdapRealm")) {
            usersList.addAll(getUserListObj.getUserList((LdapRealm) realm, searchText));
            rolesList.addAll(getUserListObj.getRolesList((LdapRealm) realm));
          } else if (name.equals("org.apache.zeppelin.realm.ActiveDirectoryGroupRealm")) {
            usersList.addAll(getUserListObj.getUserList((ActiveDirectoryGroupRealm) realm,
                searchText));
          } else if (name.equals("org.apache.shiro.realm.jdbc.JdbcRealm")) {
            usersList.addAll(getUserListObj.getUserList((JdbcRealm) realm));
          }
        }
      }
    } catch (Exception e) {
      LOG.error("Exception in retrieving Users from realms ", e);
    }
    List<String> autoSuggestUserList = new ArrayList<>();
    List<String> autoSuggestRoleList = new ArrayList<>();
    Collections.sort(usersList);
    Collections.sort(rolesList);
    Collections.sort(usersList, new Comparator<String>() {
      @Override
      public int compare(String o1, String o2) {
        if (o1.matches(searchText + "(.*)") && o2.matches(searchText + "(.*)")) {
          return 0;
        } else if (o1.matches(searchText + "(.*)")) {
          return -1;
        }
        return 0;
      }
    });
    int maxLength = 0;
    for (String user : usersList) {
      if (StringUtils.containsIgnoreCase(user, searchText)) {
        autoSuggestUserList.add(user);
        maxLength++;
      }
      if (maxLength == 5) {
        break;
      }
    }

    for (String role : rolesList) {
      if (StringUtils.containsIgnoreCase(role, searchText)) {
        autoSuggestRoleList.add(role);
      }
    }

    Map<String, List> returnListMap = new HashMap<>();
    returnListMap.put("users", autoSuggestUserList);
    returnListMap.put("roles", autoSuggestRoleList);


    return new JsonResponse<>(Response.Status.OK, "", returnListMap).build();
  }

}
