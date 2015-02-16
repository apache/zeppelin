package com.nflabs.zeppelin.interpreter.remote;


import java.io.FilePermission;
import java.net.SocketPermission;
import java.security.*;
import java.util.*;

/**
 *
 */
public class RMIPolicy extends Policy {
  private static PermissionCollection perms;

  public RMIPolicy() {
    super();
    if (perms == null) {
      perms = new RMIPermissionCollection();
      addPermissions();
    }
  }

  @Override
  public PermissionCollection getPermissions(CodeSource codesource) {
    return perms;
  }

  private void addPermissions() {
    AllPermission allPermission = new AllPermission();
    perms.add(allPermission);
    /*
    SocketPermission socketPermission = new SocketPermission("*:1024-", "listen, connect, resolve");
    PropertyPermission propertyPermission = new PropertyPermission("*", "read, write");
    FilePermission filePermission = new FilePermission("<<ALL FILES>>", "read");

    perms.add(socketPermission);
    perms.add(propertyPermission);
    perms.add(filePermission);
    */
  }


  class RMIPermissionCollection extends PermissionCollection {

    private static final long serialVersionUID = 614300921365729272L;

    ArrayList<Permission> perms = new ArrayList<Permission>();

    public void add(Permission p) {
      perms.add(p);
    }

    public boolean implies(Permission p) {
      for (Iterator<Permission> i = perms.iterator(); i.hasNext();) {
        if (((Permission) i.next()).implies(p)) {
          return true;
        }
      }
      return false;
    }

    public Enumeration<Permission> elements() {
      return Collections.enumeration(perms);
    }

    public boolean isReadOnly() {
      return false;
    }
  }
}
