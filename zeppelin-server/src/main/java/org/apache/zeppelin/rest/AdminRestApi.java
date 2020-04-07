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

import com.google.common.collect.Lists;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import org.apache.commons.lang3.StringUtils;
import org.apache.zeppelin.annotation.ZeppelinApi;
import org.apache.zeppelin.rest.message.LoggerRequest;
import org.apache.zeppelin.service.AdminService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** This rest apis support some of feature related admin. e.g. changin log level. */
@Path("/admin")
@Singleton
public class AdminRestApi {
  private static final Logger logger = LoggerFactory.getLogger(AdminRestApi.class);

  private AdminService adminService;

  @Inject
  public AdminRestApi(AdminService adminService) {
    this.adminService = adminService;
  }

  /**
   * It gets current loggers' name and level.
   *
   * @param name FQCN
   * @return List of current loggers' name and level with json format. It returns all of loggers'
   *     name and level without name. With name, it returns only specific logger's name and level.
   */
  @GET
  @ZeppelinApi
  public List<org.apache.log4j.Logger> getLoggerSetting(@QueryParam("name") String name) {
    logger.debug("name: {}", name);
    return null == name || name.isEmpty()
        ? adminService.getLoggers()
        : Lists.newArrayList(adminService.getLogger(name));
  }

  /**
   * It change logger's level.
   *
   * @param loggerRequest logger's name and level with json format
   * @return The changed logger's name and level.
   */
  @POST
  @ZeppelinApi
  public List<org.apache.log4j.Logger> setLoggerLevel(LoggerRequest loggerRequest) {
    if (null == loggerRequest
        || StringUtils.isEmpty(loggerRequest.getName())
        || StringUtils.isEmpty(loggerRequest.getLevel())) {
      logger.trace("loggerRequest: {}", loggerRequest);
      throw new BadRequestException("Wrong request body");
    }
    logger.debug("loggerRequest: {}", loggerRequest);

    adminService.setLoggerLevel(loggerRequest);

    return Lists.newArrayList(adminService.getLogger(loggerRequest.getName()));
  }
}
