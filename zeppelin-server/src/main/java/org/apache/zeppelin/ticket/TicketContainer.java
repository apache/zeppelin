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

package org.apache.zeppelin.ticket;

import java.util.Calendar;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Legacy UI identity metadata retained for response compatibility.
 *
 * <p>Tickets from this container are not authentication credentials for REST or WebSocket.
 * Both transports are authenticated by the Shiro session. No cleanup is done because the same
 * user across different devices shares one legacy entry, so the map is bounded by user names.
 */


public class TicketContainer {

  private static final Logger LOGGER = LoggerFactory.getLogger(TicketContainer.class);

  public static final Entry ANONYMOUS_ENTRY = new Entry("anonymous", "anonymous", new HashSet<>());

  public static class Entry {
    private final String ticket;
    private final String principal;
    private final Set<String> roles;

    // lastAccessTime still unused
    public final long lastAccessTime;

    Entry(String ticket, String principal, Set<String> roles) {
      this.ticket = ticket;
      this.principal = principal;
      this.roles = roles;
      this.lastAccessTime = Calendar.getInstance().getTimeInMillis();
    }

    public String getTicket() {
      return ticket;
    }

    public String getPrincipal() {
      return principal;
    }

    public Set<String> getRoles() {
      return roles;
    }
  }

  private Map<String, Entry> sessions = new ConcurrentHashMap<>();

  public static final TicketContainer instance = new TicketContainer();

  /**
   * For test use
   * @param principal
   * @param ticket
   * @return true if ticket assigned to principal.
   */
  public boolean isValid(String principal, String ticket) {
    if ("anonymous".equals(principal) && "anonymous".equals(ticket))
      return true;
    Entry entry = sessions.get(principal);
    return entry != null && entry.ticket.equals(ticket);
  }

  /**
   * Get or create legacy response metadata for an authenticated Shiro user.
   * For an unauthenticated user (anonymous), always return ticket value "anonymous".
   * @param principal
   * @return
   */
  public synchronized Entry getTicketEntry(String principal, Set<String> roles) {
    Entry entry = sessions.get(principal);
    if (entry == null) {
      String ticket;
      if (principal.equals("anonymous")) {
        ticket = "anonymous";
      } else {
        ticket = UUID.randomUUID().toString();
      }
      entry = new Entry(ticket, principal, roles);
      sessions.put(principal, entry);
    }
    return entry;
  }

  public synchronized String getTicket(String principal, Set<String> roles) {
    Entry entry = sessions.get(principal);
    String ticket;
    if (entry == null) {
      if (principal.equals("anonymous"))
        ticket = "anonymous";
      else
        ticket = UUID.randomUUID().toString();
    } else {
      ticket = entry.ticket;
    }
    entry = new Entry(ticket, principal, roles);
    sessions.put(principal, entry);
    return ticket;
  }

  public Entry getTicketEntry(String ticket) {
    if ("anonymous".equals(ticket)) {
      return ANONYMOUS_ENTRY;
    }
    return sessions.get(ticket);
  }

  /**
   * Remove ticket from session cache.
   * @param principal
   */
  public synchronized void removeTicket(String principal) {
    try {
      if (sessions.get(principal) != null) {
        sessions.remove(principal);
      }
    } catch (Exception e) {
      LOGGER.error("Error removing ticket", e);
    }
  }
}
