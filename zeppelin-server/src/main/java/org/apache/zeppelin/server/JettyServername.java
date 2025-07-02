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
package org.apache.zeppelin.server;

import org.eclipse.jetty.server.Request;

import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.server.HttpChannel;


public class JettyServername implements HttpChannel.Listener {

  private final Optional<String> servername;

  public JettyServername(final ZeppelinConfiguration zConf) {
    if (zConf.sendJettyName() && StringUtils.isNotBlank(zConf.getJettyName())) {
      this.servername = Optional.of(zConf.getJettyName().strip());
    } else {
      this.servername = Optional.empty();
    }
  }

  @Override
  public void onResponseBegin(Request request) {
    servername.ifPresent(value -> request.getResponse().setHeader(HttpHeader.SERVER, value));
  }
}
