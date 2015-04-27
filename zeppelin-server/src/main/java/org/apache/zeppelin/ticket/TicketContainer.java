package org.apache.zeppelin.ticket;

import java.util.Calendar;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by hayssams on 24/04/15.
 * Very simple ticket container
 * No cleanup is done, since the same user accross different devices share the same ticket
 * The Map size is at most the number of different user names having access to a Zeppelin instance
 */


public class TicketContainer {
  private static class Entry {
    public final String ticket;
    // lastAccessTime still unused
    public final long lastAccessTime;
    Entry(String ticket) {
      this.ticket = ticket;
      this.lastAccessTime = Calendar.getInstance().getTimeInMillis();
    }
  }
  private Map<String, Entry> sessions = new ConcurrentHashMap<>();

  public static final TicketContainer instance = new TicketContainer();

  public boolean isValid(String principal, String ticket) {
    Entry entry = sessions.get(principal);
    return entry != null && entry.ticket.equals(ticket);
  }

  public synchronized String getTicket(String principal) {
    Entry entry = sessions.get(principal);
    String ticket;
    if (entry == null) {
      if (principal.equals("anonymous"))
        ticket = "anonymous"; // enable testing on anonymous when ticket is required in the url
      else
        ticket = UUID.randomUUID().toString();
    }
    else {
      ticket = entry.ticket;
    }
    entry = new Entry(ticket);
    sessions.put(principal, entry);
    return ticket;
  }
}
