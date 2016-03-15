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

import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.notebook.Notebook;
import org.apache.zeppelin.server.JsonResponse;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import java.util.Map;

/**
 * Configurations Rest API Endpoint
 */
@Path("/configurations")
@Produces("application/json")
public class ConfigurationsRestApi {

  private Notebook notebook;

  public ConfigurationsRestApi() {}

  public ConfigurationsRestApi(Notebook notebook) {
    this.notebook = notebook;
  }

  @GET
  @Path("all")
  public Response getAll() {
    ZeppelinConfiguration conf = notebook.getConf();

    Map<String, String> configurations = conf.dumpConfigurations(conf,
        new ZeppelinConfiguration.ConfigurationKeyPredicate() {
        @Override
        public boolean apply(String key) {
          return !key.contains("password") &&
              !key.equals(ZeppelinConfiguration
                  .ConfVars
                  .ZEPPELIN_NOTEBOOK_AZURE_CONNECTION_STRING
                  .getVarName());
        }
      }
    );

    return new JsonResponse(Status.OK, "", configurations).build();
  }

  @GET
  @Path("prefix/{prefix}")
  public Response getByPrefix(@PathParam("prefix") final String prefix) {
    ZeppelinConfiguration conf = notebook.getConf();

    Map<String, String> configurations = conf.dumpConfigurations(conf,
        new ZeppelinConfiguration.ConfigurationKeyPredicate() {
        @Override
        public boolean apply(String key) {
          return !key.contains("password") &&
              !key.equals(ZeppelinConfiguration
                  .ConfVars
                  .ZEPPELIN_NOTEBOOK_AZURE_CONNECTION_STRING
                  .getVarName()) &&
              key.startsWith(prefix);
        }
      }
    );

    return new JsonResponse(Status.OK, "", configurations).build();
  }

}
