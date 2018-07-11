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

package org.apache.zeppelin.service;

import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import javax.ws.rs.BadRequestException;
import org.apache.log4j.LogManager;
import org.apache.zeppelin.rest.message.LoggerRequest;

/** This class handles all of business logic of {@link org.apache.zeppelin.rest.AdminRestApi}. */
public class AdminService {

  public List<org.apache.log4j.Logger> getLoggers() {
    Enumeration loggers = LogManager.getCurrentLoggers();
    return StreamSupport.stream(
            Spliterators.spliteratorUnknownSize(
                new Iterator<org.apache.log4j.Logger>() {
                  @Override
                  public boolean hasNext() {
                    return loggers.hasMoreElements();
                  }

                  @Override
                  public org.apache.log4j.Logger next() {
                    return org.apache.log4j.Logger.class.cast(loggers.nextElement());
                  }
                },
                Spliterator.ORDERED),
            false)
        .collect(Collectors.toList());
  }

  public org.apache.log4j.Logger getLogger(String name) {
    return LogManager.getLogger(name);
  }

  public void setLoggerLevel(LoggerRequest loggerRequest) throws BadRequestException {
    try {
      Class.forName(loggerRequest.getName());
    } catch (Throwable ignore) {
      throw new BadRequestException(
          "The class of '" + loggerRequest.getName() + "' doesn't exists");
    }

    org.apache.log4j.Logger logger = LogManager.getLogger(loggerRequest.getName());
    if (null == logger) {
      throw new BadRequestException("The name of the logger is wrong");
    }

    org.apache.log4j.Level level = org.apache.log4j.Level.toLevel(loggerRequest.getLevel(), null);
    if (null == level) {
      throw new BadRequestException("The level of the logger is wrong");
    }

    logger.setLevel(level);
  }
}
