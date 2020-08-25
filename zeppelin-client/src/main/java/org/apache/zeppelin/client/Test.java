package org.apache.zeppelin.client;

import org.apache.commons.lang3.StringUtils;

import java.util.List;

public class Test {

  public static void main(String[] args) throws Exception {
    ClientConfig clientConfig = new ClientConfig("http://localhost:8080");
    ZeppelinClient zeppelinClient = new ZeppelinClient(clientConfig);
    List<SessionResult> sessionResultList = zeppelinClient.listSessions();
    System.out.println(StringUtils.join("\n", sessionResultList));
  }
}
