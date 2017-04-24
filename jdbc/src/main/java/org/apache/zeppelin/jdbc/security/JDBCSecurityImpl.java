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
package org.apache.zeppelin.jdbc.security;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.security.UserGroupInformation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Properties;

import org.apache.hadoop.security.UserGroupInformation.AuthenticationMethod;
import static org.apache.hadoop.security.UserGroupInformation.AuthenticationMethod.KERBEROS;
import static org.apache.hadoop.security.UserGroupInformation.AuthenticationMethod.SIMPLE;

/**
 * Created for org.apache.zeppelin.jdbc.security on 09/07/16.
 */
public class JDBCSecurityImpl {

  private static Logger LOGGER = LoggerFactory.getLogger(JDBCSecurityImpl.class);

  /***
   * @param properties
   */
  public static void createSecureConfiguration(Properties properties) {
    AuthenticationMethod authType = getAuthtype(properties);

    switch (authType) {
        case KERBEROS:
          Configuration conf = new
              org.apache.hadoop.conf.Configuration();
          conf.set("hadoop.security.authentication", KERBEROS.toString());
          UserGroupInformation.setConfiguration(conf);
          try {
            UserGroupInformation.loginUserFromKeytab(
                properties.getProperty("zeppelin.jdbc.principal"),
                properties.getProperty("zeppelin.jdbc.keytab.location")
            );
          } catch (IOException e) {
            LOGGER.error("Failed to get either keytab location or principal name in the " +
                "interpreter", e);
          }
    }
  }

  public static AuthenticationMethod getAuthtype(Properties properties) {
    AuthenticationMethod authType;
    try {
      authType = AuthenticationMethod.valueOf(properties.getProperty("zeppelin.jdbc.auth.type")
          .trim().toUpperCase());
    } catch (Exception e) {
      LOGGER.error(String.format("Invalid auth.type detected with value %s, defaulting " +
          "auth.type to SIMPLE", properties.getProperty("zeppelin.jdbc.auth.type")));
      authType = SIMPLE;
    }
    return authType;
  }

}
