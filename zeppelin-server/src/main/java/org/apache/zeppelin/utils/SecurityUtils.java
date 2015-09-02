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
package org.apache.zeppelin.utils;

import org.apache.zeppelin.conf.ZeppelinConfiguration;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;

/**
 * Created by joelz on 8/19/15.
 */
public class SecurityUtils {
  public static Boolean isValidOrigin(String sourceHost, ZeppelinConfiguration conf)
      throws UnknownHostException, URISyntaxException {
    if (sourceHost == null){
      return false;
    }

    URI sourceHostUri = new URI(sourceHost);
    String currentHost = java.net.InetAddress.getLocalHost().getHostName().toLowerCase();
    if (currentHost.equals(sourceHostUri.getHost()) ||
            "localhost".equals(sourceHostUri.getHost()) ||
            conf.getAllowedOrigins().contains(sourceHost) ||
            conf.getAllowedOrigins().contains("*")) {
      return true;
    }

    return false;
  }
}
