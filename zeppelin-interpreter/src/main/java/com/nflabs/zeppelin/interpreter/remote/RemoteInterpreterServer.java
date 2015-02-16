package com.nflabs.zeppelin.interpreter.remote;


import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.security.Policy;

/**
 *
 */
public class RemoteInterpreterServer implements RemoteInterpreter {


  public static void main(String [] args) {
    if (System.getSecurityManager() == null) {
      Policy.setPolicy(new RMIPolicy());
      SecurityManager securityManager = new SecurityManager();
      System.setSecurityManager(securityManager);
    }

    String name = "interpreterId";
    int port = 0;
    RemoteInterpreter remoteInterpreterServer = new RemoteInterpreterServer();
    try {
      RemoteInterpreter stub = (RemoteInterpreter)
              UnicastRemoteObject.exportObject(remoteInterpreterServer, port);
      Registry registry = LocateRegistry.getRegistry();
      registry.rebind(name, stub);
    } catch (RemoteException e) {
      e.printStackTrace();
    }
    System.out.println("ComputeEngine bound");

  }

}
