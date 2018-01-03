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

package org.apache.zeppelin.inject;

import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.Provides;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.search.SearchService;
import org.apache.zeppelin.server.ZeppelinServer;
import org.apache.zeppelin.web.WebSecurity;

/**
 * Guice injection module.
 */
public class ZeppelinModule implements Module {
  private ZeppelinConfiguration conf;

  public ZeppelinModule(ZeppelinConfiguration conf) {
    this.conf = conf;
  }

  @Override
  public void configure(Binder binder) {

    binder.bind(WebSecurity.class)
        .to((Class<WebSecurity>) conf.getZeppelinWebSecurityClassname());

    binder.bind(SearchService.class)
        .to((Class<SearchService>) conf.getZeppelinSearchServiceClassname());

    binder.requestStaticInjection(ZeppelinServer.class);

  }

  @Provides
  public ZeppelinConfiguration getZeppelinConfiguration() {
    return conf;
  }

}
